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

package org.smallcrm.api;

import io.quarkus.security.Authenticated;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import org.jboss.resteasy.reactive.RestForm;
import org.smallcrm.api.dto.ChangePasswordRequest;
import org.smallcrm.api.dto.UserDto;
import org.smallcrm.api.error.ApiError;
import org.smallcrm.security.LoginService;
import org.smallcrm.security.SessionCookie;
import org.smallcrm.security.SessionService;
import org.smallcrm.service.UserService;

/**
 * Signing in and out, and the signed-in profile.
 *
 * <p>The login is an ordinary endpoint rather than a framework interception, which is what lets
 * it lock out repeated failures, keep a missing account and a wrong password
 * indistinguishable in both answer and timing, and refuse deactivated accounts before a cookie
 * is ever issued.
 *
 * <p>The request body stays form encoded, which is what browsers send from a password manager
 * and what the frontend already posts.
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

  @Inject LoginService loginService;
  @Inject SessionService sessions;
  @Inject SessionCookie cookie;
  @Inject UserService userService;
  @Inject RoutingContext routingContext;

  /** Verifies credentials and starts a session. */
  @POST
  @Path("/login")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response login(
      @RestForm("username") String username, @RestForm("password") String password) {
    LoginService.Attempt attempt = loginService.attempt(username, password);

    if (!attempt.succeeded()) {
      if (attempt.failure() == LoginService.Failure.LOCKED_OUT) {
        long seconds = Math.max(1, attempt.retryAfter().toSeconds());
        return Response.status(Response.Status.TOO_MANY_REQUESTS)
            .header("Retry-After", seconds)
            .entity(
                ApiError.of(
                    "TOO_MANY_ATTEMPTS",
                    "Too many failed attempts. Try again in " + seconds + " seconds."))
            .build();
      }
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(ApiError.of("BAD_CREDENTIALS", "User name or password is not correct"))
          .build();
    }

    SessionService.IssuedSession issued = sessions.issue(attempt.user());
    cookie.write(
        routingContext,
        issued.token(),
        Duration.between(Instant.now(), issued.expiresAt()).toSeconds());
    return Response.noContent().build();
  }

  /** The signed-in profile, including whether the password still has to be changed. */
  @GET
  @Path("/me")
  @Authenticated
  public UserDto me() {
    return userService.me();
  }

  /**
   * Replaces the own password, clears the "must change password" flag and ends every other
   * session of this account.
   */
  @POST
  @Path("/password")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public UserDto changePassword(@Valid ChangePasswordRequest request) {
    UserDto updated = userService.changeOwnPassword(request);
    // Every session was just revoked, including this one, so issue a fresh cookie rather than
    // signing the user out of the browser they are actively using.
    reissueSessionForCurrentUser();
    return updated;
  }

  /** Ends this session, on the server as well as in the browser. */
  @POST
  @Path("/logout")
  public Response logout() {
    sessions.revoke(cookie.read(routingContext));
    cookie.clear(routingContext);
    return Response.noContent().build();
  }

  private void reissueSessionForCurrentUser() {
    userService
        .currentAccount()
        .ifPresent(
            user -> {
              SessionService.IssuedSession issued = sessions.issue(user);
              cookie.write(
                  routingContext,
                  issued.token(),
                  Duration.between(Instant.now(), issued.expiresAt()).toSeconds());
            });
  }
}
