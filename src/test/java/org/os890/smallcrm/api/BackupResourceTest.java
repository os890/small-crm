/*
 * Copyright 2026 the Small CRM authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.os890.smallcrm.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.backup.BackupService;
import org.os890.smallcrm.backup.BackupSettingsService;
import org.os890.smallcrm.domain.Company;
import org.os890.smallcrm.domain.Contact;
import org.os890.smallcrm.support.AbstractApiTest;
import org.os890.smallcrm.support.BackupTestProfile;

/** Backup listing, creation, download, restore and retention, through the HTTP API. */
@QuarkusTest
@TestProfile(BackupTestProfile.class)
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class BackupResourceTest extends AbstractApiTest {

  @Inject BackupService backupService;
  @Inject BackupSettingsService settingsService;

  @BeforeEach
  void emptyTheBackupFolderAndResetTheRetention() throws IOException {
    Path dir = backupService.directory();
    Files.createDirectories(dir);
    try (Stream<Path> files = Files.list(dir)) {
      for (Path file : files.toList()) {
        Files.deleteIfExists(file);
      }
    }
    // Through the service rather than the API, because one test runs as a plain user who is
    // not allowed to call it.
    settingsService.updateRetentionDays(BackupSettingsService.DEFAULT_RETENTION_DAYS);
  }

  @Test
  void the_folder_starts_empty_and_a_backup_appears_after_creating_one() {
    given().when().get("/api/backups").then().statusCode(200).body("$", hasSize(0));

    given()
        .when()
        .post("/api/backups")
        .then()
        .statusCode(201)
        .body("name", startsWith("smallcrm-backup-"))
        .body("beforeRestore", is(false))
        .body("sizeBytes", greaterThan(0));

    given().when().get("/api/backups").then().body("$", hasSize(1));
  }

  @Test
  void the_backup_holds_the_business_data_but_no_accounts() {
    // Created through the API so the records carry a real owner, which is what the backup has
    // to represent as a plain user name.
    createCompanyViaApi("Muster GmbH");
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("firstName", "Maria", "lastName", "Huber"))
        .when()
        .post("/api/contacts")
        .then()
        .statusCode(201);

    String name = given().when().post("/api/backups").then().extract().path("name");
    String xml = download(name);

    assertThat(xml).contains("Muster GmbH").contains("Maria").contains("Huber");
    // Accounts are deliberately left out, so no password hash can travel in a backup.
    assertThat(xml).doesNotContain("$2a$").doesNotContain("<appUser");
    // The owner survives as a plain user name so a restore can re-link it.
    assertThat(xml).contains("<owner>admin</owner>");
  }

  @Test
  void a_restore_replaces_the_data_and_keeps_the_previous_state_in_a_before_restore_file() {
    Company kept = fixtures.createCompany("In the backup");
    fixtures.createContact("Maria", "Huber", kept);
    String name = given().when().post("/api/backups").then().extract().path("name");

    // Change the world after the backup was taken.
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "Added afterwards"))
        .when()
        .post("/api/companies")
        .then()
        .statusCode(201);
    given().when().get("/api/companies").then().body("$", hasSize(2));

    String safetyCopy =
        given()
            .when()
            .post("/api/backups/" + name + "/restore")
            .then()
            .statusCode(200)
            .body("recordCount", is(2))
            .extract()
            .path("safetyCopy");

    // The company created after the backup is gone, the backed up one is back.
    given()
        .when()
        .get("/api/companies")
        .then()
        .body("$", hasSize(1))
        .body("[0].name", is("In the backup"));
    given().when().get("/api/contacts").then().body("$", hasSize(1));

    // The state from just before the restore was preserved, timestamp in the name.
    assertThat(safetyCopy).startsWith("before-restore-").endsWith(".xml");
    assertThat(download(safetyCopy)).contains("Added afterwards");
  }

  @Test
  void restoring_the_safety_copy_undoes_an_unwanted_restore() {
    fixtures.createCompany("Original");
    String first = given().when().post("/api/backups").then().extract().path("name");

    given()
        .contentType(ContentType.JSON)
        .body(Map.of("name", "Second"))
        .when()
        .post("/api/companies")
        .then()
        .statusCode(201);

    String safetyCopy =
        given().when().post("/api/backups/" + first + "/restore").then().extract()
            .path("safetyCopy");
    given().when().get("/api/companies").then().body("$", hasSize(1));

    given().when().post("/api/backups/" + safetyCopy + "/restore").then().statusCode(200);

    given().when().get("/api/companies").then().body("name",
        org.hamcrest.Matchers.containsInAnyOrder("Original", "Second"));
  }

  @Test
  void a_restore_relinks_the_owner_by_name_and_leaves_it_empty_for_an_unknown_account() {
    createCompanyViaApi("Owned by admin");
    String name = given().when().post("/api/backups").then().extract().path("name");
    String xml = download(name);
    assertThat(xml).as("the owner has to be in the file for this test to mean anything")
        .contains("<owner>admin</owner>");
    xml = xml.replace("<owner>admin</owner>", "<owner>somebody-else</owner>");

    restoreUpload(xml).statusCode(200);

    given()
        .when()
        .get("/api/companies")
        .then()
        .body("$", hasSize(1))
        .body("[0].ownerName", org.hamcrest.Matchers.nullValue());
  }

  @Test
  void an_uploaded_backup_can_be_restored() {
    fixtures.createCompany("From an upload");
    fixtures.createContact("Maria", "Huber", null);
    String xml = download(given().when().post("/api/backups").then().extract().path("name"));

    fixtures.reset();
    given().when().get("/api/companies").then().body("$", hasSize(0));

    restoreUpload(xml).statusCode(200).body("recordCount", is(2));

    given().when().get("/api/companies").then().body("[0].name", is("From an upload"));
    given().when().get("/api/contacts").then().body("[0].displayName", is("Maria Huber"));
  }

  @Test
  void tags_and_relations_survive_a_round_trip() {
    Company company = fixtures.createCompany("Muster GmbH");
    Contact contact = fixtures.createContact("Maria", "Huber", company);
    fixtures.createDeal("Website relaunch", contact);
    fixtures.createInteraction(contact, Instant.parse("2026-07-01T09:00:00Z"));

    String xml = download(given().when().post("/api/backups").then().extract().path("name"));
    fixtures.reset();
    restoreUpload(xml).statusCode(200);

    given()
        .when()
        .get("/api/contacts")
        .then()
        .body("[0].companyName", is("Muster GmbH"))
        .body("[0].tags", org.hamcrest.Matchers.contains("poc"));
    given().when().get("/api/deals").then().body("[0].contactName", is("Maria Huber"));
    given()
        .when()
        .get("/api/interactions")
        .then()
        .body("$", hasSize(1))
        .body("[0].contactName", is("Maria Huber"));
  }

  @Test
  void a_file_that_is_not_a_backup_is_refused_without_touching_the_data() {
    fixtures.createCompany("Still here");

    restoreUpload("this is not xml at all")
        .statusCode(400)
        .body("code", is("BACKUP_UNREADABLE"));

    given().when().get("/api/companies").then().body("$", hasSize(1));
  }

  @Test
  void a_backup_from_a_newer_version_is_refused() {
    fixtures.createCompany("Muster GmbH");
    String xml =
        download(given().when().post("/api/backups").then().extract().path("name"))
            .replace("formatVersion=\"1\"", "formatVersion=\"99\"");

    restoreUpload(xml).statusCode(400).body("code", is("BACKUP_VERSION_UNSUPPORTED"));
  }

  @Test
  void a_hand_edited_file_with_a_repeated_id_is_refused_rather_than_half_restored() {
    fixtures.createCompany("Muster GmbH");
    fixtures.createCompany("Beispiel AG");
    String xml = download(given().when().post("/api/backups").then().extract().path("name"));
    // The restore rebuilds the links between records through maps keyed on these ids. A repeat
    // silently drops an entry, and the result is activity attached to the wrong contact.
    String firstId = xml.substring(xml.indexOf("<company>"), xml.indexOf("</company>"));
    String id = firstId.substring(firstId.indexOf("<id>") + 4, firstId.indexOf("</id>"));
    String broken = xml.replaceAll("<id>\\d+</id>", "<id>" + id + "</id>");

    restoreUpload(broken).statusCode(400).body("code", is("BACKUP_ID_DUPLICATE"));

    given().when().get("/api/companies").then().body("$", hasSize(2));
  }

  @Test
  void an_unknown_or_unsafe_file_name_yields_a_not_found_error() {
    given().when().get("/api/backups/nope.xml/content").then().statusCode(404);
    given()
        .when()
        .post("/api/backups/..%2F..%2Fetc%2Fpasswd/restore")
        .then()
        .statusCode(404);
  }

  @Test
  void the_retention_period_can_be_changed_and_is_reported_with_its_bounds() {
    given()
        .when()
        .get("/api/backups/settings")
        .then()
        .statusCode(200)
        .body("retentionDays", is(14))
        .body("minRetentionDays", is(1))
        .body("maxRetentionDays", is(3650))
        .body("directory", startsWith("/"));

    updateRetention(30).statusCode(200).body("retentionDays", is(30));
    given().when().get("/api/backups/settings").then().body("retentionDays", is(30));
  }

  @Test
  void a_retention_period_outside_the_accepted_range_is_refused() {
    updateRetention(0).statusCode(400);
    updateRetention(100_000).statusCode(400);
    given().when().get("/api/backups/settings").then().body("retentionDays", is(14));
  }

  @Test
  void the_rolling_clean_up_removes_files_older_than_the_retention_period() throws IOException {
    Path dir = backupService.directory();
    Path old = dir.resolve("smallcrm-backup-2020-01-01T00-00-00.xml");
    Path recent = dir.resolve("smallcrm-backup-2020-01-02T00-00-00.xml");
    Path foreign = dir.resolve("someone-elses-notes.xml");
    Files.writeString(old, "<smallCrmBackup formatVersion=\"1\"/>");
    Files.writeString(recent, "<smallCrmBackup formatVersion=\"1\"/>");
    Files.writeString(foreign, "not ours");
    Files.setLastModifiedTime(
        old, java.nio.file.attribute.FileTime.from(Instant.now().minus(40, ChronoUnit.DAYS)));
    Files.setLastModifiedTime(
        recent, java.nio.file.attribute.FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));

    // Changing the setting applies the new period straight away.
    updateRetention(7).statusCode(200);

    assertThat(Files.exists(old)).as("older than the period").isFalse();
    assertThat(Files.exists(recent)).as("inside the period").isTrue();
    assertThat(Files.exists(foreign)).as("not written by this application").isTrue();
  }

  @Test
  void the_newest_backup_is_listed_first() {
    given().when().post("/api/backups").then().statusCode(201);
    given().when().post("/api/backups").then().statusCode(201);

    List<String> created = given().when().get("/api/backups").then().extract().path("createdAt");

    assertThat(created).hasSize(2);
    assertThat(created).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  @TestSecurity(user = "assistant", roles = {"USER"})
  void a_plain_user_may_not_reach_the_backups() {
    given().when().get("/api/backups").then().statusCode(403);
    given().when().post("/api/backups").then().statusCode(403);
    given().when().get("/api/backups/settings").then().statusCode(403);
  }

  private static void createCompanyViaApi(String name) {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("name", name))
        .when()
        .post("/api/companies")
        .then()
        .statusCode(201);
  }

  private static String download(String name) {
    return given()
        .when()
        .get("/api/backups/" + name + "/content")
        .then()
        .statusCode(200)
        .extract()
        .asString();
  }

  private static io.restassured.response.ValidatableResponse restoreUpload(String xml) {
    return given()
        .multiPart("file", "backup.xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "application/xml")
        .when()
        .post("/api/backups/restore-upload")
        .then();
  }

  private static io.restassured.response.ValidatableResponse updateRetention(int days) {
    return given()
        .contentType(ContentType.JSON)
        .body(Map.of("retentionDays", days, "minRetentionDays", 1, "maxRetentionDays", 3650,
            "directory", ""))
        .when()
        .put("/api/backups/settings")
        .then();
  }
}
