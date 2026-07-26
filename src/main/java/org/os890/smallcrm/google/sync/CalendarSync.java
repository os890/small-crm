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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.os890.smallcrm.domain.Appointment;
import org.os890.smallcrm.domain.GoogleAccount;
import org.os890.smallcrm.domain.GoogleSyncState.Resource;
import org.os890.smallcrm.google.GoogleApi;
import org.os890.smallcrm.google.GoogleConfig;

/**
 * Keeps the agenda in step with the user's primary Google calendar.
 *
 * <p>Recurring events are the reason this is not simply symmetric. An Appointment has a start and
 * an end and no notion of "every Tuesday"; pulling a series in and writing it back would replace
 * a standing weekly meeting with a single event in somebody's real calendar. So a series, and any
 * instance of one, is pulled in to be seen and marked read-only.
 *
 * <p>All-day events have the same problem in miniature — Google gives them a date rather than a
 * time — so they are shown spanning the day and are likewise not written back.
 */
@ApplicationScoped
public class CalendarSync {

  private static final Logger LOG = Logger.getLogger(CalendarSync.class);

  /** How far back a first, full pass reaches. Older meetings are history, not an agenda. */
  private static final int FULL_SYNC_DAYS_BACK = 30;

  @Inject GoogleApi api;
  @Inject GoogleConfig config;
  @Inject Clock clock;

  public SyncReport run(GoogleAccount account, String syncToken, SyncCursor cursor) {
    SyncReport.Counter counted = new SyncReport.Counter(Resource.CALENDAR.name());
    pull(account, syncToken, counted, cursor);
    push(account, counted);
    return counted.done();
  }

  private void pull(
      GoogleAccount account, String syncToken, SyncReport.Counter counted, SyncCursor cursor) {
    String pageToken = null;
    String nextSyncToken = null;
    do {
      Map<String, String> query = new LinkedHashMap<>();
      query.put("maxResults", "250");
      query.put("showDeleted", "true");
      query.put("pageToken", pageToken);
      if (syncToken != null) {
        query.put("syncToken", syncToken);
      } else {
        // timeMin and a sync token are mutually exclusive; the window only applies to the first,
        // full pass, after which Google decides what has changed.
        query.put(
            "timeMin",
            Instant.now(clock).minusSeconds(FULL_SYNC_DAYS_BACK * 86400L).toString());
        query.put("singleEvents", "false");
      }
      JsonNode page =
          api.get(account, config.apiBase() + "/calendar/v3/calendars/primary/events", query);

      for (JsonNode event : page.path("items")) {
        applyRemote(event, counted);
      }
      pageToken = text(page, "nextPageToken");
      nextSyncToken = text(page, "nextSyncToken");
    } while (pageToken != null);
    cursor.token(nextSyncToken);
  }

  private void applyRemote(JsonNode event, SyncReport.Counter counted) {
    String id = text(event, "id");
    if (id == null) {
      counted.skipped();
      return;
    }
    Appointment local = Appointment.find("externalEventId", id).firstResult();

    if ("cancelled".equals(text(event, "status"))) {
      if (local != null) {
        local.delete();
        counted.pulledDeleted();
      }
      return;
    }

    Instant startsAt = instantOf(event.path("start"));
    Instant endsAt = instantOf(event.path("end"));
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
      // Nothing sensible to show on an agenda, and nothing this CRM would accept if typed in.
      counted.skipped();
      return;
    }

    boolean readOnly = tooRichToWriteBack(event);
    Instant remoteUpdated = parse(text(event, "updated"));

    if (local == null) {
      local = new Appointment();
      local.externalEventId = id;
      local.externalCalendarId = "primary";
      apply(event, local, startsAt, endsAt);
      local.externalReadOnly = readOnly;
      local.externalEtag = text(event, "etag");
      local.lastSyncedAt = Instant.now(clock);
      local.persist();
      counted.pulledIn();
      if (readOnly) {
        counted.readOnly();
      }
      return;
    }

