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

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.domain.Appointment;
import org.os890.smallcrm.domain.Contact;
import org.os890.smallcrm.domain.CrmTask;
import org.os890.smallcrm.domain.GoogleAccount;
import org.os890.smallcrm.domain.GoogleSyncState;
import org.os890.smallcrm.domain.GoogleSyncState.Resource;
import org.os890.smallcrm.google.sync.GoogleSyncService;
import org.os890.smallcrm.google.sync.SyncReport;
import org.os890.smallcrm.support.AbstractApiTest;

/**
 * The three syncs, against the stub.
 *
 * <p>What is checked here is the part that decides whether somebody's data survives: which
 * records come in, which are pushed back, and above all which are recognised as holding more
 * than this CRM can and are therefore left alone. The field names themselves come from Google's
 * documentation and are unverified against the live API.
 */
@QuarkusTest
@WithTestResource(GoogleStubResource.class)
class GoogleSyncTest extends AbstractApiTest {

  private static final String LABEL = "contactGroups/12345";

  @Inject GoogleSyncService sync;
  @Inject TokenCrypto crypto;

  private AppUser user;

  @BeforeEach
  void connectAndStubGoogle() {
    GoogleStubResource.stub.requests.clear();
    GoogleStubResource.stub.on(
        "/token",
        request ->
            GoogleStub.Reply.ok(
                "{\"access_token\":\"an-access-token\",\"expires_in\":3599,"
                    + "\"token_type\":\"Bearer\"}"));
    // Empty answers by default, so each test only stubs what it is about.
    GoogleStubResource.stub.onGet(
        "/v1/contactGroups",
        "{\"contactGroups\":[{\"name\":\"Small CRM\",\"resourceName\":\"" + LABEL + "\"}]}");
    GoogleStubResource.stub.onGet("/v1/people/me/connections", "{\"connections\":[]}");
    GoogleStubResource.stub.onGet("/calendar/v3/calendars/primary/events", "{\"items\":[]}");
    GoogleStubResource.stub.onGet(
        "/tasks/v1/users/@me/lists", "{\"items\":[{\"id\":\"@default\"}]}");
    GoogleStubResource.stub.onGet("/tasks/v1/lists/@default/tasks", "{\"items\":[]}");
    user = connect();
  }

  @Transactional
  AppUser connect() {
    GoogleAccount.deleteAll();
    AppUser owner = AppUser.find("username", "admin").firstResult();
    GoogleAccount account = new GoogleAccount();
    account.user = owner;
    account.userId = owner.id;
    account.subject = "subject-1";
    account.email = "maria@example.org";
    account.refreshToken = crypto.encrypt("a-refresh-token");
    account.scopes =
        String.join(
            " ",
            List.of(
                GoogleConfig.CONTACTS_SCOPE,
                GoogleConfig.CALENDAR_SCOPE,
                GoogleConfig.TASKS_SCOPE));
    account.persist();
    return owner;
  }

  // ---------------------------------------------------------------- contacts

  @Test
  void a_labelled_google_contact_is_pulled_in() {
    GoogleStubResource.stub.onGet(
        "/v1/people/me/connections",
        """
        {"connections":[{
          "resourceName":"people/c1",
          "names":[{"givenName":"Maria","familyName":"Huber"}],
          "emailAddresses":[{"value":"maria@example.org"}],
          "phoneNumbers":[{"value":"+43 1 234"}],
          "organizations":[{"title":"Owner"}],
          "memberships":[{"contactGroupMembership":{"contactGroupResourceName":"%s"}}],
          "metadata":{"sources":[{"updateTime":"2026-07-01T08:00:00Z"}]}
        }],"nextSyncToken":"token-1"}
        """
            .formatted(LABEL));

    SyncReport report = sync.sync(user, Resource.CONTACTS);

    assertThat(report.pulledIn()).isEqualTo(1);
    Contact stored = findContact("people/c1");
    assertThat(stored.firstName).isEqualTo("Maria");
    assertThat(stored.lastName).isEqualTo("Huber");
    assertThat(stored.email).isEqualTo("maria@example.org");
    assertThat(stored.position).isEqualTo("Owner");
    assertThat(stored.externalReadOnly).isFalse();
  }

