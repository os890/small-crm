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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.smallcrm.security.SessionCookie;
import org.smallcrm.support.AbstractApiTest;
import org.smallcrm.support.TestFixtures;

/**
 * The real sign-in round trip, and the guarantees the session mechanism exists to provide:
 * sessions that can actually be ended, and repeated guessing that gets slower.
 */
@QuarkusTest
class LoginFlowTest extends AbstractApiTest {

  @Test
  void anonymous_request_to_a_protected_endpoint_is_rejected() {
    given().when().get("/api/contacts").then().statusCode(401);
  }

  @Test
  void login_with_correct_credentials_returns_a_session_cookie() {
    String cookie = login(TestFixtures.ADMIN_USERNAME, TestFixtures.ADMIN_PASSWORD);
    assertThat(cookie).isNotBlank();

    given()
        .cookie(SessionCookie.NAME, cookie)
        .when()
        .get("/api/auth/me")
        .then()
        .statusCode(200)
        .body("username", is(TestFixtures.ADMIN_USERNAME))
        .body("admin", is(true))
        .body("mustChangePassword", is(false));
  }

  @Test
  void the_session_cookie_is_not_readable_from_javascript() {
    Response response = postCredentials(TestFixtures.ADMIN_USERNAME, TestFixtures.ADMIN_PASSWORD);

    io.restassured.http.Cookie session = response.getDetailedCookie(SessionCookie.NAME);
    assertThat(session).isNotNull();
    // Without HttpOnly, any script on the page could read the session out of document.cookie.
    assertThat(session.isHttpOnly()).as("HttpOnly").isTrue();
    assertThat(session.getSameSite()).as("SameSite").isEqualToIgnoringCase("Strict");
  }

  @Test
  void login_with_a_wrong_password_is_rejected_and_hands_out_no_cookie() {
    Response response = postCredentials(TestFixtures.ADMIN_USERNAME, "not-the-password");

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.getCookie(SessionCookie.NAME)).isNull();
    assertThat(response.jsonPath().getString("code")).isEqualTo("BAD_CREDENTIALS");
  }

  @Test
  void login_with_an_unknown_user_is_rejected_the_same_way_as_a_wrong_password() {
    Response unknown = postCredentials("nobody-at-all", "whatever");
    Response wrong = postCredentials(TestFixtures.ADMIN_USERNAME, "not-the-password");

    // Same status and same code: neither answer reveals whether the account exists.
    assertThat(unknown.statusCode()).isEqualTo(wrong.statusCode());
    assertThat(unknown.jsonPath().getString("code"))
        .isEqualTo(wrong.jsonPath().getString("code"));
  }

  @Test
  void repeated_failures_lock_the_account_for_a_while() {
    fixtures.createUser("throttled", "their-own-password", false);

    for (int attempt = 0; attempt < 5; attempt++) {
      assertThat(postCredentials("throttled", "wrong").statusCode()).isEqualTo(401);
    }

    // The next attempt is refused before the password is even considered.
    Response locked = postCredentials("throttled", "their-own-password");
    assertThat(locked.statusCode()).isEqualTo(429);
    assertThat(locked.jsonPath().getString("code")).isEqualTo("TOO_MANY_ATTEMPTS");
    assertThat(locked.getHeader("Retry-After")).isNotNull();
  }

  @Test
  void a_successful_login_clears_the_failure_count() {
    fixtures.createUser("recovering", "their-own-password", false);
    postCredentials("recovering", "wrong");
    postCredentials("recovering", "wrong");

    assertThat(postCredentials("recovering", "their-own-password").statusCode()).isEqualTo(204);
    // Four more failures would lock the account if the count had not been reset.
    for (int attempt = 0; attempt < 4; attempt++) {
      assertThat(postCredentials("recovering", "wrong").statusCode()).isEqualTo(401);
    }
    assertThat(postCredentials("recovering", "their-own-password").statusCode()).isEqualTo(204);
  }

  @Test
  void a_deactivated_account_cannot_sign_in_at_all() {
    fixtures.createUser("temp", "their-own-password", false);
    fixtures.deactivate("temp");

    Response response = postCredentials("temp", "their-own-password");

    // Refused at the authentication itself, so no cookie is issued and the answer does not
    // confirm to a former employee that their password still works.
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.getCookie(SessionCookie.NAME)).isNull();
  }

  @Test
  void deactivating_an_account_ends_the_session_it_is_already_using() {
    fixtures.createUser("temp", "their-own-password", false);
    String cookie = login("temp", "their-own-password");
    given().cookie(SessionCookie.NAME, cookie).when().get("/api/auth/me").then().statusCode(200);

    fixtures.deactivate("temp");

    given().cookie(SessionCookie.NAME, cookie).when().get("/api/auth/me").then().statusCode(401);
  }

  @Test
  void signing_out_ends_the_session_on_the_server_not_just_in_the_browser() {
    String cookie = login(TestFixtures.ADMIN_USERNAME, TestFixtures.ADMIN_PASSWORD);

    given().cookie(SessionCookie.NAME, cookie).when().post("/api/auth/logout").then()
        .statusCode(204);

    // The same cookie value, replayed as an attacker with a stolen copy would, is now useless.
    given().cookie(SessionCookie.NAME, cookie).when().get("/api/auth/me").then().statusCode(401);
  }

  @Test
  void changing_the_password_ends_every_other_session_of_that_account() {
    fixtures.createUser("busy", "their-own-password", false);
    String stolen = login("busy", "their-own-password");
    String own = login("busy", "their-own-password");

    given()
        .cookie(SessionCookie.NAME, own)
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "currentPassword", "their-own-password",
                "newPassword", "a-brand-new-password"))
        .when()
        .post("/api/auth/password")
        .then()
        .statusCode(200);

    // The other session, which is what a stolen cookie looks like, stops working.
    given().cookie(SessionCookie.NAME, stolen).when().get("/api/auth/me").then().statusCode(401);
  }

  @Test
  void logout_is_harmless_when_no_session_exists() {
    given().when().post("/api/auth/logout").then().statusCode(204);
  }

  private static String login(String username, String password) {
    Response response = postCredentials(username, password);
    assertThat(response.statusCode())
        .as("login should succeed, body was: %s", response.getBody().asString())
        .isEqualTo(204);
    return response.getCookie(SessionCookie.NAME);
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
