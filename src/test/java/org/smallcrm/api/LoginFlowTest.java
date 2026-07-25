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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.smallcrm.support.AbstractApiTest;
import org.smallcrm.support.TestFixtures;

/** Exercises the real form-authentication round trip rather than a mocked identity. */
@QuarkusTest
class LoginFlowTest extends AbstractApiTest {

  private static final String SESSION_COOKIE = "quarkus-credential";

  @Test
  void anonymous_request_to_a_protected_endpoint_is_rejected() {
    given().when().get("/api/contacts").then().statusCode(401);
  }

  @Test
  void login_with_correct_credentials_returns_a_session_cookie() {
    String cookie = login(TestFixtures.ADMIN_USERNAME, TestFixtures.ADMIN_PASSWORD);
    assertThat(cookie).isNotBlank();

    given()
        .cookie(SESSION_COOKIE, cookie)
        .when()
        .get("/api/auth/me")
        .then()
        .statusCode(200)
        .body("username", is(TestFixtures.ADMIN_USERNAME))
        .body("admin", is(true))
        .body("mustChangePassword", is(false));
  }

  @Test
  void login_with_a_wrong_password_is_rejected_and_hands_out_no_cookie() {
    Response response = postCredentials(TestFixtures.ADMIN_USERNAME, "not-the-password");

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.getCookie(SESSION_COOKIE)).isNull();
  }

  @Test
  void login_with_an_unknown_user_is_rejected() {
    assertThat(postCredentials("nobody", "whatever").statusCode()).isEqualTo(401);
  }

  @Test
  void a_deactivated_account_cannot_use_the_api() {
    fixtures.createUser("temp", "temp-password", false);
    String cookie = login("temp", "temp-password");
    fixtures.deactivate("temp");

    given()
        .cookie(SESSION_COOKIE, cookie)
        .when()
        .get("/api/contacts")
        .then()
        .statusCode(403)
        .body("code", is("ACCOUNT_DEACTIVATED"));
  }

  @Test
  void logout_clears_the_session_cookie() {
    given().when().post("/api/auth/logout").then().statusCode(204);
  }

  private static String login(String username, String password) {
    Response response = postCredentials(username, password);
    assertThat(response.statusCode())
        .as("login should succeed, body was: %s", response.getBody().asString())
        .isEqualTo(200);
    return response.getCookie(SESSION_COOKIE);
  }

  private static Response postCredentials(String username, String password) {
    return given()
        .contentType(ContentType.URLENC)
        .formParam("username", username)
        .formParam("password", password)
        .when()
        .post("/api/auth/login");
  }
}
