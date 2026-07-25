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
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.smallcrm.support.AbstractApiTest;
import org.smallcrm.support.TestFixtures;

/** Profile and password endpoints, plus the forced password change. */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class AuthResourceTest extends AbstractApiTest {

  @Test
  void me_returns_the_signed_in_profile() {
    given()
        .when()
        .get("/api/auth/me")
        .then()
        .statusCode(200)
        .body("username", is("admin"))
        .body("admin", is(true))
        .body("active", is(true));
  }

  @Test
  void changing_the_password_requires_the_correct_current_one() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("currentPassword", "wrong", "newPassword", "brand-new-secret"))
        .when()
        .post("/api/auth/password")
        .then()
        .statusCode(400)
        .body("code", is("CURRENT_PASSWORD_WRONG"));
  }

  @Test
  void the_new_password_must_actually_be_new() {
    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "currentPassword",
                TestFixtures.ADMIN_PASSWORD,
                "newPassword",
                TestFixtures.ADMIN_PASSWORD))
        .when()
        .post("/api/auth/password")
        .then()
        .statusCode(400)
        .body("code", is("PASSWORD_UNCHANGED"));
  }

  @Test
  void the_new_password_must_be_long_enough() {
    given()
        .contentType(ContentType.JSON)
        .body(Map.of("currentPassword", TestFixtures.ADMIN_PASSWORD, "newPassword", "short"))
        .when()
        .post("/api/auth/password")
        .then()
        .statusCode(400)
        .body("code", is("VALIDATION_FAILED"))
        .body("details.newPassword", is("size must be between 8 and 100"));
  }

  @Test
  void a_successful_change_clears_the_forced_password_change() {
    fixtures.requirePasswordChangeForAdmin();

    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "currentPassword", TestFixtures.ADMIN_PASSWORD, "newPassword", "a-better-secret"))
        .when()
        .post("/api/auth/password")
        .then()
        .statusCode(200)
        .body("mustChangePassword", is(false));

    given().when().get("/api/contacts").then().statusCode(200);
  }

  @Test
  void a_forced_password_change_blocks_the_rest_of_the_api() {
    fixtures.requirePasswordChangeForAdmin();

    given()
        .when()
        .get("/api/contacts")
        .then()
        .statusCode(403)
        .body("code", is("PASSWORD_CHANGE_REQUIRED"));

    // Reading the own profile has to stay possible, otherwise the UI cannot explain the state.
    given().when().get("/api/auth/me").then().statusCode(200).body("mustChangePassword", is(true));
  }
}
