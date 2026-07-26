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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.os890.smallcrm.api.dto.DashboardDto;
import org.os890.smallcrm.service.DashboardService;

/** Everything the start page needs, in one request. */
@Path("/dashboard")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

  @Inject DashboardService dashboardService;

  @GET
  public DashboardDto summary() {
    return dashboardService.summary();
  }
}
