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
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.os890.smallcrm.api.error.BusinessRuleException;

/**
 * Whether the Google integration is switched on, and what it was given to work with.
 *
 * <p>Off by default and off in every installation that does not configure it. The endpoints
 * still exist but decline, rather than being absent, so the interface can ask once and show the
 * user a reason instead of a 404.
 */
@ApplicationScoped
public class GoogleConfig {

  /** Google's OAuth2 and API endpoints, overridable so tests can point at a stub. */
  @ConfigProperty(name = "smallcrm.google.auth-uri",
      defaultValue = "https://accounts.google.com/o/oauth2/v2/auth")
  String authUri;

  @ConfigProperty(name = "smallcrm.google.token-uri",
      defaultValue = "https://oauth2.googleapis.com/token")
  String tokenUri;

  @ConfigProperty(name = "smallcrm.google.api-base", defaultValue = "https://www.googleapis.com")
  String apiBase;

  @ConfigProperty(name = "smallcrm.google.people-base",
      defaultValue = "https://people.googleapis.com")
  String peopleBase;

  @ConfigProperty(name = "smallcrm.google.client-id")
  Optional<String> clientId;

  @ConfigProperty(name = "smallcrm.google.client-secret")
  Optional<String> clientSecret;

  @ConfigProperty(name = "smallcrm.google.redirect-uri")
  Optional<String> redirectUri;

  @ConfigProperty(name = "smallcrm.google.contact-label")
  Optional<String> contactLabel;

  @Inject TokenCrypto crypto;

  /** Identity only; asked for on every connection so the account can be recognised again. */
  public static final List<String> IDENTITY_SCOPES = List.of("openid", "email", "profile");

  /**
   * What each kind of sync needs.
   *
   * <p>Read and write, because the sync goes both ways. Consent is per scope and the user may
   * grant some and refuse others, which is why what was actually granted is stored rather than
   * what was requested.
   */
  public static final String CONTACTS_SCOPE = "https://www.googleapis.com/auth/contacts";

  public static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar";

  public static final String TASKS_SCOPE = "https://www.googleapis.com/auth/tasks";

  public static final List<String> SYNC_SCOPES =
      List.of(CONTACTS_SCOPE, CALENDAR_SCOPE, TASKS_SCOPE);

  /**
   * Whether the integration can be used at all.
   *
   * <p>Both halves are needed: a client to talk to Google with, and a key to store what comes
   * back. Missing either is a configuration problem, not a user problem, so the interface hides
   * the feature rather than offering something that will fail.
   */
  public boolean isEnabled() {
    return present(clientId) && present(clientSecret) && present(redirectUri) && crypto
        .isConfigured();
  }

  /** Why it is off, phrased for whoever configures the installation. */
  public String disabledReason() {
    if (!present(clientId) || !present(clientSecret)) {
      return "SMALLCRM_GOOGLE_CLIENT_ID and SMALLCRM_GOOGLE_CLIENT_SECRET are not set.";
    }
    if (!present(redirectUri)) {
      return "SMALLCRM_GOOGLE_REDIRECT_URI is not set.";
    }
    if (!crypto.isConfigured()) {
      return "SMALLCRM_TOKEN_KEY is not set, so Google credentials could not be stored safely.";
    }
    return "";
  }

  public String clientId() {
    return require(clientId, "SMALLCRM_GOOGLE_CLIENT_ID");
  }

  public String clientSecret() {
    return require(clientSecret, "SMALLCRM_GOOGLE_CLIENT_SECRET");
  }

  public String redirectUri() {
    return require(redirectUri, "SMALLCRM_GOOGLE_REDIRECT_URI");
  }

  /** The Google label whose contacts are synced; everything else stays in Google. */
  public String contactLabel() {
    return contactLabel.filter(value -> !value.isBlank()).orElse("Small CRM");
  }

  public String authUri() {
    return authUri;
  }

  public String tokenUri() {
    return tokenUri;
  }

  public String apiBase() {
    return apiBase;
  }

  public String peopleBase() {
    return peopleBase;
  }

  private static boolean present(Optional<String> value) {
    return value.filter(candidate -> !candidate.isBlank()).isPresent();
  }

  private static String require(Optional<String> value, String name) {
    return value
        .filter(candidate -> !candidate.isBlank())
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    "GOOGLE_NOT_CONFIGURED", name + " is not set on this installation."));
  }
}
