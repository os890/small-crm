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
import java.util.LinkedHashMap;
import java.util.Map;
import org.jboss.logging.Logger;
import org.os890.smallcrm.domain.GoogleAccount;

/**
 * The calling half of talking to Google: one authenticated request, with the failures that
 * matter told apart from each other.
 *
 * <p>Three answers mean different things and a sync has to react differently to each. A 401 is
 * a token that needs refreshing, and is retried once. A 410 is an expired sync token, which no
 * retry fixes — the next pass has to be a full one. A 429 or a 5xx is Google asking to be left
 * alone, which means stopping and trying later rather than hammering.
 */
@ApplicationScoped
public class GoogleApi {

  private static final Logger LOG = Logger.getLogger(GoogleApi.class);

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @Inject GoogleConfig config;
  @Inject GoogleConnectionService connections;
  @Inject ObjectMapper json;

  /** Raised when Google says the sync token is no longer usable. */
  public static class SyncTokenExpired extends RuntimeException {
    public SyncTokenExpired(String message) {
      super(message);
    }
  }

  /** Raised when Google is unwilling right now: rate limited, or having a bad day. */
  public static class TryAgainLater extends RuntimeException {
    public TryAgainLater(String message) {
      super(message);
    }
  }

  /** Raised when Google refuses in a way that retrying will not fix. */
  public static class GoogleRefused extends RuntimeException {
    private final int status;

    public GoogleRefused(int status, String message) {
      super(message);
      this.status = status;
    }

    public int status() {
      return status;
    }
  }

  public JsonNode get(GoogleAccount account, String url, Map<String, String> query) {
    return send(account, "GET", withQuery(url, query), null);
  }

  public JsonNode post(GoogleAccount account, String url, Object body) {
    return send(account, "POST", url, write(body));
  }

  public JsonNode patch(GoogleAccount account, String url, Map<String, String> query,
      Object body) {
    return send(account, "PATCH", withQuery(url, query), write(body));
  }

  public JsonNode put(GoogleAccount account, String url, Object body) {
    return send(account, "PUT", url, write(body));
  }

  public void delete(GoogleAccount account, String url) {
    send(account, "DELETE", url, null);
  }

  /**
   * Sends one request, refreshing the access token once if Google says it is stale.
   *
   * <p>Only once: a second 401 after a fresh token means the grant itself is gone, which the
   * user has to fix by connecting again, and retrying would only turn that into a loop.
   */
  private JsonNode send(GoogleAccount account, String method, String url, String body) {
    HttpResponse<String> response = exchange(account, method, url, body);
    if (response.statusCode() == 401) {
      LOG.debugf("Google rejected the access token for %s, refreshing once", account.email);
      response = exchange(account, method, url, body);
    }
    int status = response.statusCode();
    if (status / 100 == 2) {
      return parse(response.body());
    }
    if (status == 410) {
      throw new SyncTokenExpired("Google expired the sync token; the next pass must be a full one");
    }
    if (status == 429 || status / 100 == 5) {
      throw new TryAgainLater("Google answered " + status + "; trying again later");
    }
    throw new GoogleRefused(status, describe(status, response.body()));
  }

  private HttpResponse<String> exchange(
      GoogleAccount account, String method, String url, String body) {
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(30))
              .header("Authorization", "Bearer " + connections.freshAccessToken(account))
              .header("Accept", "application/json");
      if (body == null) {
        request.method(method, HttpRequest.BodyPublishers.noBody());
      } else {
        request
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body));
      }
      return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new TryAgainLater("Google could not be reached: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TryAgainLater("Interrupted while talking to Google");
    }
  }

  /** Google's errors carry a readable message; falling back to the status when they do not. */
  private String describe(int status, String body) {
    try {
      JsonNode message = parse(body).path("error").path("message");
      if (!message.isMissingNode() && !message.asText().isBlank()) {
        return message.asText();
      }
    } catch (RuntimeException e) {
      // Not JSON, or not the shape Google documents. The status still says something.
    }
    return "Google answered " + status;
  }

  private JsonNode parse(String body) {
    try {
      return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
    } catch (IOException e) {
      throw new GoogleRefused(502, "Google's answer was not readable JSON");
    }
  }

  private String write(Object body) {
    try {
      return json.writeValueAsString(body);
    } catch (IOException e) {
      throw new IllegalStateException("Could not serialise a request for Google", e);
    }
  }

  private static String withQuery(String url, Map<String, String> query) {
    if (query == null || query.isEmpty()) {
      return url;
    }
    Map<String, String> present = new LinkedHashMap<>();
    query.forEach(
        (key, value) -> {
          if (value != null && !value.isBlank()) {
            present.put(key, value);
          }
        });
    if (present.isEmpty()) {
      return url;
    }
    StringBuilder built = new StringBuilder(url).append(url.contains("?") ? '&' : '?');
    boolean first = true;
    for (Map.Entry<String, String> entry : present.entrySet()) {
      if (!first) {
        built.append('&');
      }
      first = false;
      built
          .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }
    return built.toString();
  }
}
