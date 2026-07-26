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

package org.os890.smallcrm.backup;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.support.AbstractApiTest;

/**
 * The automatic backup: a change has to produce a file, several changes in quick succession have
 * to produce only one, and reads must produce none.
 */
@QuarkusTest
@TestProfile(AutoBackupTriggerTest.AutoBackupProfile.class)
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class AutoBackupTriggerTest extends AbstractApiTest {

  /** Automatic backups switched on, with a window short enough to keep the test quick. */
  public static class AutoBackupProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "smallcrm.backup-dir", "target/test-auto-backups",
          "smallcrm.backup.auto-enabled", "true",
          "smallcrm.backup.coalesce-seconds", "1");
    }
  }

  @Inject BackupService backupService;
  @Inject AutoBackupTrigger trigger;

  @BeforeEach
  void emptyTheBackupFolder() throws IOException {
    Path dir = backupService.directory();
    Files.createDirectories(dir);
    try (Stream<Path> files = Files.list(dir)) {
      for (Path file : files.toList()) {
        Files.deleteIfExists(file);
      }
    }
  }

  @Test
  void a_change_produces_a_backup_shortly_afterwards() {
    createCompany("Muster GmbH");

    await().atMost(Duration.ofSeconds(10)).until(() -> backupService.list().size() == 1);
    assertThat(backupService.list().getFirst().beforeRestore()).isFalse();
  }

  @Test
  void a_burst_of_changes_ends_up_in_a_single_file() {
    for (int index = 0; index < 5; index++) {
      createCompany("Company " + index);
    }

    await().atMost(Duration.ofSeconds(10)).until(() -> !backupService.list().isEmpty());
    // Let any further scheduled write land before counting.
    await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
        .until(() -> backupService.list().size() <= 2);

    assertThat(backupService.list()).hasSize(1);
  }

  @Test
  void reading_data_does_not_produce_a_backup() {
    given().when().get("/api/companies").then().statusCode(200);
    given().when().get("/api/contacts").then().statusCode(200);
    given().when().get("/api/dashboard").then().statusCode(200);

    await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10))
        .until(() -> backupService.list().isEmpty());
  }

  @Test
  void the_backup_written_after_a_change_contains_that_change() {
    createCompany("Freshly added");

    await().atMost(Duration.ofSeconds(10)).until(() -> !backupService.list().isEmpty());

    Path file = backupService.directory().resolve(backupService.list().getFirst().name());
    assertThat(readFile(file)).contains("Freshly added");
  }

  @Test
  void a_change_still_waiting_out_the_coalescing_window_is_written_on_shutdown() {
    createCompany("Saved on the way out");
    assertThat(trigger.isPending()).isTrue();

    // What a service stop does first, before the scheduler is torn down. Shutting the scheduler
    // down without this dropped the pending write, which bites exactly the "stop the service,
    // copy the backup folder off the machine" workflow. Only the flush is exercised here: the
    // shutdown itself would leave the container without a scheduler for the tests that follow.
    trigger.flushPending();

    assertThat(backupService.list()).hasSize(1);
    Path file = backupService.directory().resolve(backupService.list().getFirst().name());
    assertThat(readFile(file)).contains("Saved on the way out");
    assertThat(trigger.isPending()).isFalse();
  }

  private static void createCompany(String name) {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("name", name))
        .when()
        .post("/api/companies")
        .then()
        .statusCode(201);
  }

  private static String readFile(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + file, e);
    }
  }
}
