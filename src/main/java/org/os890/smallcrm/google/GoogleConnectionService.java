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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;
import org.os890.smallcrm.api.error.BusinessRuleException;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.domain.GoogleAccount;

/**
 * Connecting a Google account to a CRM account, and using it to sign in afterwards.
 *
 * <p>Google never creates an account here. An administrator invites somebody, that person signs
 * in with the password they were given and connects Google from their own settings; only then
 * does signing in with Google work for them. Anything else would mean a stranger with a Google
 * account could walk into a database of somebody's customers.
 */
@ApplicationScoped
public class GoogleConnectionService {

  private static final Logger LOG = Logger.getLogger(GoogleConnectionService.class);

  /** How long a consent round trip may take before the request is no longer recognised. */
  private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);

  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * Outstanding consent requests, by their state value.
   *
   * <p>In memory on purpose. These live for minutes, only matter while a browser is away at
   * Google, and a restart mid-consent costs the user one click. Putting them in the database
   * would mean a table whose rows are garbage within the hour.
   */
  private final Map<String, PendingConsent> pending = new ConcurrentHashMap<>();

  @Inject GoogleConfig config;
  @Inject GoogleOAuthClient oauth;
  @Inject TokenCrypto crypto;
  @Inject Clock clock;

  /**
   * A consent request that has been started and not yet come back.
   *
   * @param userId the account being linked, or null when this is a sign-in
   */
  private record PendingConsent(Long userId, Instant expiresAt) {}

  /** What the callback turned out to be for. */
  public sealed interface Outcome {
    /** An account was linked; the user was already signed in. */
    record Linked(AppUser user, String email) implements Outcome {}

    /** Somebody signed in with a Google account already linked to this user. */
    record SignedIn(AppUser user) implements Outcome {}
  }

  public boolean isEnabled() {
    return config.isEnabled();
  }

  /**
   * Starts linking Google to the signed-in account.
   *
   * @return where to send the browser
   */
  public String startLinking(AppUser user) {
    requireEnabled();
    List<String> scopes = new ArrayList<>(GoogleConfig.IDENTITY_SCOPES);
    scopes.addAll(GoogleConfig.SYNC_SCOPES);
    // Forced consent, because Google hands out a refresh token only on a fresh grant and
    // without one the connection dies with the first access token.
    return oauth.authorizationUrl(newState(user.id), scopes, true);
  }

  /**
   * Starts a sign-in with Google.
   *
   * <p>Only identity is asked for. Whoever comes back must already be linked; nothing is created
   * from this path, so there is no reason to request access to their data at this point.
   */
  public String startSignIn() {
    requireEnabled();
    return oauth.authorizationUrl(newState(null), GoogleConfig.IDENTITY_SCOPES, false);
  }

  /**
   * Handles the browser coming back from Google.
   *
   * @throws BusinessRuleException if the state is unknown or stale, if the account is not
   *     linked, or if the Google account is already linked to somebody else
   */
  @Transactional
  public Outcome complete(String code, String state) {
    requireEnabled();
    PendingConsent consent = consume(state);
    GoogleTokens tokens = oauth.exchangeCode(code);
    GoogleOAuthClient.GoogleIdentity identity = oauth.readIdentity(tokens.idToken());

    if (!identity.emailVerified()) {
      // An unverified address on a Google account is not evidence of anything.
      throw new BusinessRuleException(
          "GOOGLE_EMAIL_UNVERIFIED", "That Google account has no verified e-mail address.");
    }

    return consent.userId() == null
        ? signIn(identity)
        : link(consent.userId(), identity, tokens);
  }

  private Outcome signIn(GoogleOAuthClient.GoogleIdentity identity) {
    GoogleAccount account = GoogleAccount.findBySubject(identity.subject());
    if (account == null) {
      // Deliberately the same answer whether the Google account is unknown or belongs to
      // nobody here: a stranger learns nothing about who has an account.
      throw new BusinessRuleException(
          "GOOGLE_NOT_LINKED",
          "That Google account is not connected to anybody here."
              + " Sign in with your password first, then connect it from your settings.");
    }
    if (!account.user.active) {
      throw new BusinessRuleException(
          "GOOGLE_NOT_LINKED",
          "That Google account is not connected to anybody here."
              + " Sign in with your password first, then connect it from your settings.");
    }
    return new Outcome.SignedIn(account.user);
  }

  private Outcome link(Long userId, GoogleOAuthClient.GoogleIdentity identity,
      GoogleTokens tokens) {
    AppUser user = AppUser.findById(userId);
    if (user == null || !user.active) {
      throw new BusinessRuleException(
          "GOOGLE_LINK_FAILED", "The account being connected no longer exists.");
    }

    GoogleAccount existing = GoogleAccount.findBySubject(identity.subject());
    if (existing != null && !existing.userId.equals(userId)) {
      throw new BusinessRuleException(
          "GOOGLE_ALREADY_LINKED",
          "That Google account is already connected to another user here.");
    }

    GoogleAccount account = GoogleAccount.findByUser(userId);
    if (account == null) {
      account = new GoogleAccount();
      account.user = user;
      account.userId = userId;
    }
    account.subject = identity.subject();
    account.email = identity.email();
    if (tokens.refreshToken() != null) {
      account.refreshToken = crypto.encrypt(tokens.refreshToken());
    } else if (account.refreshToken == null) {
      // Google issues one only on a fresh grant. Without it the connection is useless, so it
      // is refused now rather than failing at the first sync.
      throw new BusinessRuleException(
          "GOOGLE_NO_REFRESH_TOKEN",
          "Google did not grant lasting access. Remove Small CRM from your Google account's"
              + " third-party access and connect it again.");
    }
    applyAccessToken(account, tokens);
    account.scopes = tokens.scopeOrEmpty();
    account.persist();
    LOG.infof("Connected Google account %s to user %s", identity.email(), user.username);
    return new Outcome.Linked(user, identity.email());
  }

  /** Removes the connection here and withdraws the grant at Google. */
  @Transactional
  public void disconnect(AppUser user) {
    GoogleAccount account = GoogleAccount.findByUser(user.id);
    if (account == null) {
      return;
    }
    String refreshToken = crypto.decrypt(account.refreshToken);
    account.delete();
    // After the local record is gone, so a failure at Google cannot leave a row nobody can use.
    oauth.revoke(refreshToken);
    LOG.infof("Disconnected the Google account of user %s", user.username);
  }

  /**
   * An access token that is good right now, refreshing if the cached one has run out.
   *
   * @throws BusinessRuleException if Google will no longer refresh it, which means the user
   *     revoked access on their side and has to connect again
   */
  @Transactional
  public String freshAccessToken(GoogleAccount account) {
    Instant now = Instant.now(clock);
    if (account.hasUsableAccessToken(now)) {
      return crypto.decrypt(account.accessToken);
    }
    GoogleTokens refreshed = oauth.refresh(crypto.decrypt(account.refreshToken));
    GoogleAccount attached = GoogleAccount.findByUser(account.userId);
    applyAccessToken(attached, refreshed);
    return refreshed.accessToken();
  }

  private void applyAccessToken(GoogleAccount account, GoogleTokens tokens) {
    if (tokens.accessToken() == null) {
      return;
    }
    account.accessToken = crypto.encrypt(tokens.accessToken());
    account.accessExpires = Instant.now(clock).plusSeconds(tokens.lifetimeSeconds());
  }

  private String newState(Long userId) {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    forgetExpired();
    pending.put(state, new PendingConsent(userId, Instant.now(clock).plus(STATE_LIFETIME)));
    return state;
  }

  /**
   * Recognises a state value and uses it up.
   *
   * <p>Single use: a callback replayed with the same state is refused, which is what stops
   * somebody replaying a consent round trip they observed.
   */
  private PendingConsent consume(String state) {
    forgetExpired();
    PendingConsent consent = state == null ? null : pending.remove(state);
    if (consent == null || consent.expiresAt().isBefore(Instant.now(clock))) {
      throw new BusinessRuleException(
          "GOOGLE_STATE_UNKNOWN",
          "That sign-in with Google took too long or was not started here. Try again.");
    }
    return consent;
  }

  private void forgetExpired() {
    Instant now = Instant.now(clock);
    pending.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
  }

  private void requireEnabled() {
    if (!config.isEnabled()) {
      throw new BusinessRuleException(
          "GOOGLE_NOT_CONFIGURED",
          "The Google integration is not set up on this installation. " + config.disabledReason());
    }
  }
}
