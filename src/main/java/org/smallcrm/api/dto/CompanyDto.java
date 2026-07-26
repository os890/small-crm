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
import org.smallcrm.domain.Company;

/** Wire representation of a {@link Company}. Server maintained fields are ignored on input. */
public record CompanyDto(
    Long id,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 50) String vatId,
    @Size(max = 200) String website,
    @Email @Size(max = 200) String email,
    @Size(max = 50) String phone,
    @Size(max = 200) String street,
    @Size(max = 20) String postalCode,
    @Size(max = 100) String city,
    @Size(max = 100) String country,
    @Size(max = 4000) String notes,
    String ownerName,
    Instant createdAt,
    Instant updatedAt,
    // Optimistic locking token; send back what the GET returned.
    Long version) {

  public static CompanyDto from(Company company) {
    return new CompanyDto(
        company.id,
        company.name,
        company.vatId,
        company.website,
        company.email,
        company.phone,
        company.street,
        company.postalCode,
        company.city,
        company.country,
        company.notes,
        company.owner == null ? null : company.owner.username,
        company.createdAt,
        company.updatedAt,
        company.version);
  }
}
