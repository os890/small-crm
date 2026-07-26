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

package org.os890.smallcrm.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;
import java.util.Set;
import org.jboss.logging.Logger;
import org.os890.smallcrm.api.error.ApiError;

/**
 * Refuses a state-changing request that came from another origin.
 *
 * <p>{@code SameSite=strict} already keeps the session cookie off cross-site requests, and every
 * JSON endpoint declares {@code @Consumes(application/json)}, which a plain HTML form cannot
 * produce. Two endpoints fall outside that: the login, which is form encoded, and the backup
 * restore, which is {@code multipart/form-data} — both submittable by a cross-origin
 * {@code <form>}. The restore replaces the entire database, so relying on a single control for
 * it is thin.
 *
 * <p>The check is an origin comparison rather than a token, because the application is
 * same-origin by construction: the frontend is served by this very process. Requests with no
 * {@code Origin} header at all are allowed, since non-browser clients such as curl do not send
 * one and browsers always do for the methods in question.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class CrossOriginWriteFilter implements ContainerRequestFilter {

  private static final Logger LOG = Logger.getLogger(CrossOriginWriteFilter.class);

  private static final Set<String> WRITE_METHODS =
      Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH);

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (!WRITE_METHODS.contains(requestContext.getMethod())) {
      return;
    }
    String origin = requestContext.getHeaderString("Origin");
    if (origin == null || origin.isBlank() || "null".equals(origin)) {
      // No Origin header: not a browser cross-site request.
      return;
    }
    URI target = requestContext.getUriInfo().getRequestUri();
    if (sameOrigin(origin, target)) {
      return;
    }
    LOG.warnf(
        "Refused a cross-origin %s to %s from %s",
        requestContext.getMethod(), target.getPath(), origin);
    requestContext.abortWith(
        Response.status(Response.Status.FORBIDDEN)
            .entity(
                ApiError.of(
                    "CROSS_ORIGIN_REFUSED",
                    "This request came from another site and was refused."))
            .build());
  }

  private static boolean sameOrigin(String origin, URI target) {
    try {
      URI source = URI.create(origin);
      return source.getScheme() != null
          && source.getScheme().equalsIgnoreCase(target.getScheme())
          && source.getHost() != null
          && source.getHost().equalsIgnoreCase(target.getHost())
          && port(source) == port(target);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static int port(URI uri) {
    if (uri.getPort() != -1) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }
}
