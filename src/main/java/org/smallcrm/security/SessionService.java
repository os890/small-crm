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
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.smallcrm.domain.AppSession;
import org.smallcrm.domain.AppUser;

/**
 * Issues, validates and revokes sessions.
 *
 * <p>The browser is given a random token; the database keeps only its SHA-256, so the session
 * table is not a list of usable credentials. Every session has both an idle timeout and an
 * absolute lifetime, and every session can be ended from the server — which is what makes
 * signing out real and lets a password change invalidate sessions issued before it.
 */
@ApplicationScoped
public class SessionService {

  private static final Logger LOG = Logger.getLogger(SessionService.class);

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  /**
   * How stale {@code lastSeenAt} may become before it is written again. Without this every
   * request would issue an UPDATE.
   */
  private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

  @ConfigProperty(name = "smallcrm.session.idle-timeout", defaultValue = "PT8H")
  Duration idleTimeout;

  @ConfigProperty(name = "smallcrm.session.absolute-timeout", defaultValue = "PT12H")
  Duration absoluteTimeout;

  @Inject Clock clock;

  /** A freshly issued session: the token for the browser, and when it stops working. */
  public record IssuedSession(String token, Instant expiresAt) {}

  public Duration idleTimeout() {
    return idleTimeout;
  }

  /** Creates a session for an account and returns the token to hand to the browser. */
  @Transactional
  public IssuedSession issue(AppUser user) {
    byte[] raw = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

    Instant now = Instant.now(clock);
    AppSession session = new AppSession();
    session.tokenHash = hash(token);
    session.user = user;
    session.createdAt = now;
    session.lastSeenAt = now;
    session.expiresAt = now.plus(absoluteTimeout);
    session.persist();

    // A good moment to clear out what has aged away, without needing a scheduler.
    purgeExpired();
    return new IssuedSession(token, session.expiresAt);
  }

  /**
   * Resolves a token to its account.
   *
   * @return the account, or {@code null} when the token is unknown, expired, idle for too long
   *     or belongs to a deactivated user
   */
  @ActivateRequestContext
  @Transactional
  public AppUser authenticate(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    AppSession session = AppSession.findById(hash(token));
    if (session == null) {
      return null;
    }
    Instant now = Instant.now(clock);
    if (!session.isUsableAt(now, idleTimeout)) {
      session.delete();
      return null;
    }
    AppUser user = session.user;
    if (user == null || !user.active) {
      // Deactivation takes effect immediately, on the authentication itself rather than in a
      // filter that only covers some paths.
      session.delete();
      return null;
    }
    if (session.lastSeenAt.plus(TOUCH_INTERVAL).isBefore(now)) {
      session.lastSeenAt = now;
    }
    return user;
  }

  /** Ends one session, used by signing out. */
  @Transactional
  public void revoke(String token) {
    if (token != null && !token.isBlank()) {
      AppSession.deleteById(hash(token));
    }
  }

  /**
   * Ends every session of an account.
   *
   * <p>Called when a password changes or is reset, and when an account is deactivated or
   * deleted, so a stolen session cannot outlive the thing that was supposed to stop it.
   */
  @Transactional
  public long revokeAllFor(AppUser user) {
    long removed = AppSession.delete("user", user);
    if (removed > 0) {
      LOG.infof("Ended %d session(s) of '%s'", removed, user.username);
    }
    return removed;
  }

  /** Removes rows that can no longer authenticate anybody. */
  @Transactional
  public long purgeExpired() {
    Instant now = Instant.now(clock);
    return AppSession.delete(
        "expiresAt < ?1 or lastSeenAt < ?2", now, now.minus(idleTimeout));
  }

  static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
