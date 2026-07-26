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

package org.os890.smallcrm.api;

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
import org.os890.smallcrm.api.dto.CrmTaskDto;
import org.os890.smallcrm.service.CrmTaskService;
import org.os890.smallcrm.service.PageRequest;

/** CRUD endpoints for follow-up tasks. */
@Path("/tasks")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CrmTaskResource {

  @Inject CrmTaskService taskService;

  /** One page of tasks; see {@link PagedResponse} for the paging headers. */
  @GET
  public Response list(
      @QueryParam("openOnly") @DefaultValue("false") boolean openOnly,
      @QueryParam("contactId") Long contactId,
      @QueryParam("dealId") Long dealId,
      @QueryParam("page") Integer page,
      @QueryParam("size") Integer size) {
    return PagedResponse.of(
        taskService.list(openOnly, contactId, dealId, PageRequest.of(page, size)));
  }

  @GET
  @Path("/{id}")
  public CrmTaskDto get(@PathParam("id") Long id) {
    return taskService.get(id);
  }

  @POST
  public Response create(@Valid CrmTaskDto input) {
    CrmTaskDto created = taskService.create(input);
    return Response.created(URI.create("/api/tasks/" + created.id())).entity(created).build();
  }

  @PUT
  @Path("/{id}")
  public CrmTaskDto update(@PathParam("id") Long id, @Valid CrmTaskDto input) {
    return taskService.update(id, input);
  }

  /** One-click completion toggle used by the checkbox in the task list. */
  @PUT
  @Path("/{id}/done")
  public CrmTaskDto setDone(
      @PathParam("id") Long id, @QueryParam("value") @DefaultValue("true") boolean done) {
    return taskService.setDone(id, done);
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") Long id) {
    taskService.delete(id);
    return Response.noContent().build();
  }
}
