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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.smallcrm.domain.Deal;
import org.smallcrm.domain.DealStage;

/** Wire representation of a {@link Deal}. */
public record DealDto(
    Long id,
    @NotBlank @Size(max = 200) String title,
    Long contactId,
    String contactName,
    Long companyId,
    String companyName,
    @PositiveOrZero BigDecimal amount,
    @Size(min = 3, max = 3) String currency,
    DealStage stage,
    LocalDate expectedCloseDate,
    @Size(max = 4000) String notes,
    String ownerName,
    Instant createdAt,
    Instant updatedAt) {

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
        deal.updatedAt);
  }
}
