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

import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Reads and writes the session cookie.
 *
 * <p>All the flags live here, in one place, rather than being spread across configuration keys
 * and a hardcoded name in the logout endpoint. In particular the cookie is always
 * {@code HttpOnly}: the frontend never reads it, so keeping JavaScript away costs nothing and
 * means a future cross-site scripting bug cannot walk off with a session.
 */
@ApplicationScoped
public class SessionCookie {

  public static final String NAME = "smallcrm_session";

  /**
   * Whether to mark the cookie {@code Secure}.
   *
   * <p>Opt-in rather than automatic: a browser silently drops a Secure cookie over plain HTTP,
   * so switching it on before TLS is actually in front would lock everyone out with no
   * explanation. Behind a TLS terminating proxy set {@code SMALLCRM_HTTPS=true}.
   */
  @ConfigProperty(name = "smallcrm.session.cookie-secure", defaultValue = "false")
  boolean secure;

  /** Reads the token the browser sent, or {@code null}. */
  public String read(RoutingContext context) {
    Cookie cookie = context.request().getCookie(NAME);
    return cookie == null ? null : cookie.getValue();
  }

  /** Attaches a freshly issued token. */
  public void write(RoutingContext context, String token, long maxAgeSeconds) {
    context.response().addCookie(base(token).setMaxAge(maxAgeSeconds));
  }

  /** Tells the browser to forget the session. */
  public void clear(RoutingContext context) {
    context.response().addCookie(base("").setMaxAge(0));
  }

  private Cookie base(String value) {
    return Cookie.cookie(NAME, value)
        .setPath("/")
        .setHttpOnly(true)
        .setSecure(secure)
        // Strict keeps the cookie off cross-site requests entirely, which is what makes the
        // multipart restore endpoint unreachable from another origin's form.
        .setSameSite(CookieSameSite.STRICT);
  }
}