    if (local.updatedAt != null
        && remoteUpdated != null
        && local.updatedAt.isAfter(remoteUpdated)
        && !readOnly) {
      return;
    }
    apply(event, local, startsAt, endsAt);
    local.externalReadOnly = readOnly;
    local.externalEtag = text(event, "etag");
    local.lastSyncedAt = Instant.now(clock);
    counted.pulledUpdated();
    if (readOnly) {
      counted.readOnly();
    }
  }

  /**
   * Whether writing this event back would destroy something.
   *
   * <p>A recurrence rule, or membership of a series, is the whole of it: an Appointment cannot
   * hold either, so a write-back turns a standing meeting into a one-off. All-day events go the
   * same way because Google gives them a date and this model insists on a time.
   */
  private static boolean tooRichToWriteBack(JsonNode event) {
    return !event.path("recurrence").isMissingNode()
        || text(event, "recurringEventId") != null
        || text(event.path("start"), "date") != null;
  }

  private static void apply(JsonNode event, Appointment appointment, Instant startsAt,
      Instant endsAt) {
    String title = text(event, "summary");
    appointment.title = title == null || title.isBlank() ? "(no title)" : title;
    appointment.startsAt = startsAt;
    appointment.endsAt = endsAt;
    appointment.location = text(event, "location");
    appointment.notes = text(event, "description");
    String zone = text(event.path("start"), "timeZone");
    appointment.timeZone = zone == null ? "UTC" : zone;
  }

  private void push(GoogleAccount account, SyncReport.Counter counted) {
    List<Appointment> outgoing = new ArrayList<>();
    outgoing.addAll(Appointment.list("externalEventId is null"));
    outgoing.addAll(
        Appointment.list(
            "externalEventId is not null and externalReadOnly = false"
                + " and (lastSyncedAt is null or updatedAt > lastSyncedAt)"));

    for (Appointment appointment : outgoing) {
      if (appointment.externalReadOnly) {
        counted.readOnly();
        continue;
      }
      try {
        if (appointment.externalEventId == null) {
          JsonNode created =
              api.post(
                  account,
                  config.apiBase() + "/calendar/v3/calendars/primary/events",
                  body(appointment));
          appointment.externalEventId = text(created, "id");
          appointment.externalCalendarId = "primary";
          appointment.externalEtag = text(created, "etag");
          counted.pushedNew();
        } else {
          JsonNode updated =
              api.patch(
                  account,
                  config.apiBase()
                      + "/calendar/v3/calendars/primary/events/"
                      + appointment.externalEventId,
                  Map.of(),
                  body(appointment));
          appointment.externalEtag = text(updated, "etag");
          counted.pushedUpdated();
        }
        appointment.lastSyncedAt = Instant.now(clock);
      } catch (GoogleApi.GoogleRefused e) {
        LOG.warnf("Google refused appointment %s: %s", appointment.title, e.getMessage());
        counted.skipped();
      }
    }
  }

  private static Map<String, Object> body(Appointment appointment) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("summary", appointment.title);
    event.put("start", Map.of("dateTime", appointment.startsAt.toString(), "timeZone", "UTC"));
    event.put("end", Map.of("dateTime", appointment.endsAt.toString(), "timeZone", "UTC"));
    if (appointment.location != null && !appointment.location.isBlank()) {
      event.put("location", appointment.location);
    }
    if (appointment.notes != null && !appointment.notes.isBlank()) {
      event.put("description", appointment.notes);
    }
    return event;
  }

  /** Google gives a timed event a dateTime and an all-day event a date. */
  private static Instant instantOf(JsonNode when) {
    String dateTime = text(when, "dateTime");
    if (dateTime != null) {
      return parse(dateTime);
    }
    String date = text(when, "date");
    if (date == null) {
      return null;
    }
    try {
      return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static Instant parse(String value) {
    try {
      return value == null ? null : java.time.OffsetDateTime.parse(value).toInstant();
    } catch (RuntimeException e) {
      try {
        return Instant.parse(value);
      } catch (RuntimeException ignored) {
        return null;
      }
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() || !value.isValueNode() ? null : value.asText();
  }
}
