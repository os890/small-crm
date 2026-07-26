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

package org.os890.smallcrm.google;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.os890.smallcrm.api.dto.GoogleStatusDto;
import org.os890.smallcrm.api.dto.GoogleStatusDto.GoogleResourceStatusDto;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.domain.GoogleAccount;
import org.os890.smallcrm.domain.GoogleSyncState;
import org.os890.smallcrm.domain.GoogleSyncState.Resource;

/** Reads back the state of one user's Google connection for the settings screen. */
@ApplicationScoped
public class GoogleStatusService {

  /** Which scope each resource needs, so a partial consent can be reported honestly. */
  private static final Map<Resource, String> SCOPES =
      Map.of(
          Resource.CONTACTS, GoogleConfig.CONTACTS_SCOPE,
          Resource.CALENDAR, GoogleConfig.CALENDAR_SCOPE,
          Resource.TASKS, GoogleConfig.TASKS_SCOPE);

  @Inject GoogleConfig config;

  @Transactional
  public GoogleStatusDto forUser(AppUser user) {
    if (!config.isEnabled()) {
      return GoogleStatusDto.unavailable(config.disabledReason());
    }
    GoogleAccount account = GoogleAccount.findByUser(user.id);
    if (account == null) {
      return new GoogleStatusDto(true, "", false, null, null, List.of());
    }
    return new GoogleStatusDto(
        true,
        "",
        true,
        account.email,
        account.connectedAt,
        List.of(
            resourceStatus(account, Resource.CONTACTS),
            resourceStatus(account, Resource.CALENDAR),
            resourceStatus(account, Resource.TASKS)));
  }

  private GoogleResourceStatusDto resourceStatus(GoogleAccount account, Resource resource) {
    GoogleSyncState state =
        GoogleSyncState.findById(new GoogleSyncState.Key(account.userId, resource));
    boolean permitted = account.hasScopes(Set.of(SCOPES.get(resource)));
    return new GoogleResourceStatusDto(
        resource.name(),
        permitted,
        state == null ? null : state.lastOkAt,
        state == null ? null : state.lastRunAt,
        state == null ? null : state.lastError,
        state == null ? 0 : state.failures);
  }
}