  @Test
  void a_contact_without_the_label_is_left_in_google() {
    GoogleStubResource.stub.onGet(
        "/v1/people/me/connections",
        """
        {"connections":[{
          "resourceName":"people/c2",
          "names":[{"givenName":"Private","familyName":"Person"}],
          "memberships":[{"contactGroupMembership":
              {"contactGroupResourceName":"contactGroups/other"}}],
          "metadata":{"sources":[{"updateTime":"2026-07-01T08:00:00Z"}]}
        }],"nextSyncToken":"token-1"}
        """);

    sync.sync(user, Resource.CONTACTS);

    // The whole point of scoping to a label: a shared workspace must not fill up with somebody's
    // private address book.
    assertThat(findContact("people/c2")).isNull();
  }

  @Test
  void a_contact_google_holds_more_richly_is_read_only_rather_than_flattened() {
    GoogleStubResource.stub.onGet(
        "/v1/people/me/connections",
        """
        {"connections":[{
          "resourceName":"people/c3",
          "names":[{"givenName":"Rich","familyName":"Record"}],
          "emailAddresses":[{"value":"one@example.org"},{"value":"two@example.org"}],
          "memberships":[{"contactGroupMembership":{"contactGroupResourceName":"%s"}}],
          "metadata":{"sources":[{"updateTime":"2026-07-01T08:00:00Z"}]}
        }],"nextSyncToken":"token-1"}
        """
            .formatted(LABEL));

    sync.sync(user, Resource.CONTACTS);

    Contact stored = findContact("people/c3");
    // Two addresses, one field. Writing back would delete the second from the user's own Google
    // contact, so the record is shown and never pushed.
    assertThat(stored.externalReadOnly).isTrue();
    assertThat(GoogleStubResource.stub.requestsTo("/v1/people/c3:updateContact")).isEmpty();
  }

  @Test
  void a_contact_created_here_is_pushed_and_given_the_label() {
    fixtures.createContact("Jonas", "Berger", null);
    GoogleStubResource.stub.on(
        "/v1/people:createContact",
        request -> GoogleStub.Reply.ok("{\"resourceName\":\"people/c9\",\"etag\":\"e1\"}"));
    GoogleStubResource.stub.on(
        "/v1/" + LABEL + "/members:modify", request -> GoogleStub.Reply.ok("{}"));

    SyncReport report = sync.sync(user, Resource.CONTACTS);

    assertThat(report.pushedNew()).isEqualTo(1);
    var created = GoogleStubResource.stub.requestsTo("/v1/people:createContact").getFirst();
    assertThat(created.bodyHas("Jonas")).isTrue();
    // Labelled straight away, otherwise the next pull would see an unlabelled contact and
    // delete the one it just created.
    assertThat(GoogleStubResource.stub.requestsTo("/v1/" + LABEL + "/members:modify")).hasSize(1);
  }

  // ---------------------------------------------------------------- calendar

  @Test
  void a_plain_google_event_becomes_an_appointment() {
    GoogleStubResource.stub.onGet(
        "/calendar/v3/calendars/primary/events",
        """
        {"items":[{
          "id":"ev1","status":"confirmed","summary":"Client meeting",
          "location":"Vienna","description":"Kickoff",
          "start":{"dateTime":"2026-09-01T08:00:00Z"},
          "end":{"dateTime":"2026-09-01T09:00:00Z"},
          "updated":"2026-08-01T08:00:00Z","etag":"e1"
        }],"nextSyncToken":"cal-token"}
        """);

    SyncReport report = sync.sync(user, Resource.CALENDAR);

    assertThat(report.pulledIn()).isEqualTo(1);
    Appointment stored = findAppointment("ev1");
    assertThat(stored.title).isEqualTo("Client meeting");
    assertThat(stored.location).isEqualTo("Vienna");
    assertThat(stored.externalReadOnly).isFalse();
  }

