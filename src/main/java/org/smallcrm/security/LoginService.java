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

package org.smallcrm.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.smallcrm.domain.AppUser;

/**
 * Verifies credentials and decides whether a sign-in may proceed.
 *
 * <p>Three things happen here that used to be missing, and all three need the same code path,
 * which is why the framework's form mechanism was replaced by an endpoint of our own.
 *
 * <p><strong>Repeated failures cost time.</strong> Consecutive failures lock the account for a
 * growing interval. Without this the only brake on guessing is the cost of bcrypt, which allows
 * tens of thousands of attempts an hour.
 *
 * <p><strong>A missing account costs the same as a wrong password.</strong> Looking the user up
 * and only then running bcrypt makes a non-existent name answer in about a millisecond and an
 * existing one take a full hash — a timing oracle that turns guessing user names into a matter
 * of measurement. A miss is therefore verified against a fixed dummy hash.
 *
 * <p><strong>Deactivated accounts never authenticate.</strong> Previously the login succeeded
 * and a filter refused the subsequent calls, which still confirmed to a former employee that
 * their password was intact.
 */
@ApplicationScoped
public class LoginService {

  private static final Logger LOG = Logger.getLogger(LoginService.class);

  /** Failures tolerated before the account starts locking. */
  static final int FAILURES_BEFORE_LOCK = 5;

  /** Lock length is this, doubled for each failure beyond the threshold, up to the cap. */
  static final Duration BASE_LOCK = Duration.ofSeconds(30);

  static final Duration MAX_LOCK = Duration.ofMinutes(30);

  /**
   * Hashed once at class initialisation. Verifying a wrong password against this costs the same
   * as verifying it against a real account's hash, so a miss and a hit take comparable time.
   */
  private static final String DUMMY_HASH =
      Passwords.hash("a-password-that-belongs-to-nobody-at-all");

  @ConfigProperty(name = "smallcrm.login.lockout-enabled", defaultValue = "true")
  boolean lockoutEnabled;

  @Inject Clock clock;

  /** Why a sign-in was refused, so the endpoint can answer appropriately. */
  public enum Failure {
    /** Wrong user name or password. */
    BAD_CREDENTIALS,
    /** Correct or not, the account is locked for now. */
    LOCKED_OUT
  }

  /** Either the authenticated account, or the reason it was refused. */
  public record Attempt(AppUser user, Failure failure, Duration retryAfter) {

    public boolean succeeded() {
      return user != null;
    }
  }

  /**
   * Checks a user name and password.
   *
   * <p>Runs in its own transaction because it records the failure counter even when the attempt
   * is refused.
   */
  @Transactional
  public Attempt attempt(String username, String password) {
    Instant now = Instant.now(clock);
    AppUser user = username == null ? null : AppUser.findByUsername(username.trim());

    if (user == null) {
      // Spend the same time as a real verification, then fail.
      Passwords.matches(password == null ? "" : password, DUMMY_HASH);
      LOG.infof("Failed sign-in for unknown user name '%s'", username);
      return new Attempt(null, Failure.BAD_CREDENTIALS, null);
    }

    if (lockoutEnabled && user.isLockedAt(now)) {
      Duration remaining = Duration.between(now, user.lockedUntil);
      LOG.infof(
          "Refused sign-in for '%s': locked for another %d second(s)",
          user.username, remaining.toSeconds());
      return new Attempt(null, Failure.LOCKED_OUT, remaining);
    }

    boolean correct = Passwords.matches(password == null ? "" : password, user.password);
    if (!correct) {
      recordFailure(user, now);
      LOG.infof(
          "Failed sign-in for '%s' (%d consecutive failure(s))",
          user.username, user.failedLoginCount);
      return new Attempt(null, Failure.BAD_CREDENTIALS, null);
    }

    if (!user.active) {
      // Same answer as a wrong password: a deactivated account must not be a working oracle
      // telling a former employee that their password still works elsewhere.
      LOG.infof("Refused sign-in for the deactivated account '%s'", user.username);
      return new Attempt(null, Failure.BAD_CREDENTIALS, null);
    }

    user.failedLoginCount = 0;
    user.lockedUntil = null;
    return new Attempt(user, null, null);
  }

  private void recordFailure(AppUser user, Instant now) {
    user.failedLoginCount++;
    if (!lockoutEnabled || user.failedLoginCount < FAILURES_BEFORE_LOCK) {
      return;
    }
    int beyond = user.failedLoginCount - FAILURES_BEFORE_LOCK;
    // Doubling, but shifted through long arithmetic so a long run cannot overflow.
    long seconds = Math.min(MAX_LOCK.toSeconds(), BASE_LOCK.toSeconds() << Math.min(beyond, 16));
    user.lockedUntil = now.plusSeconds(seconds);
  }
}
