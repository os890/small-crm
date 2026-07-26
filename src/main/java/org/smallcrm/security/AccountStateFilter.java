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

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Set;
import org.smallcrm.api.error.ApiError;
import org.smallcrm.domain.AppUser;

/**
 * Blocks API access for accounts that are deactivated or still carry a password that has to be
 * replaced.
 *
 * <p>A very small allow list keeps the endpoints reachable that the user needs in order to get out
 * of the "must change password" state.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 10)
public class AccountStateFilter implements ContainerRequestFilter {

  private static final Set<String> ALWAYS_ALLOWED =
      Set.of("api/auth/me", "api/auth/password", "api/auth/login", "api/auth/logout");

  @Inject CurrentUser currentUser;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (requestContext.getSecurityContext().getUserPrincipal() == null) {
      // Anonymous; the permission layer already decided this path is reachable.
      return;
    }
    AppUser user = currentUser.find().orElse(null);
    if (user == null) {
      // Authenticated but no account: the row vanished mid-request. Refuse rather than fall
      // through, which is the wrong default for a security filter even when unreachable.
      abort(
          requestContext,
          Response.Status.UNAUTHORIZED,
          "SESSION_INVALID",
          "This session is no longer valid.");
      return;
    }
    String path = requestContext.getUriInfo().getPath();
    if (user.mustChangePassword && !ALWAYS_ALLOWED.contains(normalise(path))) {
      abort(
          requestContext,
          Response.Status.FORBIDDEN,
          "PASSWORD_CHANGE_REQUIRED",
          "The password must be changed before the application can be used.");
    }
  }

  private static String normalise(String path) {
    String trimmed = path.startsWith("/") ? path.substring(1) : path;
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  private static void abort(
      ContainerRequestContext context, Response.Status status, String code, String message) {
    context.abortWith(
        Response.status(status).entity(new ApiError(code, message, null)).build());
  }
}
