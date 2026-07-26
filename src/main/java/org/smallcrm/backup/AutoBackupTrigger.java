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

package org.smallcrm.backup;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Writes a backup after the data changed.
 *
 * <p>Every change schedules a backup rather than writing one immediately. Changes that arrive
 * inside the coalescing window fold into the same file, so editing five fields in a row produces
 * one backup instead of five, while an installation that sits idle produces none at all. The
 * window is short enough that at most a few seconds of work is ever missing from the most recent
 * file.
 */
@ApplicationScoped
public class AutoBackupTrigger {

  private static final Logger LOG = Logger.getLogger(AutoBackupTrigger.class);

  @ConfigProperty(name = "smallcrm.backup.auto-enabled", defaultValue = "true")
  boolean enabled;

  @ConfigProperty(name = "smallcrm.backup.coalesce-seconds", defaultValue = "30")
  int coalesceSeconds;

  @Inject BackupService backupService;

  private ScheduledExecutorService scheduler;
  private final AtomicBoolean scheduled = new AtomicBoolean();

  void onStart(@Observes StartupEvent event) {
    if (!enabled) {
      LOG.info("Automatic backups are switched off");
      return;
    }
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "small-crm-backup");
              thread.setDaemon(true);
              return thread;
            });
    // Expired files are removed at startup too, so an installation that is left running for
    // months, or one that is barely used, still honours the retention period.
    runQuietly(backupService::applyRetention, "apply the backup retention");
  }

  /**
   * Writes a pending backup before the scheduler goes away.
   *
   * <p>Coalescing means a change made in the last half minute is still only scheduled. Dropping
   * it on shutdown would bite precisely the "stop the service, copy the backup folder off the
   * machine" workflow, where the last thing done before the stop is the thing most worth
   * keeping.
   */
  @ActivateRequestContext
  void onStop(@Observes ShutdownEvent event) {
    flushPending();
    shutdown();
  }

  /**
   * Writes a coalesced backup that is still waiting its turn.
   *
   * <p>Annotated in its own right because {@link #onStop} reaches it by self-invocation, which
   * no interceptor sees.
   */
  @ActivateRequestContext
  void flushPending() {
    writeNow();
  }

  /**
   * Records that the data changed and makes sure a backup follows.
   *
   * <p>Returns immediately: the request that changed something must not wait for a file to be
   * written, and must not fail if writing it does.
   */
  public void dataChanged() {
    if (!enabled || scheduler == null || scheduler.isShutdown()) {
      return;
    }
    if (scheduled.compareAndSet(false, true)) {
      scheduler.schedule(this::writeNow, coalesceSeconds, TimeUnit.SECONDS);
    }
  }

  /** Visible for tests: whether a backup is currently pending. */
  public boolean isPending() {
    return scheduled.get();
  }

  @ActivateRequestContext
  void writeNow() {
    // Claimed before writing, so a change that arrives while the file is being produced
    // schedules another one rather than being swallowed — and so a timer that fires after the
    // pending write was already flushed does nothing instead of writing the same data twice.
    if (!scheduled.compareAndSet(true, false)) {
      return;
    }
    runQuietly(() -> backupService.write(false), "write an automatic backup");
  }

  @PreDestroy
  void shutdown() {
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
    }
  }

  /**
   * Runs a backup step without letting it disturb anything else.
   *
   * <p>A full disk or a read-only folder must not take the application down or fail the user's
   * request; it is logged and the next change tries again.
   */
  private static void runQuietly(Runnable step, String description) {
    try {
      step.run();
    } catch (RuntimeException e) {
      LOG.errorf(e, "Could not %s", description);
    }
  }
}
