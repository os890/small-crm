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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.os890.smallcrm.domain.Appointment;

/** Wire representation of an {@link Appointment}. */
public record AppointmentDto(
    Long id,
    @NotBlank @Size(max = 200) String title,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @Size(max = 60) String timeZone,
    @Size(max = 200) String location,
    @Size(max = 4000) String notes,
    Long contactId,
    String contactName,
    Long dealId,
    String dealTitle,
    String ownerName,
    Instant createdAt,
    Instant updatedAt,
    // Optimistic locking token; send back what the GET returned.
    Long version) {

  public static AppointmentDto from(Appointment appointment) {
    return new AppointmentDto(
        appointment.id,
        appointment.title,
        appointment.startsAt,
        appointment.endsAt,
        appointment.timeZone,
        appointment.location,
        appointment.notes,
        appointment.contact == null ? null : appointment.contact.id,
        appointment.contact == null ? null : appointment.contact.displayName(),
        appointment.deal == null ? null : appointment.deal.id,
        appointment.deal == null ? null : appointment.deal.title,
        appointment.owner == null ? null : appointment.owner.username,
        appointment.createdAt,
        appointment.updatedAt,
        appointment.version);
  }
}
