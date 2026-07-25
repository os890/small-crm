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

package org.smallcrm.api.dto;

import java.time.Instant;
import java.util.List;
import org.smallcrm.domain.AppUser;

/** Wire representation of an account. The password hash is deliberately absent. */
public record UserDto(
    Long id,
    String username,
    String fullName,
    String email,
    List<String> roles,
    boolean admin,
    boolean active,
    boolean mustChangePassword,
    Instant createdAt) {

  public static UserDto from(AppUser user) {
    List<String> roles = user.roles == null ? List.of() : List.of(user.roles.split(","));
    return new UserDto(
        user.id,
        user.username,
        user.fullName,
        user.email,
        roles,
        user.isAdmin(),
        user.active,
        user.mustChangePassword,
        user.createdAt);
  }
}
