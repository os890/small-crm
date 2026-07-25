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

package org.smallcrm.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.smallcrm.domain.AppUser;

/**
 * Resolves the {@link AppUser} behind the authenticated identity of the current request.
 *
 * <p>The entity is deliberately looked up again on every call instead of being cached for the
 * request. A request touches the database both outside and inside a transaction, and those use
 * different Hibernate sessions; handing out an instance loaded by the earlier session would mean
 * changes made to it inside a transaction are never written. Repeated lookups inside one
 * transaction are served from Hibernate's first level cache, so this costs nothing in practice.
 */
@RequestScoped
public class CurrentUser {

  @Inject SecurityIdentity identity;

  /** The signed-in account, or empty for anonymous requests. */
  public Optional<AppUser> find() {
    if (identity == null || identity.isAnonymous() || identity.getPrincipal() == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(AppUser.findByUsername(identity.getPrincipal().getName()));
  }

  /**
   * The signed-in account.
   *
   * @throws IllegalStateException if the request is not authenticated
   */
  public AppUser get() {
    return find().orElseThrow(() -> new IllegalStateException("No authenticated user in request"));
  }
}
