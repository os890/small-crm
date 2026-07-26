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

package org.os890.smallcrm.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.os890.smallcrm.domain.Deal;
import org.os890.smallcrm.domain.DealStage;

/** Wire representation of a {@link Deal}. */
public record DealDto(
    Long id,
    @NotBlank @Size(max = 200) String title,
    Long contactId,
    String contactName,
    Long companyId,
    String companyName,
    // Matches NUMERIC(15,2); without this an 18 digit amount passes validation and
    // fails at flush as a 500.
    @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal amount,
    @Size(min = 3, max = 3) @Pattern(regexp = "[A-Za-z]{3}") String currency,
    DealStage stage,
    LocalDate expectedCloseDate,
    @Size(max = 4000) String notes,
    String ownerName,
    Instant createdAt,
    Instant updatedAt,
    // Optimistic locking token; send back what the GET returned.
    Long version) {

  public static DealDto from(Deal deal) {
    return new DealDto(
        deal.id,
        deal.title,
        deal.contact == null ? null : deal.contact.id,
        deal.contact == null ? null : deal.contact.displayName(),
        deal.company == null ? null : deal.company.id,
        deal.company == null ? null : deal.company.name,
        deal.amount,
        deal.currency,
        deal.stage,
        deal.expectedCloseDate,
        deal.notes,
        deal.owner == null ? null : deal.owner.username,
        deal.createdAt,
        deal.updatedAt,
        deal.version);
  }
}
