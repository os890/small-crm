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
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import org.smallcrm.domain.CrmTask;
import org.smallcrm.domain.TaskPriority;

/** Wire representation of a {@link CrmTask}. */
public record CrmTaskDto(
    Long id,
    @NotBlank @Size(max = 200) String title,
    @Size(max = 4000) String description,
    LocalDate dueDate,
    boolean done,
    Instant completedAt,
    TaskPriority priority,
    Long contactId,
    String contactName,
    Long dealId,
    String dealTitle,
    boolean overdue,
    String ownerName,
    Instant createdAt,
    Instant updatedAt) {

  public static CrmTaskDto from(CrmTask task, LocalDate today) {
    return new CrmTaskDto(
        task.id,
        task.title,
        task.description,
        task.dueDate,
        task.done,
        task.completedAt,
        task.priority,
        task.contact == null ? null : task.contact.id,
        task.contact == null ? null : task.contact.displayName(),
        task.deal == null ? null : task.deal.id,
        task.deal == null ? null : task.deal.title,
        task.isOverdue(today),
        task.owner == null ? null : task.owner.username,
        task.createdAt,
        task.updatedAt);
  }
}
