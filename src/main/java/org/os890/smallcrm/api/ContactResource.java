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
import java.util.List;
import org.os890.smallcrm.api.dto.ContactDto;
import org.os890.smallcrm.service.ContactService;
import org.os890.smallcrm.service.PageRequest;

/** CRUD endpoints for contacts. */
@Path("/contacts")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContactResource {

  @Inject ContactService contactService;

  /** One page of contacts; see {@link PagedResponse} for the paging headers. */
  @GET
  public Response list(
      @QueryParam("search") String search,
      @QueryParam("companyId") Long companyId,
      @QueryParam("page") Integer page,
      @QueryParam("size") Integer size) {
    return PagedResponse.of(contactService.list(search, companyId, PageRequest.of(page, size)));
  }

  @GET
  @Path("/tags")
  public List<String> tags() {
    return contactService.allTags();
  }

  @GET
  @Path("/{id}")
  public ContactDto get(@PathParam("id") Long id) {
    return contactService.get(id);
  }

  @POST
  public Response create(@Valid ContactDto input) {
    ContactDto created = contactService.create(input);
    return Response.created(URI.create("/api/contacts/" + created.id())).entity(created).build();
  }

  @PUT
  @Path("/{id}")
  public ContactDto update(@PathParam("id") Long id, @Valid ContactDto input) {
    return contactService.update(id, input);
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") Long id) {
    contactService.delete(id);
    return Response.noContent().build();
  }
}
