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
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.smallcrm.api.dto.ChangePasswordRequest;
import org.smallcrm.api.dto.UserDto;
import org.smallcrm.service.UserService;

/**
 * Session endpoints.
 *
 * <p>{@code POST /api/auth/login} itself is handled by the Quarkus form authentication mechanism,
 * which intercepts the request before it reaches a resource method; it expects a form encoded body
 * with {@code username} and {@code password}. What remains here is everything the frontend needs
 * around that: reading the current profile, changing the password and signing out.
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

  private static final String SESSION_COOKIE = "quarkus-credential";

  @Inject UserService userService;

  /** The signed-in profile, including whether the password still has to be changed. */
  @GET
  @Path("/me")
  @Authenticated
  public UserDto me() {
    return userService.me();
  }

  /** Replaces the own password and clears the "must change password" flag. */
  @POST
  @Path("/password")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public UserDto changePassword(@Valid ChangePasswordRequest request) {
    return userService.changeOwnPassword(request);
  }

  /** Clears the session cookie. Safe to call when no session exists. */
  @POST
  @Path("/logout")
  public Response logout() {
    NewCookie expired =
        new NewCookie.Builder(SESSION_COOKIE)
            .value("")
            .path("/")
            .maxAge(0)
            .httpOnly(true)
            .build();
    return Response.noContent().cookie(expired).build();
  }
}
