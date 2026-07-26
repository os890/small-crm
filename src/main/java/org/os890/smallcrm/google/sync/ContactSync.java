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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.os890.smallcrm.domain.Contact;
import org.os890.smallcrm.domain.GoogleAccount;
import org.os890.smallcrm.domain.GoogleSyncState.Resource;
import org.os890.smallcrm.google.GoogleApi;
import org.os890.smallcrm.google.GoogleConfig;

/**
 * Keeps contacts in step with one Google label.
 *
 * <p>Only the label matters: this is a shared workspace, and mirroring somebody's whole address
 * book into it would put their dentist and their mother in front of their colleagues. The user
 * decides what is shared by labelling it in Google, and a contact created here is given that
 * label when it is pushed.
 *
 * <p>A person Google holds richly — several e-mail addresses, a shelf of phone numbers — is
 * pulled in and marked read-only rather than written back, because this CRM has one field for
 * each and a write-back would delete the rest from the user's own account.
 */
@ApplicationScoped
public class ContactSync {

  private static final Logger LOG = Logger.getLogger(ContactSync.class);

  /** Only what is actually mapped, so Google leaves everything else on the person alone. */
  private static final String WRITE_FIELDS =
      "names,emailAddresses,phoneNumbers,organizations,biographies";

  private static final String READ_FIELDS =
      "names,emailAddresses,phoneNumbers,organizations,biographies,memberships,metadata";

  @Inject GoogleApi api;
  @Inject GoogleConfig config;
  @Inject Clock clock;

  public SyncReport run(GoogleAccount account, String syncToken, SyncCursor cursor) {
    SyncReport.Counter counted = new SyncReport.Counter(Resource.CONTACTS.name());
    String labelId = findLabel(account);
    if (labelId == null) {
      LOG.infof("No Google label named '%s'; nothing to sync", config.contactLabel());
      cursor.token(null);
      return counted.done();
    }
    pull(account, syncToken, labelId, counted, cursor);
    push(account, labelId, counted);
    return counted.done();
  }

  /**
   * Finds the label the user nominated.
   *
   * <p>Google calls them contact groups. Absent means the user has not made one yet, which is
   * not an error — it means nothing is shared, which is a perfectly reasonable state.
   */
  private String findLabel(GoogleAccount account) {
    JsonNode groups =
        api.get(account, config.peopleBase() + "/v1/contactGroups", Map.of("pageSize", "200"));
    for (JsonNode group : groups.path("contactGroups")) {
      if (config.contactLabel().equalsIgnoreCase(group.path("name").asText())) {
        return group.path("resourceName").asText();
      }
    }
    return null;
  }

  private void pull(
      GoogleAccount account,
      String syncToken,
      String labelId,
      SyncReport.Counter counted,
      SyncCursor cursor) {
    String pageToken = null;
    String nextSyncToken = null;
    do {
      Map<String, String> query = new LinkedHashMap<>();
      query.put("personFields", READ_FIELDS);
      query.put("pageSize", "200");
      query.put("requestSyncToken", "true");
      query.put("syncToken", syncToken);
      query.put("pageToken", pageToken);
      JsonNode page =
          api.get(account, config.peopleBase() + "/v1/people/me/connections", query);

      for (JsonNode person : page.path("connections")) {
        applyRemote(person, labelId, counted);
      }
      pageToken = text(page, "nextPageToken");
      nextSyncToken = text(page, "nextSyncToken");
    } while (pageToken != null);
    cursor.token(nextSyncToken);
  }

  private void applyRemote(JsonNode person, String labelId, SyncReport.Counter counted) {
    String resourceName = person.path("resourceName").asText(null);
    if (resourceName == null) {
      counted.skipped();
      return;
    }
    Contact local = Contact.find("externalId", resourceName).firstResult();

    if (person.path("metadata").path("deleted").asBoolean(false) || !carries(person, labelId)) {
      // Deleted in Google, or the user took the label off, which means the same thing here:
      // they no longer want it shared. The contact is removed rather than quietly orphaned.
      if (local != null) {
        local.delete();
        counted.pulledDeleted();
      }
      return;
    }

    Instant remoteUpdated = updatedAt(person);
    boolean readOnly = tooRichToWriteBack(person);

    if (local == null) {
      local = new Contact();
      local.externalId = resourceName;
      applyFields(person, local);
      local.externalReadOnly = readOnly;
      local.lastSyncedAt = Instant.now(clock);
      local.externalEtag = text(person.path("metadata"), "sources");
      local.persist();
      counted.pulledIn();
      if (readOnly) {
        counted.readOnly();
      }
      return;
    }

    // Last writer wins, per record. A local edit newer than Google's is left alone here and
    // pushed in the other direction below.
    if (local.updatedAt != null && remoteUpdated != null && local.updatedAt.isAfter(remoteUpdated)
        && !readOnly) {
      return;
    }
    applyFields(person, local);
    local.externalReadOnly = readOnly;
    local.lastSyncedAt = Instant.now(clock);
    counted.pulledUpdated();
    if (readOnly) {
      counted.readOnly();
    }
  }

