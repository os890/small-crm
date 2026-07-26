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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.os890.smallcrm.api.error.BusinessRuleException;

/**
 * The OAuth2 half of talking to Google: consent URLs, code exchange, refresh and revocation.
 *
 * <p>Written against the JDK's HTTP client rather than Google's own libraries. The endpoints are
 * ordinary form-encoded HTTP returning JSON, Jackson is already here, and the alternative pulls
 * a dozen artefacts into the build to save perhaps eighty lines.
 */
@ApplicationScoped
public class GoogleOAuthClient {

  private static final Logger LOG = Logger.getLogger(GoogleOAuthClient.class);

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @Inject GoogleConfig config;
  @Inject ObjectMapper json;

  /**
   * The address to send the browser to for consent.
   *
   * @param state opaque value returned untouched by Google, used to recognise our own request
   * @param scopes what to ask for on top of identity
   * @param forceConsent true when a refresh token is needed; Google only issues one on a fresh
   *     grant, so re-connecting an account that already agreed otherwise yields nothing to
   *     store
   */
  public String authorizationUrl(String state, List<String> scopes, boolean forceConsent) {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("client_id", config.clientId());
    parameters.put("redirect_uri", config.redirectUri());
    parameters.put("response_type", "code");
    parameters.put("scope", String.join(" ", scopes));
    parameters.put("state", state);
    // Without this Google issues no refresh token, and the integration would stop working the
    // moment the first access token expired.
    parameters.put("access_type", "offline");
    parameters.put("include_granted_scopes", "true");
    if (forceConsent) {
      parameters.put("prompt", "consent");
    }
    return config.authUri() + "?" + form(parameters);
  }

  /** Trades the code the browser came back with for tokens. */
  public GoogleTokens exchangeCode(String code) {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("code", code);
    parameters.put("client_id", config.clientId());
    parameters.put("client_secret", config.clientSecret());
    parameters.put("redirect_uri", config.redirectUri());
    parameters.put("grant_type", "authorization_code");
    return post(parameters, "exchange the authorisation code");
  }

  /** Obtains a fresh access token. The response carries no new refresh token. */
  public GoogleTokens refresh(String refreshToken) {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("refresh_token", refreshToken);
    parameters.put("client_id", config.clientId());
    parameters.put("client_secret", config.clientSecret());
    parameters.put("grant_type", "refresh_token");
    return post(parameters, "refresh the access token");
  }

  /**
   * Asks Google to invalidate the grant, so disconnecting here also withdraws access there.
   *
   * <p>Failure is logged and swallowed: the local record is removed either way, and leaving a
   * user connected in our database because Google was unreachable would be the worse outcome.
   */
  public void revoke(String token) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(config.apiBase() + "/revoke"))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("token", token))))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        LOG.warnf("Google refused to revoke the token: %d", response.statusCode());
      }
    } catch (IOException e) {
      LOG.warn("Could not reach Google to revoke the token", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while revoking the token", e);
    }
  }

  /**
   * Reads the subject and e-mail out of an id token.
   *
   * <p>The signature is not verified, and that is deliberate rather than an omission: this token
   * did not arrive through the browser but from a direct TLS connection to Google's own token
   * endpoint, which is the case OpenID Connect explicitly allows server validation to stand in
   * for signature checking. Verifying would mean fetching and rotating Google's JWKS to prove
   * something the transport already proved.
   */
  public GoogleIdentity readIdentity(String idToken) {
    if (idToken == null || idToken.isBlank()) {
      throw new BusinessRuleException(
          "GOOGLE_NO_IDENTITY", "Google did not say which account this is.");
    }
    String[] parts = idToken.split("\\.");
    if (parts.length < 2) {
      throw new BusinessRuleException(
          "GOOGLE_NO_IDENTITY", "Google's identity token was not readable.");
    }
    try {
      byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
      JsonNode claims = json.readTree(payload);
      String subject = claims.path("sub").asText(null);
      String email = claims.path("email").asText(null);
      if (subject == null || email == null) {
        throw new BusinessRuleException(
            "GOOGLE_NO_IDENTITY", "Google's identity token named no account.");
      }
      return new GoogleIdentity(subject, email, claims.path("email_verified").asBoolean(false));
    } catch (IOException | IllegalArgumentException e) {
      throw new BusinessRuleException(
          "GOOGLE_NO_IDENTITY", "Google's identity token was not readable.");
    }
  }

  /** Who Google says the person is. */
  public record GoogleIdentity(String subject, String email, boolean emailVerified) {}

  private GoogleTokens post(Map<String, String> parameters, String what) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(config.tokenUri()))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(form(parameters)))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        // Google's body names the reason, but it can also carry the client secret back in an
        // error echo, so it is logged rather than returned to the browser.
        LOG.errorf("Google refused to %s: %d %s", what, response.statusCode(), response.body());
        throw new BusinessRuleException(
            "GOOGLE_REFUSED", "Google refused to " + what + ". Try connecting the account again.");
      }
      return json.readValue(response.body(), GoogleTokens.class);
    } catch (IOException e) {
      throw new BusinessRuleException(
          "GOOGLE_UNREACHABLE", "Google could not be reached. Try again in a moment.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessRuleException(
          "GOOGLE_UNREACHABLE", "Google could not be reached. Try again in a moment.");
    }
  }

  private static String form(Map<String, String> parameters) {
    StringBuilder encoded = new StringBuilder();
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      if (!encoded.isEmpty()) {
        encoded.append('&');
      }
      encoded
          .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }
    return encoded.toString();
  }
}
