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

package org.os890.smallcrm.google;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.domain.GoogleAccount;
import org.os890.smallcrm.support.AbstractApiTest;

/**
 * Connecting a Google account, signing in with it, and every way that is meant to be refused.
 *
 * <p>Runs against {@link GoogleStub} rather than Google. That covers the flow, the state
 * handling and the storage; it does not prove Google's real responses match the stub's, which
 * only a run with real credentials can.
 */
@QuarkusTest
@WithTestResource(GoogleStubResource.class)
@TestSecurity(user = "admin", roles = {"ADMIN", "USER"})
class GoogleConnectionTest extends AbstractApiTest {

  private static final String SUBJECT = "104729382910928374651";
  private static final String EMAIL = "maria@example.org";

  @Inject TokenCrypto crypto;

  @BeforeEach
  void resetStub() {
    GoogleStubResource.stub.requests.clear();
    respondWithTokens("a-refresh-token", SUBJECT, EMAIL, true);
    GoogleStubResource.stub.on("/revoke", request -> GoogleStub.Reply.ok("{}"));
    removeConnections();
  }

  @Transactional
  void removeConnections() {
    GoogleAccount.deleteAll();
  }

  private void respondWithTokens(String refreshToken, String subject, String email,
      boolean verified) {
    GoogleStubResource.stub.on(
        "/token",
        request -> {
          StringBuilder body = new StringBuilder("{\"access_token\":\"an-access-token\"");
          body.append(",\"expires_in\":3599,\"token_type\":\"Bearer\"");
          body.append(",\"scope\":\"openid email profile ")
              .append(GoogleConfig.CONTACTS_SCOPE)
              .append(' ')
              .append(GoogleConfig.CALENDAR_SCOPE)
              .append(' ')
              .append(GoogleConfig.TASKS_SCOPE)
              .append('"');
          if (refreshToken != null) {
            body.append(",\"refresh_token\":\"").append(refreshToken).append('"');
          }
          body.append(",\"id_token\":\"")
              .append(GoogleStub.idToken(subject, email, verified))
              .append('"');
          return GoogleStub.Reply.ok(body.append('}').toString());
        });
  }

  /** Starts the connect flow and returns the state Google would be asked to hand back. */
  private String startConnecting() {
    String url = given().when().post("/api/google/connect").then().statusCode(200)
        .extract().path("url");
    return queryOf(url).get("state");
  }

  private static Map<String, String> queryOf(String url) {
    Map<String, String> values = new HashMap<>();
    String query = URI.create(url).getQuery();
    if (query != null) {
      Arrays.stream(query.split("&"))
          .map(pair -> pair.split("=", 2))
          .forEach(
              pair ->
                  values.put(
                      pair[0],
                      pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : ""));
    }
    return values;
  }

  @Test
  void the_consent_url_asks_for_lasting_access_and_all_three_scopes() {
    String url = given().when().post("/api/google/connect").then().statusCode(200)
        .extract().path("url");

    Map<String, String> query = queryOf(url);
    assertThat(query.get("client_id")).isEqualTo("test-client-id.apps.googleusercontent.com");
    assertThat(query.get("response_type")).isEqualTo("code");
    // Without offline access Google issues no refresh token, and the connection would die with
    // the first access token an hour later.
    assertThat(query.get("access_type")).isEqualTo("offline");
    assertThat(query.get("prompt")).isEqualTo("consent");
    assertThat(query.get("scope"))
        .contains("openid")
        .contains(GoogleConfig.CONTACTS_SCOPE)
        .contains(GoogleConfig.CALENDAR_SCOPE)
        .contains(GoogleConfig.TASKS_SCOPE);
    assertThat(query.get("state")).isNotBlank();
  }

  @Test
  void coming_back_from_google_connects_the_account() {
    String state = startConnecting();

    given()
        .redirects()
        .follow(false)
        .queryParam("code", "an-authorisation-code")
        .queryParam("state", state)
        .when()
        .get("/api/google/callback")
        .then()
        .statusCode(303)
        .header("Location", containsString("google=connected"));

    given()
        .when()
        .get("/api/google/status")
        .then()
        .statusCode(200)
        .body("connected", is(true))
        .body("email", is(EMAIL))
        .body("available", is(true));
  }

