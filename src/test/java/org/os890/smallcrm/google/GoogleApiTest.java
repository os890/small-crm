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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.domain.GoogleAccount;
import org.os890.smallcrm.support.AbstractApiTest;

/**
 * The one authenticated request: what it sends, and how it tells Google's failures apart.
 *
 * <p>Which failure is which decides what a sync does next, so these are behaviours rather than
 * details: a 401 is retried once, a 410 means the sync token has to be thrown away, and a 429 or
 * a 5xx means stop and come back later.
 */
@QuarkusTest
@WithTestResource(GoogleStubResource.class)
class GoogleApiTest extends AbstractApiTest {

  @Inject GoogleApi api;
  @Inject TokenCrypto crypto;

  private GoogleAccount account;

  @BeforeEach
  void connectAnAccount() {
    GoogleStubResource.stub.requests.clear();
    GoogleStubResource.stub.on(
        "/token",
        request ->
            GoogleStub.Reply.ok(
                "{\"access_token\":\"a-fresh-access-token\",\"expires_in\":3599,"
                    + "\"token_type\":\"Bearer\",\"scope\":\"openid\"}"));
    account = storeAccount();
  }

  @Transactional
  GoogleAccount storeAccount() {
    GoogleAccount.deleteAll();
    AppUser user = fixtures.createUser("googler", "a-password-of-length", false);
    GoogleAccount stored = new GoogleAccount();
    stored.user = user;
    stored.userId = user.id;
    stored.subject = "subject-1";
    stored.email = "googler@example.org";
    stored.refreshToken = crypto.encrypt("a-refresh-token");
    stored.scopes = "openid";
    // Deliberately already expired, so the first call has to refresh.
    stored.accessToken = crypto.encrypt("a-stale-access-token");
    stored.accessExpires = Instant.now().minusSeconds(60);
    stored.persist();
    return stored;
  }

  @Test
  void a_request_carries_a_bearer_token_and_the_query_it_was_given() {
    GoogleStubResource.stub.onGet("/v1/things", "{\"items\":[{\"id\":\"one\"}]}");

    var answer =
        api.get(
            account,
            GoogleStubResource.stub.baseUrl() + "/v1/things",
            Map.of("pageSize", "50", "syncToken", "a-token"));

    assertThat(answer.path("items").get(0).path("id").asText()).isEqualTo("one");
    var request = GoogleStubResource.stub.requestsTo("/v1/things").getFirst();
    assertThat(request.authorization()).isEqualTo("Bearer a-fresh-access-token");
    assertThat(request.query()).contains("pageSize=50").contains("syncToken=a-token");
  }

  @Test
  void blank_query_values_are_left_out_rather_than_sent_empty() {
    GoogleStubResource.stub.onGet("/v1/things", "{}");

    // A null sync token means "a full pass"; sending syncToken= would be a different request
    // and Google rejects it.
    api.get(account, GoogleStubResource.stub.baseUrl() + "/v1/things",
        Map.of("pageSize", "50"));

    var request = GoogleStubResource.stub.requestsTo("/v1/things").getFirst();
    assertThat(request.query()).isEqualTo("pageSize=50");
  }

  @Test
  void an_expired_access_token_is_refreshed_once_and_the_call_retried() {
    AtomicInteger calls = new AtomicInteger();
    GoogleStubResource.stub.on(
        "/v1/things",
        request ->
            calls.incrementAndGet() == 1
                ? GoogleStub.Reply.status(401, "{\"error\":{\"message\":\"Invalid Credentials\"}}")
                : GoogleStub.Reply.ok("{\"ok\":true}"));

    var answer = api.get(account, GoogleStubResource.stub.baseUrl() + "/v1/things", Map.of());

    assertThat(answer.path("ok").asBoolean()).isTrue();
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void a_second_refusal_after_refreshing_is_not_retried_for_ever() {
    AtomicInteger calls = new AtomicInteger();
    GoogleStubResource.stub.on(
        "/v1/things",
        request -> {
          calls.incrementAndGet();
          return GoogleStub.Reply.status(401, "{\"error\":{\"message\":\"Invalid Credentials\"}}");
        });

    // The grant itself is gone; the user has to connect again, and looping would only hide it.
    assertThatThrownBy(
            () -> api.get(account, GoogleStubResource.stub.baseUrl() + "/v1/things", Map.of()))
        .isInstanceOf(GoogleApi.GoogleRefused.class);
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void an_expired_sync_token_is_its_own_failure_because_no_retry_fixes_it() {
    GoogleStubResource.stub.on(
        "/v1/things",
        request ->
            GoogleStub.Reply.status(410, "{\"error\":{\"message\":\"Sync token expired\"}}"));

    assertThatThrownBy(
            () -> api.get(account, GoogleStubResource.stub.baseUrl() + "/v1/things", Map.of()))
        .isInstanceOf(GoogleApi.SyncTokenExpired.class);
  }

  @Test
  void being_rate_limited_or_meeting_a_bad_day_means_come_back_later() {
    for (int status : List.of(429, 500, 503)) {
      GoogleStubResource.stub.on(
          "/v1/things", request -> GoogleStub.Reply.status(status, "{}"));

      assertThatThrownBy(
              () -> api.get(account, GoogleStubResource.stub.baseUrl() + "/v1/things", Map.of()))
          .as("status %d", status)
          .isInstanceOf(GoogleApi.TryAgainLater.class);
    }
  }

  @Test
  void a_refusal_carries_the_reason_google_gave() {
    GoogleStubResource.stub.on(
        "/v1/things",
        request ->
            GoogleStub.Reply.status(
                403,
                "{\"error\":{\"message\":\"Request had insufficient authentication scopes.\"}}"));

    assertThatThrownBy(
            () -> api.get(account, GoogleStubResource.stub.baseUrl() + "/v1/things", Map.of()))
        .isInstanceOf(GoogleApi.GoogleRefused.class)
        .hasMessageContaining("insufficient authentication scopes");
  }

  @Test
  void an_answer_that_is_not_json_is_refused_rather_than_parsed_into_nonsense() {
    GoogleStubResource.stub.on(
        "/v1/things", request -> GoogleStub.Reply.ok("<html>a proxy error page</html>"));

    assertThatThrownBy(
            () -> api.get(account, GoogleStubResource.stub.baseUrl() + "/v1/things", Map.of()))
        .isInstanceOf(GoogleApi.GoogleRefused.class);
  }

  @Test
  void an_empty_body_is_an_empty_object_so_deletes_do_not_fail_on_nothing() {
    GoogleStubResource.stub.on("/v1/things/one", request -> GoogleStub.Reply.ok(""));

    api.delete(account, GoogleStubResource.stub.baseUrl() + "/v1/things/one");

    assertThat(GoogleStubResource.stub.requestsTo("/v1/things/one").getFirst().method())
        .isEqualTo("DELETE");
  }
}
