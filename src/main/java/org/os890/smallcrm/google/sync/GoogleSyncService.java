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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jboss.logging.Logger;
import org.os890.smallcrm.api.error.BusinessRuleException;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.domain.GoogleAccount;
import org.os890.smallcrm.domain.GoogleSyncState;
import org.os890.smallcrm.domain.GoogleSyncState.Resource;
import org.os890.smallcrm.google.GoogleApi;
import org.os890.smallcrm.google.GoogleConfig;

/**
 * Runs the three syncs for one user and remembers how each got on.
 *
 * <p>Each resource is its own transaction and its own failure. A calendar that Google is refusing
 * today must not stop contacts from syncing, and a scope the user declined must not read as a
 * fault — it reads as "you did not grant this", which is a different thing and needs a different
 * sentence in the interface.
 */
@ApplicationScoped
public class GoogleSyncService {

  private static final Logger LOG = Logger.getLogger(GoogleSyncService.class);

  @Inject ContactSync contacts;
  @Inject CalendarSync calendar;
  @Inject TaskSync tasks;
  @Inject GoogleConfig config;
  @Inject Clock clock;

  /** Runs everything the user granted, and reports each resource separately. */
  public List<SyncReport> syncAll(AppUser user) {
    List<SyncReport> reports = new ArrayList<>();
    reports.add(sync(user, Resource.CONTACTS));
    reports.add(sync(user, Resource.CALENDAR));
    reports.add(sync(user, Resource.TASKS));
    return reports;
  }

  /**
   * Runs one resource.
   *
   * <p>Never throws for an ordinary failure: the outcome is the report, because a partial sync
   * is normal and the interface has to show three independent states rather than one exception.
   */
  @Transactional
  public SyncReport sync(AppUser user, Resource resource) {
    GoogleAccount account = GoogleAccount.findByUser(user.id);
    if (account == null) {
      throw new BusinessRuleException(
          "GOOGLE_NOT_CONNECTED", "No Google account is connected to this user.");
    }
    if (!account.hasScopes(Set.of(scopeFor(resource)))) {
      // Consent is per scope. Declining one is a choice, not a breakage.
      return SyncReport.failed(resource.name(), "not permitted: that access was not granted");
    }

    GoogleSyncState state = GoogleSyncState.of(user.id, resource);
    SyncCursor cursor = new SyncCursor();
    Instant now = Instant.now(clock);
    try {
      SyncReport report = run(resource, account, state.syncToken, cursor);
      state.succeeded(cursor.token(), now);
      state.persist();
      if (report.changedAnything()) {
        LOG.infof("Google %s sync for %s: %s", resource, user.username, report);
      }
      return report;
    } catch (GoogleApi.SyncTokenExpired e) {
      // Google forgot where we were. Dropping the token makes the next pass a full one, which
      // is the documented remedy and not worth troubling the user with.
      state.failed(e.getMessage(), true, now);
      state.persist();
      return SyncReport.failed(resource.name(), "starting again from scratch next time");
    } catch (GoogleApi.TryAgainLater e) {
      state.failed(e.getMessage(), false, now);
      state.persist();
      return SyncReport.failed(resource.name(), e.getMessage());
    } catch (GoogleApi.GoogleRefused e) {
      state.failed(e.getMessage(), false, now);
      state.persist();
      return SyncReport.failed(resource.name(), e.getMessage());
    }
  }

  private SyncReport run(
      Resource resource, GoogleAccount account, String syncToken, SyncCursor cursor) {
    return switch (resource) {
      case CONTACTS -> contacts.run(account, syncToken, cursor);
      case CALENDAR -> calendar.run(account, syncToken, cursor);
      case TASKS -> tasks.run(account, syncToken, cursor);
    };
  }

  private static String scopeFor(Resource resource) {
    return switch (resource) {
      case CONTACTS -> GoogleConfig.CONTACTS_SCOPE;
      case CALENDAR -> GoogleConfig.CALENDAR_SCOPE;
      case TASKS -> GoogleConfig.TASKS_SCOPE;
    };
  }
}
