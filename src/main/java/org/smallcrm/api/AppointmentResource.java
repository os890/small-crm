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
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.smallcrm.api.dto.AppointmentDto;
import org.smallcrm.service.AppointmentService;

/**
 * CRUD endpoints for appointments.
 *
 * <p>Writes refuse a slot that is already occupied with HTTP 409 and list the colliding entries,
 * unless {@code allowConflict=true} says the parallel booking is intentional.
 */
@Path("/api/appointments")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppointmentResource {

  @Inject AppointmentService appointmentService;

  @GET
  public List<AppointmentDto> list(
      @QueryParam("from") Instant from, @QueryParam("to") Instant to) {
    return appointmentService.list(from, to);
  }

  /**
   * Reports which appointments a slot would collide with, without storing anything. The dialog
   * calls this while the user is still typing so the warning appears before saving.
   */
  @GET
  @Path("/conflicts")
  public List<AppointmentDto> conflicts(
      @QueryParam("startsAt") Instant startsAt,
      @QueryParam("endsAt") Instant endsAt,
      @QueryParam("excludeId") Long excludeId) {
    return appointmentService.conflicts(excludeId, startsAt, endsAt);
  }

  @GET
  @Path("/{id}")
  public AppointmentDto get(@PathParam("id") Long id) {
    return appointmentService.get(id);
  }

  @POST
  public Response create(
      @Valid AppointmentDto input,
      @QueryParam("allowConflict") @DefaultValue("false") boolean allowConflict) {
    AppointmentDto created = appointmentService.create(input, allowConflict);
    return Response.created(URI.create("/api/appointments/" + created.id()))
        .entity(created)
        .build();
  }

  @PUT
  @Path("/{id}")
  public AppointmentDto update(
      @PathParam("id") Long id,
      @Valid AppointmentDto input,
      @QueryParam("allowConflict") @DefaultValue("false") boolean allowConflict) {
    return appointmentService.update(id, input, allowConflict);
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") Long id) {
    appointmentService.delete(id);
    return Response.noContent().build();
  }
}
