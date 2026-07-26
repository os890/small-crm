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

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.smallcrm.domain.Contact;

/** Wire representation of a {@link Contact}. */
public record ContactDto(
    Long id,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @Email @Size(max = 200) String email,
    @Size(max = 50) String phone,
    @Size(max = 50) String mobile,
    @Size(max = 150) String position,
    Long companyId,
    String companyName,
    Set<String> tags,
    @Size(max = 4000) String notes,
    String displayName,
    String ownerName,
    Instant createdAt,
    Instant updatedAt,
    // Optimistic locking token; send back what the GET returned.
    Long version) {

  public static ContactDto from(Contact contact) {
    return new ContactDto(
        contact.id,
        contact.firstName,
        contact.lastName,
        contact.email,
        contact.phone,
        contact.mobile,
        contact.position,
        contact.company == null ? null : contact.company.id,
        contact.company == null ? null : contact.company.name,
        new LinkedHashSet<>(contact.tags),
        contact.notes,
        contact.displayName(),
        contact.owner == null ? null : contact.owner.username,
        contact.createdAt,
        contact.updatedAt,
        contact.version);
  }
}
