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
import org.os890.smallcrm.domain.CrmTask;
import org.os890.smallcrm.domain.GoogleAccount;
import org.os890.smallcrm.domain.GoogleSyncState.Resource;
import org.os890.smallcrm.google.GoogleApi;
import org.os890.smallcrm.google.GoogleConfig;

/**
 * Keeps follow-up to-dos in step with the user's default Google Tasks list.
 *
 * <p>Google Tasks is the poorest of the three models and the mismatch runs the other way: a CRM
 * task has a priority, a contact and a deal, and Google has nowhere to put any of them. Those
 * stay here — pushing a task out and pulling it back must not erase them, which is why a pull
 * only touches the fields Google actually owns.
 *
 * <p>Subtasks are Google's one piece of structure this model lacks, so a task with a parent is
 * shown and left alone rather than being flattened into a sibling on the way back.
 */
@ApplicationScoped
public class TaskSync {

  private static final Logger LOG = Logger.getLogger(TaskSync.class);

  @Inject GoogleApi api;
  @Inject GoogleConfig config;
  @Inject Clock clock;

  public SyncReport run(GoogleAccount account, String syncToken, SyncCursor cursor) {
    SyncReport.Counter counted = new SyncReport.Counter(Resource.TASKS.name());
    String listId = defaultList(account);
    if (listId == null) {
      LOG.info("The Google account has no task list; nothing to sync");
      cursor.token(null);
      return counted.done();
    }
    pull(account, listId, syncToken, counted, cursor);
    push(account, listId, counted);
    // Google Tasks has no sync token of its own; the pull is bounded by updatedMin instead, and
    // the timestamp of this pass is what the next one asks from.
    cursor.token(Instant.now(clock).toString());
    return counted.done();
  }

  private String defaultList(GoogleAccount account) {
    JsonNode lists = api.get(account, config.apiBase() + "/tasks/v1/users/@me/lists",
        Map.of("maxResults", "100"));
    JsonNode first = lists.path("items").path(0);
    return text(first, "id");
  }

  private void pull(
      GoogleAccount account,
      String listId,
      String since,
      SyncReport.Counter counted,
      SyncCursor cursor) {
    String pageToken = null;
    do {
      Map<String, String> query = new LinkedHashMap<>();
      query.put("maxResults", "100");
      query.put("showCompleted", "true");
      query.put("showHidden", "true");
      query.put("showDeleted", "true");
      query.put("updatedMin", since);
      query.put("pageToken", pageToken);
      JsonNode page =
          api.get(account, config.apiBase() + "/tasks/v1/lists/" + listId + "/tasks", query);

      for (JsonNode task : page.path("items")) {
        applyRemote(task, listId, counted);
      }
      pageToken = text(page, "nextPageToken");
    } while (pageToken != null);
  }

  private void applyRemote(JsonNode remote, String listId, SyncReport.Counter counted) {
    String id = text(remote, "id");
    if (id == null) {
      counted.skipped();
      return;
    }
    CrmTask local = CrmTask.find("externalId", id).firstResult();

    if (remote.path("deleted").asBoolean(false)) {
      if (local != null) {
        local.delete();
        counted.pulledDeleted();
      }
      return;
    }

    boolean readOnly = text(remote, "parent") != null;
    Instant remoteUpdated = parse(text(remote, "updated"));

    if (local == null) {
      local = new CrmTask();
      local.externalId = id;
      local.externalListId = listId;
      apply(remote, local);
      local.externalReadOnly = readOnly;
      local.externalEtag = text(remote, "etag");
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
    // Only what Google owns. Priority, the contact and the deal are this application's and
    // survive a round trip precisely because they are never touched here.
    apply(remote, local);
    local.externalReadOnly = readOnly;
    local.lastSyncedAt = Instant.now(clock);
    counted.pulledUpdated();
    if (readOnly) {
      counted.readOnly();
    }
  }

  private void apply(JsonNode remote, CrmTask task) {
    String title = text(remote, "title");
    task.title = title == null || title.isBlank() ? "(no title)" : title;
    task.description = text(remote, "notes");
    task.dueDate = dateOf(text(remote, "due"));
    boolean done = "completed".equals(text(remote, "status"));
    if (done && !task.done) {
      task.completedAt = parse(text(remote, "completed"));
      if (task.completedAt == null) {
        task.completedAt = Instant.now(clock);
      }
    } else if (!done) {
      task.completedAt = null;
    }
    task.done = done;
  }

  private void push(GoogleAccount account, String listId, SyncReport.Counter counted) {
    List<CrmTask> outgoing = new ArrayList<>();
    outgoing.addAll(CrmTask.list("externalId is null"));
    outgoing.addAll(
        CrmTask.list(
            "externalId is not null and externalReadOnly = false"
                + " and (lastSyncedAt is null or updatedAt > lastSyncedAt)"));

    for (CrmTask task : outgoing) {
      if (task.externalReadOnly) {
        counted.readOnly();
        continue;
      }
      try {
        String base = config.apiBase() + "/tasks/v1/lists/";
        if (task.externalId == null) {
          JsonNode created = api.post(account, base + listId + "/tasks", body(task));
          task.externalId = text(created, "id");
          task.externalListId = listId;
          task.externalEtag = text(created, "etag");
          counted.pushedNew();
        } else {
          String list = task.externalListId == null ? listId : task.externalListId;
          JsonNode updated =
              api.patch(
                  account, base + list + "/tasks/" + task.externalId, Map.of(), body(task));
          task.externalEtag = text(updated, "etag");
          counted.pushedUpdated();
        }
        task.lastSyncedAt = Instant.now(clock);
      } catch (GoogleApi.GoogleRefused e) {
        LOG.warnf("Google refused task %s: %s", task.title, e.getMessage());
        counted.skipped();
      }
    }
  }

  private static Map<String, Object> body(CrmTask task) {
    Map<String, Object> remote = new LinkedHashMap<>();
    remote.put("title", task.title);
    if (task.description != null && !task.description.isBlank()) {
      remote.put("notes", task.description);
    }
    if (task.dueDate != null) {
      // Google stores a date here but insists on RFC 3339, and ignores the time part.
      remote.put("due", task.dueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toString());
    }
    remote.put("status", task.done ? "completed" : "needsAction");
    if (task.done && task.completedAt != null) {
      remote.put("completed", task.completedAt.toString());
    }
    return remote;
  }

  private static LocalDate dateOf(String value) {
    Instant instant = parse(value);
    return instant == null ? null : instant.atZone(ZoneOffset.UTC).toLocalDate();
  }

  private static Instant parse(String value) {
    if (value == null) {
      return null;
    }
    try {
      return java.time.OffsetDateTime.parse(value).toInstant();
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
