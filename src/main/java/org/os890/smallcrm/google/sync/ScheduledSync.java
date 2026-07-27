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

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.domain.GoogleAccount;
import org.os890.smallcrm.domain.GoogleSyncState;
import org.os890.smallcrm.domain.GoogleSyncState.Resource;
import org.os890.smallcrm.google.GoogleConfig;

/**
 * Runs the Google sync on a timer, for every account that is connected.
 *
 * <p>Without this the two sides only agree when somebody presses a button, which is not what
 * anybody means by "synced". The interval is configuration rather than a constant because how
 * often is genuinely a matter of taste and of how much of somebody's Google quota they want
 * spent: a busy shared workspace might want five minutes, a single person once an hour.
 *
 * <p>Setting {@code smallcrm.google.sync-interval} to {@code off} disables it and leaves the
 * Sync now button as the only way, which is the right setting while somebody is watching what
 * the integration does to their data for the first time.
 */
@ApplicationScoped
public class ScheduledSync {

  private static final Logger LOG = Logger.getLogger(ScheduledSync.class);

  /**
   * How many consecutive failures put a resource into backoff.
   *
   * <p>A scheduled job that retries a broken call every quarter of an hour for ever is rude to
   * Google and useless to the user: the log fills with the same line and the quota drains for
   * nothing.
   */
  static final int FAILURES_BEFORE_BACKOFF = 3;

  /** How long a failing resource is left alone before being tried again. */
  static final Duration BACKOFF = Duration.ofHours(1);

  @Inject GoogleSyncService sync;
  @Inject GoogleConfig config;
  @Inject Clock clock;

  /** Only so a test can tell that a pass happened without waiting for the timer. */
  @ConfigProperty(name = "smallcrm.google.sync-interval", defaultValue = "15m")
  String interval;

  /**
   * One pass over every connected account.
   *
   * <p>{@code SKIP} rather than queueing: a pass that takes longer than the interval means
   * Google is slow or somebody has a very large address book, and stacking a second pass on top
   * would make both worse. The next tick simply picks it up.
   */
  @Scheduled(
      every = "{smallcrm.google.sync-interval}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
      // Nothing to sync before the application has finished starting, and a sync during startup
      // would compete with the migrations for the same database.
      delayed = "2m")
  void runForEveryone() {
    if (!config.isEnabled()) {
      return;
    }
    for (Long userId : connectedUserIds()) {
      syncOneUser(userId);
    }
  }

  @Transactional
  List<Long> connectedUserIds() {
    return GoogleAccount.<GoogleAccount>listAll().stream().map(account -> account.userId).toList();
  }

  private void syncOneUser(Long userId) {
    AppUser user = findUser(userId);
    if (user == null || !user.active) {
      // The account was deleted or deactivated since the list was taken. Its connection goes
      // with it elsewhere; here it is simply nothing to do.
      return;
    }
    for (Resource resource : Resource.values()) {
      if (inBackoff(userId, resource)) {
        continue;
      }
      try {
        SyncReport report = sync.sync(user, resource);
        if (report.changedAnything()) {
          LOG.infof("Scheduled Google sync for %s: %s", user.username, report);
        }
      } catch (RuntimeException e) {
        // GoogleSyncService already turns the expected failures into reports; anything reaching
        // here is unexpected, and one user's problem must not stop the others.
        LOG.errorf(e, "Scheduled Google %s sync failed for %s", resource, user.username);
      }
    }
  }

  @Transactional
  AppUser findUser(Long userId) {
    return AppUser.findById(userId);
  }

  /**
   * Whether a resource has been failing often enough to be left alone for a while.
   *
   * <p>Deliberately not applied to the Sync now button: somebody who has just fixed their
   * Google settings should be able to try immediately rather than being told to wait an hour.
   */
  @Transactional
  boolean inBackoff(Long userId, Resource resource) {
    GoogleSyncState state =
        GoogleSyncState.findById(new GoogleSyncState.Key(userId, resource));
    if (state == null || state.failures < FAILURES_BEFORE_BACKOFF || state.lastRunAt == null) {
      return false;
    }
    return state.lastRunAt.plus(BACKOFF).isAfter(Instant.now(clock));
  }
}