  @Test
  void a_recurring_meeting_is_never_written_back() {
    GoogleStubResource.stub.onGet(
        "/calendar/v3/calendars/primary/events",
        """
        {"items":[{
          "id":"ev2","status":"confirmed","summary":"Weekly standup",
          "recurrence":["RRULE:FREQ=WEEKLY;BYDAY=TU"],
          "start":{"dateTime":"2026-09-01T08:00:00Z"},
          "end":{"dateTime":"2026-09-01T08:30:00Z"},
          "updated":"2026-08-01T08:00:00Z","etag":"e2"
        }],"nextSyncToken":"cal-token"}
        """);

    sync.sync(user, Resource.CALENDAR);

    Appointment stored = findAppointment("ev2");
    // An Appointment cannot hold "every Tuesday". Pushing this back would replace a standing
    // meeting with a single event in the user's real calendar.
    assertThat(stored.externalReadOnly).isTrue();
    assertThat(stored.title).isEqualTo("Weekly standup");
    assertThat(GoogleStubResource.stub.requestsTo("/calendar/v3/calendars/primary/events/ev2"))
        .isEmpty();
  }

  @Test
  void an_all_day_event_is_shown_but_not_written_back() {
    GoogleStubResource.stub.onGet(
        "/calendar/v3/calendars/primary/events",
        """
        {"items":[{
          "id":"ev3","status":"confirmed","summary":"Conference",
          "start":{"date":"2026-09-01"},"end":{"date":"2026-09-02"},
          "updated":"2026-08-01T08:00:00Z","etag":"e3"
        }],"nextSyncToken":"cal-token"}
        """);

    sync.sync(user, Resource.CALENDAR);

    assertThat(findAppointment("ev3").externalReadOnly).isTrue();
  }

  @Test
  void an_event_cancelled_in_google_disappears_here() {
    GoogleStubResource.stub.onGet(
        "/calendar/v3/calendars/primary/events",
        """
        {"items":[{
          "id":"ev1","status":"confirmed","summary":"Client meeting",
          "start":{"dateTime":"2026-09-01T08:00:00Z"},
          "end":{"dateTime":"2026-09-01T09:00:00Z"},
          "updated":"2026-08-01T08:00:00Z"
        }],"nextSyncToken":"t1"}
        """);
    sync.sync(user, Resource.CALENDAR);
    assertThat(findAppointment("ev1")).isNotNull();

    GoogleStubResource.stub.onGet(
        "/calendar/v3/calendars/primary/events",
        "{\"items\":[{\"id\":\"ev1\",\"status\":\"cancelled\"}],\"nextSyncToken\":\"t2\"}");
    SyncReport report = sync.sync(user, Resource.CALENDAR);

    assertThat(report.pulledDeleted()).isEqualTo(1);
    assertThat(findAppointment("ev1")).isNull();
  }

  // ------------------------------------------------------------------- tasks

  @Test
  void a_google_task_becomes_a_todo() {
    GoogleStubResource.stub.onGet(
        "/tasks/v1/lists/@default/tasks",
        """
        {"items":[{
          "id":"t1","title":"Send the offer","notes":"before Friday",
          "due":"2026-08-01T00:00:00.000Z","status":"needsAction","etag":"e1",
          "updated":"2026-07-20T08:00:00.000Z"
        }]}
        """);

    SyncReport report = sync.sync(user, Resource.TASKS);

    assertThat(report.pulledIn()).isEqualTo(1);
    CrmTask stored = findTask("t1");
    assertThat(stored.title).isEqualTo("Send the offer");
    assertThat(stored.dueDate).isEqualTo("2026-08-01");
    assertThat(stored.done).isFalse();
  }

  @Test
  void a_subtask_is_shown_but_not_written_back() {
    GoogleStubResource.stub.onGet(
        "/tasks/v1/lists/@default/tasks",
        """
        {"items":[{
          "id":"t2","title":"A step","parent":"t1","status":"needsAction",
          "updated":"2026-07-20T08:00:00.000Z"
        }]}
        """);

    sync.sync(user, Resource.TASKS);

    // This model has no subtasks; pushing it back would promote it to a sibling.
    assertThat(findTask("t2").externalReadOnly).isTrue();
  }