  /**
   * Whether Google holds more on this person than the CRM can hold.
   *
   * <p>One e-mail address and up to two numbers are representable. Beyond that, writing back
   * what this application knows would delete the rest from the user's own Google contact, so the
   * record becomes something to read rather than something to edit.
   */
  private static boolean tooRichToWriteBack(JsonNode person) {
    return person.path("emailAddresses").size() > 1
        || person.path("phoneNumbers").size() > 2
        || person.path("organizations").size() > 1;
  }

  private static boolean carries(JsonNode person, String labelId) {
    for (JsonNode membership : person.path("memberships")) {
      if (labelId.equals(membership.path("contactGroupMembership").path("contactGroupResourceName")
          .asText())) {
        return true;
      }
    }
    return false;
  }

  private static void applyFields(JsonNode person, Contact contact) {
    JsonNode name = person.path("names").path(0);
    contact.firstName = blankToPlaceholder(text(name, "givenName"), "—");
    contact.lastName = blankToPlaceholder(text(name, "familyName"), text(name, "displayName"));
    contact.email = text(person.path("emailAddresses").path(0), "value");
    contact.phone = text(person.path("phoneNumbers").path(0), "value");
    contact.mobile = text(person.path("phoneNumbers").path(1), "value");
    contact.position = text(person.path("organizations").path(0), "title");
    contact.notes = text(person.path("biographies").path(0), "value");
  }

  /** Google allows a person with no name at all; this CRM requires both halves. */
  private static String blankToPlaceholder(String value, String fallback) {
    if (value != null && !value.isBlank()) {
      return value;
    }
    return fallback == null || fallback.isBlank() ? "—" : fallback;
  }

  private void push(GoogleAccount account, String labelId, SyncReport.Counter counted) {
    List<Contact> outgoing = new ArrayList<>();
    // Never synced, or changed here since the last time the two agreed.
    outgoing.addAll(Contact.list("externalId is null"));
    outgoing.addAll(
        Contact.list(
            "externalId is not null and externalReadOnly = false"
                + " and (lastSyncedAt is null or updatedAt > lastSyncedAt)"));

    for (Contact contact : outgoing) {
      if (contact.externalReadOnly) {
        counted.readOnly();
        continue;
      }
      try {
        if (contact.externalId == null) {
          create(account, labelId, contact);
          counted.pushedNew();
        } else {
          update(account, contact);
          counted.pushedUpdated();
        }
        contact.lastSyncedAt = Instant.now(clock);
      } catch (GoogleApi.GoogleRefused e) {
        // One contact Google will not take must not stop the other two hundred.
        LOG.warnf("Google refused contact %s: %s", contact.displayName(), e.getMessage());
        counted.skipped();
      }
    }
  }

  private void create(GoogleAccount account, String labelId, Contact contact) {
    JsonNode created =
        api.post(account, config.peopleBase() + "/v1/people:createContact", body(contact));
    contact.externalId = created.path("resourceName").asText(null);
    if (contact.externalId != null) {
      // Given the label straight away, so the next pull recognises it as one of ours rather
      // than as a contact the user unlabelled and deletes it again.
      api.post(
          account,
          config.peopleBase() + "/v1/" + labelId + "/members:modify",
          Map.of("resourceNamesToAdd", List.of(contact.externalId)));
    }
  }

  private void update(GoogleAccount account, Contact contact) {
    Map<String, Object> payload = new LinkedHashMap<>(body(contact));
    // Google needs the etag it gave us, and refuses the write if the person changed meanwhile.
    payload.put("etag", contact.externalEtag);
    api.patch(
        account,
        config.peopleBase() + "/v1/" + contact.externalId + ":updateContact",
        Map.of("updatePersonFields", WRITE_FIELDS),
        payload);
  }

  private static Map<String, Object> body(Contact contact) {
    Map<String, Object> person = new LinkedHashMap<>();
    person.put(
        "names",
        List.of(Map.of("givenName", nullToEmpty(contact.firstName), "familyName",
            nullToEmpty(contact.lastName))));
    if (contact.email != null && !contact.email.isBlank()) {
      person.put("emailAddresses", List.of(Map.of("value", contact.email)));
    }
    List<Map<String, String>> numbers = new ArrayList<>();
    if (contact.phone != null && !contact.phone.isBlank()) {
      numbers.add(Map.of("value", contact.phone, "type", "work"));
    }
    if (contact.mobile != null && !contact.mobile.isBlank()) {
      numbers.add(Map.of("value", contact.mobile, "type", "mobile"));
    }
    if (!numbers.isEmpty()) {
      person.put("phoneNumbers", numbers);
    }
    if (contact.position != null && !contact.position.isBlank()) {
      person.put("organizations", List.of(Map.of("title", contact.position)));
    }
    if (contact.notes != null && !contact.notes.isBlank()) {
      person.put("biographies", List.of(Map.of("value", contact.notes)));
    }
    return person;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static Instant updatedAt(JsonNode person) {
    String updated =
        person.path("metadata").path("sources").path(0).path("updateTime").asText(null);
    try {
      return updated == null ? null : Instant.parse(updated);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() || !value.isValueNode() ? null : value.asText();
  }
}
