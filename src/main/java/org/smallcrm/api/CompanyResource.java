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
import org.smallcrm.api.dto.CompanyDto;
import org.smallcrm.service.CompanyService;
import org.smallcrm.service.PageRequest;

/** CRUD endpoints for companies. */
@Path("/companies")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CompanyResource {

  @Inject CompanyService companyService;

  /** One page of companies; see {@link PagedResponse} for the paging headers. */
  @GET
  public Response list(
      @QueryParam("search") String search,
      @QueryParam("page") Integer page,
      @QueryParam("size") Integer size) {
    return PagedResponse.of(companyService.list(search, PageRequest.of(page, size)));
  }

  @GET
  @Path("/{id}")
  public CompanyDto get(@PathParam("id") Long id) {
    return companyService.get(id);
  }

  @POST
  public Response create(@Valid CompanyDto input) {
    CompanyDto created = companyService.create(input);
    return Response.created(URI.create("/api/companies/" + created.id())).entity(created).build();
  }

  @PUT
  @Path("/{id}")
  public CompanyDto update(@PathParam("id") Long id, @Valid CompanyDto input) {
    return companyService.update(id, input);
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") Long id) {
    companyService.delete(id);
    return Response.noContent().build();
  }
}