  @Test
  void the_priority_and_the_links_a_todo_has_here_survive_a_pull() {
    // Google Tasks has nowhere to put a priority, a contact or a deal. A pull must therefore
    // touch only the fields Google owns, or a round trip would quietly erase them.
    CrmTask local = pushedTask();

    GoogleStubResource.stub.onGet(
        "/tasks/v1/lists/@default/tasks",
        """
        {"items":[{
          "id":"pushed-1","title":"Renamed in Google","status":"needsAction",
          "updated":"2036-07-20T08:00:00.000Z"
        }]}
        """);
    sync.sync(user, Resource.TASKS);

    CrmTask after = findTask("pushed-1");
    assertThat(after.title).isEqualTo("Renamed in Google");
    assertThat(after.priority).isEqualTo(local.priority);
    assertThat(after.contact).isNotNull();
  }

  @Transactional
  CrmTask pushedTask() {
    var contact = fixtures.createContact("Maria", "Huber", null);
    CrmTask task = new CrmTask();
    task.title = "Send the offer";
    task.priority = org.os890.smallcrm.domain.TaskPriority.HIGH;
    task.contact = contact;
    task.externalId = "pushed-1";
    task.externalListId = "@default";
    task.lastSyncedAt = Instant.now();
    task.persist();
    return task;
  }

  // ------------------------------------------------------------ sync state

  @Test
  void the_sync_token_google_returns_is_kept_for_next_time() {
    GoogleStubResource.stub.onGet(
        "/calendar/v3/calendars/primary/events",
        "{\"items\":[],\"nextSyncToken\":\"remember-me\"}");

    sync.sync(user, Resource.CALENDAR);

    assertThat(storedToken(Resource.CALENDAR)).isEqualTo("remember-me");
  }

  @Test
  void an_expired_sync_token_is_dropped_so_the_next_pass_is_a_full_one() {
    GoogleStubResource.stub.onGet(
        "/calendar/v3/calendars/primary/events", "{\"items\":[],\"nextSyncToken\":\"stale\"}");
    sync.sync(user, Resource.CALENDAR);
    assertThat(storedToken(Resource.CALENDAR)).isEqualTo("stale");

    GoogleStubResource.stub.on(
        "/calendar/v3/calendars/primary/events",
        request ->
            GoogleStub.Reply.status(410, "{\"error\":{\"message\":\"Sync token expired\"}}"));
    SyncReport report = sync.sync(user, Resource.CALENDAR);

    assertThat(report.error()).contains("starting again");
    assertThat(storedToken(Resource.CALENDAR)).isNull();
  }

  @Test
  void a_scope_the_user_declined_is_reported_as_that_and_not_as_a_fault() {
    narrowScopes();

    SyncReport report = sync.sync(user, Resource.TASKS);

    assertThat(report.error()).contains("not permitted");
    // Nothing was even attempted, so no failure is recorded against Google.
    assertThat(GoogleStubResource.stub.requestsTo("/tasks/v1/users/@me/lists")).isEmpty();
  }

  @Test
  void one_resource_failing_leaves_the_others_alone() {
    GoogleStubResource.stub.on(
        "/tasks/v1/users/@me/lists", request -> GoogleStub.Reply.status(503, "{}"));
    GoogleStubResource.stub.onGet(
        "/calendar/v3/calendars/primary/events", "{\"items\":[],\"nextSyncToken\":\"ok\"}");

    List<SyncReport> reports = sync.syncAll(user);

    assertThat(reports).hasSize(3);
    assertThat(reports.get(1).error()).isNull();
    assertThat(reports.get(2).error()).isNotNull();
  }

  @Transactional
  void narrowScopes() {
    GoogleAccount account = GoogleAccount.findByUser(user.id);
    account.scopes = GoogleConfig.CONTACTS_SCOPE;
  }

  @Transactional
  String storedToken(Resource resource) {
    GoogleSyncState state =
        GoogleSyncState.findById(new GoogleSyncState.Key(user.id, resource));
    return state == null ? null : state.syncToken;
  }

  @Transactional
  Contact findContact(String externalId) {
    return Contact.find("externalId", externalId).firstResult();
  }

  @Transactional
  Appointment findAppointment(String externalId) {
    return Appointment.find("externalEventId", externalId).firstResult();
  }

  @Transactional
  CrmTask findTask(String externalId) {
    return CrmTask.find("externalId", externalId).firstResult();
  }
}
