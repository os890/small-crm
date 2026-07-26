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

package org.smallcrm.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.smallcrm.domain.AppUser;
import org.smallcrm.support.AbstractApiTest;
import org.smallcrm.support.TestFixtures;

/** Account administration, including the guards that prevent locking everybody out. */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class UserResourceTest extends AbstractApiTest {

  @Test
  void a_new_account_must_change_its_password_and_never_exposes_the_hash() {
    given()
        .contentType(ContentType.JSON)
        .body(newUser("assistant", "initial-secret-x", false))
        .when()
        .post("/api/users")
        .then()
        .statusCode(201)
        .body("username", is("assistant"))
        .body("admin", is(false))
        .body("roles", contains("USER"))
        .body("mustChangePassword", is(true))
        .body("password", nullValue());
  }

  @Test
  void usernames_are_unique() {
    fixtures.createUser("assistant", "initial-secret-x", false);

    given()
        .contentType(ContentType.JSON)
        .body(newUser("assistant", "another-secret-xx", false))
        .when()
        .post("/api/users")
        .then()
        .statusCode(400)
        .body("code", is("USERNAME_TAKEN"));
  }

  @Test
  void the_username_and_password_are_validated() {
    given()
        .contentType(ContentType.JSON)
        .body(newUser("me!", "short", false))
        .when()
        .post("/api/users")
        .then()
        .statusCode(400)
        .body("code", is("VALIDATION_FAILED"))
        .body("details.username", not(nullValue()))
        .body("details.password", not(nullValue()));
  }

  @Test
  void accounts_are_listed_alphabetically() {
    fixtures.createUser("zoe", "zoe-secret-longer", false);
    fixtures.createUser("bob", "bob-secret-longer", false);

    given()
        .when()
        .get("/api/users")
        .then()
        .statusCode(200)
        .body("username", contains("admin", "bob", "zoe"));
  }

  @Test
  void an_account_can_be_promoted_and_deactivated() {
    AppUser assistant = fixtures.createUser("assistant", "initial-secret-x", false);

    given()
        .contentType(ContentType.JSON)
        .body(update("Assistant Person", "assistant@example.org", true, true))
        .when()
        .put("/api/users/" + assistant.id)
        .then()
        .statusCode(200)
        .body("admin", is(true))
        .body("fullName", is("Assistant Person"));

    given()
        .contentType(ContentType.JSON)
        .body(update("Assistant Person", "assistant@example.org", false, false))
        .when()
        .put("/api/users/" + assistant.id)
        .then()
        .statusCode(200)
        .body("active", is(false));
  }

  @Test
  void the_last_administrator_cannot_be_demoted_deactivated_or_deleted() {
    Long adminId = fixtures.admin().id;

    given()
        .contentType(ContentType.JSON)
        .body(update("Administrator", null, false, true))
        .when()
        .put("/api/users/" + adminId)
        .then()
        .statusCode(400)
        .body("code", is("LAST_ADMIN"));

    given()
        .contentType(ContentType.JSON)
        .body(update("Administrator", null, true, false))
        .when()
        .put("/api/users/" + adminId)
        .then()
        .statusCode(400)
        .body("code", is("LAST_ADMIN"));
  }

  @Test
  void you_cannot_delete_or_deactivate_yourself_even_with_a_second_administrator() {
    fixtures.createUser("second-admin", "second-secret-xx", true);
    Long adminId = fixtures.admin().id;

    given().when().delete("/api/users/" + adminId).then()
        .statusCode(400)
        .body("code", is("SELF_DELETION"));

    given()
        .contentType(ContentType.JSON)
        .body(update("Administrator", null, true, false))
        .when()
        .put("/api/users/" + adminId)
        .then()
        .statusCode(400)
        .body("code", is("SELF_DEACTIVATION"));
  }

  @Test
  void resetting_a_password_forces_the_next_login_to_change_it() {
    AppUser assistant = fixtures.createUser("assistant", "initial-secret-x", false);

    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "currentPassword", TestFixtures.ADMIN_PASSWORD,
                "newPassword", "a-fresh-secret-here"))
        .when()
        .post("/api/users/" + assistant.id + "/password")
        .then()
        .statusCode(200)
        .body("mustChangePassword", is(true));
  }

  @Test
  void resetting_another_password_requires_the_administrator_s_own_password() {
    AppUser assistant = fixtures.createUser("assistant", "initial-secret-x", false);

    // Without this, one hijacked administrator session could take over every other account.
    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "currentPassword", "not-my-password",
                "newPassword", "a-fresh-secret-here"))
        .when()
        .post("/api/users/" + assistant.id + "/password")
        .then()
        .statusCode(400)
        .body("code", is("CURRENT_PASSWORD_WRONG"));
  }

  @Test
  void deleting_an_account_leaves_the_records_it_created_behind() {
    AppUser assistant = fixtures.createUser("assistant", "initial-secret-x", false);
    var company = fixtures.createCompany("Muster GmbH");
    setOwner(company.id, assistant);

    given().when().delete("/api/users/" + assistant.id).then().statusCode(204);

    given().when().get("/api/users").then().body("$", hasSize(1));
    given()
        .when()
        .get("/api/companies/" + company.id)
        .then()
        .statusCode(200)
        .body("ownerName", nullValue());
  }

  @Test
  void unknown_accounts_yield_a_not_found_error() {
    given().when().get("/api/users/9999").then().statusCode(404);
    given().when().delete("/api/users/9999").then().statusCode(404);
  }

  @Test
  @TestSecurity(user = "assistant", roles = {"USER"})
  void a_plain_user_may_not_administer_accounts() {
    fixtures.createUser("assistant", "initial-secret-x", false);

    given().when().get("/api/users").then().statusCode(403);
    given()
        .contentType(ContentType.JSON)
        .body(newUser("intruder", "intruder-secret", true))
        .when()
        .post("/api/users")
        .then()
        .statusCode(403);
  }

  private void setOwner(Long companyId, AppUser owner) {
    fixtures.assignCompanyOwner(companyId, owner.id);
  }

  private static Map<String, Object> newUser(String username, String password, boolean admin) {
    Map<String, Object> body = new HashMap<>();
    body.put("username", username);
    body.put("password", password);
    body.put("admin", admin);
    return body;
  }

  private static Map<String, Object> update(
      String fullName, String email, boolean admin, boolean active) {
    Map<String, Object> body = new HashMap<>();
    body.put("fullName", fullName);
    body.put("email", email);
    body.put("admin", admin);
    body.put("active", active);
    return body;
  }
}
