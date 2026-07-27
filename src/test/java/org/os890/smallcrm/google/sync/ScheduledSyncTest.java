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

package org.os890.smallcrm.google.sync;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.domain.GoogleAccount;
import org.os890.smallcrm.domain.GoogleSyncState;
import org.os890.smallcrm.domain.GoogleSyncState.Resource;
import org.os890.smallcrm.google.GoogleConfig;
import org.os890.smallcrm.google.GoogleStub;
import org.os890.smallcrm.google.GoogleStubResource;
import org.os890.smallcrm.google.TokenCrypto;
import org.os890.smallcrm.support.AbstractApiTest;

/**
 * The timed sync: who it runs for, and when it deliberately does not.
 *
 * <p>The timer itself is switched off in tests — a pass firing underneath a test would make it
 * depend on how long the rest of the suite took — so the job is driven directly.
 */
@QuarkusTest
@WithTestResource(GoogleStubResource.class)
class ScheduledSyncTest extends AbstractApiTest {

  @Inject ScheduledSync scheduled;
  @Inject TokenCrypto crypto;

  @BeforeEach
  void stubGoogle() {
    GoogleStubResource.stub.requests.clear();
    GoogleStubResource.stub.on(
        "/token",
        request ->
            GoogleStub.Reply.ok(
                "{\"access_token\":\"an-access-token\",\"expires_in\":3599,"
                    + "\"token_type\":\"Bearer\"}"));
    GoogleStubResource.stub.onGet("/v1/contactGroups", "{\"contactGroups\":[]}");
    GoogleStubResource.stub.onGet(
        "/calendar/v3/calendars/primary/events", "{\"items\":[],\"nextSyncToken\":\"t\"}");
    GoogleStubResource.stub.onGet("/tasks/v1/users/@me/lists", "{\"items\":[]}");
  }

  @Test
  void nothing_happens_when_no_account_is_connected() {
    scheduled.runForEveryone();

    // An installation nobody has connected must not talk to Google at all.
    assertThat(GoogleStubResource.stub.requests).isEmpty();
  }

  @Test
  void every_connected_account_is_reconciled() {
    connect("admin");

    scheduled.runForEveryone();

    // One call per resource is enough to prove each was attempted.
    assertThat(GoogleStubResource.stub.requestsTo("/v1/contactGroups")).hasSize(1);
    assertThat(GoogleStubResource.stub.requestsTo("/calendar/v3/calendars/primary/events"))
        .hasSize(1);
    assertThat(GoogleStubResource.stub.requestsTo("/tasks/v1/users/@me/lists")).hasSize(1);
  }

  @Test
  void a_deactivated_account_is_left_alone() {
    connect("admin");
    deactivate("admin");

    scheduled.runForEveryone();

    assertThat(GoogleStubResource.stub.requests).isEmpty();
  }

  @Test
  void a_resource_that_keeps_failing_is_given_a_rest() {
    connect("admin");
    failRepeatedly(Resource.CALENDAR, ScheduledSync.FAILURES_BEFORE_BACKOFF, Instant.now());

    scheduled.runForEveryone();

    // Retrying a broken call every quarter of an hour for ever drains the quota and fills the
    // log; the other two resources are untouched by one resource's trouble.
    assertThat(GoogleStubResource.stub.requestsTo("/calendar/v3/calendars/primary/events"))
        .isEmpty();
    assertThat(GoogleStubResource.stub.requestsTo("/v1/contactGroups")).hasSize(1);
  }

  @Test
  void the_rest_is_over_once_the_backoff_has_passed() {
    connect("admin");
    failRepeatedly(
        Resource.CALENDAR,
        ScheduledSync.FAILURES_BEFORE_BACKOFF,
        Instant.now().minus(ScheduledSync.BACKOFF).minusSeconds(60));

    scheduled.runForEveryone();

    assertThat(GoogleStubResource.stub.requestsTo("/calendar/v3/calendars/primary/events"))
        .hasSize(1);
  }

  @Test
  void a_couple_of_failures_are_not_enough_to_stop_trying() {
    connect("admin");
    failRepeatedly(Resource.CALENDAR, ScheduledSync.FAILURES_BEFORE_BACKOFF - 1, Instant.now());

    scheduled.runForEveryone();

    assertThat(GoogleStubResource.stub.requestsTo("/calendar/v3/calendars/primary/events"))
        .hasSize(1);
  }

  @Transactional
  void connect(String username) {
    GoogleAccount.deleteAll();
    AppUser user = AppUser.find("username", username).firstResult();
    GoogleAccount account = new GoogleAccount();
    account.user = user;
    account.userId = user.id;
    account.subject = "subject-" + username;
    account.email = username + "@example.org";
    account.refreshToken = crypto.encrypt("a-refresh-token");
    account.scopes =
        String.join(
            " ",
            List.of(
                GoogleConfig.CONTACTS_SCOPE,
                GoogleConfig.CALENDAR_SCOPE,
                GoogleConfig.TASKS_SCOPE));
    account.persist();
  }

  @Transactional
  void deactivate(String username) {
    AppUser user = AppUser.find("username", username).firstResult();
    user.active = false;
  }

  @Transactional
  void failRepeatedly(Resource resource, int failures, Instant lastRun) {
    AppUser user = AppUser.find("username", "admin").firstResult();
    GoogleSyncState state = GoogleSyncState.of(user.id, resource);
    state.failures = failures;
    state.lastRunAt = lastRun;
    state.lastError = "Google answered 503";
    state.persist();
  }
}
