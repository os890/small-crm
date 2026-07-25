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

package org.smallcrm.backup;

import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.Set;

/**
 * Notices that a request changed the customer data and asks for a backup.
 *
 * <p>Watching the HTTP layer rather than the entities keeps the rule easy to state and to test:
 * a write method that succeeded on a data endpoint counts as a change. Endpoints that cannot
 * affect the contents of a backup are skipped, because a backup that is identical to the
 * previous one is only noise in the folder.
 */
@Provider
public class DataChangeFilter implements ContainerResponseFilter {

  private static final Set<String> WRITE_METHODS =
      Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH);

  /**
   * Paths whose writes never change what a backup contains: sessions, accounts (deliberately not
   * part of a backup) and the backup endpoints themselves, which would otherwise have a restore
   * immediately trigger a backup of what it just restored.
   */
  private static final Set<String> IGNORED_PREFIXES =
      Set.of("api/auth", "api/users", "api/backups");

  @Inject AutoBackupTrigger trigger;

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    if (!WRITE_METHODS.contains(request.getMethod())) {
      return;
    }
    if (response.getStatus() < 200 || response.getStatus() >= 300) {
      return;
    }
    String path = normalise(request.getUriInfo().getPath());
    if (IGNORED_PREFIXES.stream().anyMatch(path::startsWith)) {
      return;
    }
    trigger.dataChanged();
  }

  private static String normalise(String path) {
    return path.startsWith("/") ? path.substring(1) : path;
  }
}
