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

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import org.smallcrm.api.dto.CreateUserRequest;
import org.smallcrm.api.dto.ResetPasswordRequest;
import org.smallcrm.api.dto.UpdateUserRequest;
import org.smallcrm.api.dto.UserDto;
import org.smallcrm.domain.AppUser;
import org.smallcrm.service.UserService;

/** Account administration; reserved for administrators. */
@Path("/users")
@RolesAllowed(AppUser.ROLE_ADMIN)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

  @Inject UserService userService;

  @GET
  public List<UserDto> list() {
    return userService.list();
  }

  @GET
  @Path("/{id}")
  public UserDto get(@PathParam("id") Long id) {
    return userService.get(id);
  }

  @POST
  public Response create(@Valid CreateUserRequest request) {
    UserDto created = userService.create(request);
    return Response.created(URI.create("/api/users/" + created.id())).entity(created).build();
  }

  @PUT
  @Path("/{id}")
  public UserDto update(@PathParam("id") Long id, @Valid UpdateUserRequest request) {
    return userService.update(id, request);
  }

  @POST
  @Path("/{id}/password")
  public UserDto resetPassword(@PathParam("id") Long id, @Valid ResetPasswordRequest request) {
    return userService.resetPassword(id, request);
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") Long id) {
    userService.delete(id);
    return Response.noContent().build();
  }
}
