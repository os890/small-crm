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
import org.smallcrm.api.dto.InteractionDto;
import org.smallcrm.service.InteractionService;
import org.smallcrm.service.PageRequest;

/** CRUD endpoints for the activity log. */
@Path("/interactions")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InteractionResource {

  @Inject InteractionService interactionService;

  /** One page of the activity log; see {@link PagedResponse} for the paging headers. */
  @GET
  public Response list(
      @QueryParam("contactId") Long contactId,
      @QueryParam("dealId") Long dealId,
      @QueryParam("page") Integer page,
      @QueryParam("size") Integer size) {
    return PagedResponse.of(interactionService.list(contactId, dealId, PageRequest.of(page, size)));
  }

  @GET
  @Path("/{id}")
  public InteractionDto get(@PathParam("id") Long id) {
    return interactionService.get(id);
  }

  @POST
  public Response create(@Valid InteractionDto input) {
    InteractionDto created = interactionService.create(input);
    return Response.created(URI.create("/api/interactions/" + created.id()))
        .entity(created)
        .build();
  }

  @PUT
  @Path("/{id}")
  public InteractionDto update(@PathParam("id") Long id, @Valid InteractionDto input) {
    return interactionService.update(id, input);
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") Long id) {
    interactionService.delete(id);
    return Response.noContent().build();
  }
}