  @Test
  void the_stored_credentials_are_encrypted_and_readable_only_with_the_key() {
    completeConnection();

    GoogleAccount account = storedAccount();
    assertThat(account.refreshToken).isNotNull().doesNotContain("a-refresh-token");
    assertThat(account.accessToken).isNotNull().doesNotContain("an-access-token");
    assertThat(crypto.decrypt(account.refreshToken)).isEqualTo("a-refresh-token");
    assertThat(account.subject).isEqualTo(SUBJECT);
  }

  @Test
  void the_same_state_cannot_be_used_twice() {
    String state = startConnecting();
    callback(state).statusCode(303).header("Location", containsString("google=connected"));

    // A replayed callback must not connect anything a second time.
    callback(state).statusCode(303).header("Location", containsString("googleError"));
  }

  @Test
  void a_state_this_installation_never_issued_is_refused() {
    callback("a-state-from-somewhere-else")
        .statusCode(303)
        .header("Location", containsString("googleError=GOOGLE_STATE_UNKNOWN"));
  }

  @Test
  void cancelling_at_google_is_not_treated_as_a_failure() {
    given()
        .redirects()
        .follow(false)
        .queryParam("error", "access_denied")
        .when()
        .get("/api/google/callback")
        .then()
        .statusCode(303)
        .header("Location", containsString("google=cancelled"));
  }

  @Test
  void a_google_account_with_no_verified_address_is_refused() {
    respondWithTokens("a-refresh-token", SUBJECT, EMAIL, false);
    String state = startConnecting();

    callback(state)
        .statusCode(303)
        .header("Location", containsString("googleError=GOOGLE_EMAIL_UNVERIFIED"));
    assertThat(storedAccount()).isNull();
  }

  @Test
  void a_grant_without_a_refresh_token_is_refused_rather_than_stored_half_working() {
    // Google returns no refresh token when an account has already consented and prompt=consent
    // was not honoured. Storing that would produce a connection that fails at the first sync.
    respondWithTokens(null, SUBJECT, EMAIL, true);
    String state = startConnecting();

    callback(state)
        .statusCode(303)
        .header("Location", containsString("googleError=GOOGLE_NO_REFRESH_TOKEN"));
    assertThat(storedAccount()).isNull();
  }

  @Test
  void signing_in_with_an_unconnected_google_account_is_refused() {
    String url = given().when().post("/api/google/signin").then().statusCode(200)
        .extract().path("url");
    String state = queryOf(url).get("state");

    callback(state)
        .statusCode(303)
        .header("Location", containsString("googleError=GOOGLE_NOT_LINKED"));
  }

  @Test
  void signing_in_with_a_connected_account_issues_an_ordinary_session() {
    completeConnection();

    String url = given().when().post("/api/google/signin").then().statusCode(200)
        .extract().path("url");
    String state = queryOf(url).get("state");

    // The same session cookie a password login issues, so there is one kind of session and one
    // place to revoke it.
    callback(state)
        .statusCode(303)
        .header("Location", containsString("google=signed-in"))
        .cookie("smallcrm_session");
  }

  @Test
  void the_sign_in_consent_asks_only_who_you_are() {
    String url = given().when().post("/api/google/signin").then().statusCode(200)
        .extract().path("url");

    // Nothing is created from this path, so there is no reason to ask for anybody's contacts.
    String scope = queryOf(url).get("scope");
    assertThat(scope).contains("openid").doesNotContain(GoogleConfig.CONTACTS_SCOPE);
  }

  @Test
  void disconnecting_removes_the_credentials_and_tells_google() {
    completeConnection();

    given().when().delete("/api/google/connection").then().statusCode(204);

    assertThat(storedAccount()).isNull();
    // Withdrawn at Google too, so "disconnect" means what it says rather than only here.
    assertThat(GoogleStubResource.stub.requestsTo("/revoke")).hasSize(1);
    given().when().get("/api/google/status").then().body("connected", is(false));
  }

  @Test
  void the_login_screen_can_ask_whether_the_button_should_be_shown_without_signing_in() {
    given().when().get("/api/google/available").then().statusCode(200).body("available", is(true));
  }

  private void completeConnection() {
    callback(startConnecting()).statusCode(303);
  }

  private io.restassured.response.ValidatableResponse callback(String state) {
    return given()
        .redirects()
        .follow(false)
        .queryParam("code", "an-authorisation-code")
        .queryParam("state", state)
        .when()
        .get("/api/google/callback")
        .then();
  }

  @Transactional
  GoogleAccount storedAccount() {
    AppUser user = AppUser.find("username", "admin").firstResult();
    return user == null ? null : GoogleAccount.findByUser(user.id);
  }
}
