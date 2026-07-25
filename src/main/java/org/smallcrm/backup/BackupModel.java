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

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.smallcrm.domain.DealStage;
import org.smallcrm.domain.InteractionType;
import org.smallcrm.domain.TaskPriority;

/**
 * The shape of a backup file.
 *
 * <p>Two deliberate choices run through the whole model. Accounts are not part of a backup, so
 * the user who created a record is referenced by name (`owner`) and re-linked on restore only if
 * an account with that name exists. Relations between records use the identifiers the exporting
 * installation happened to hand out; the restore treats them purely as links inside the file and
 * lets the database allocate fresh ones.
 */
public final class BackupModel {

  /** Format version of the file, so a future change can be detected rather than misread. */
  public static final int FORMAT_VERSION = 1;

  private BackupModel() {
  }

  /** Root element of a backup file. */
  @JacksonXmlRootElement(localName = "smallCrmBackup")
  public record Backup(
      @JacksonXmlProperty(isAttribute = true) int formatVersion,
      @JacksonXmlProperty(isAttribute = true) Instant createdAt,
      @JacksonXmlProperty(isAttribute = true) String createdBy,
      @JacksonXmlElementWrapper(useWrapping = false)
          @JacksonXmlProperty(localName = "company")
          List<BackupCompany> companies,
      @JacksonXmlElementWrapper(useWrapping = false)
          @JacksonXmlProperty(localName = "contact")
          List<BackupContact> contacts,
      @JacksonXmlElementWrapper(useWrapping = false)
          @JacksonXmlProperty(localName = "deal")
          List<BackupDeal> deals,
      @JacksonXmlElementWrapper(useWrapping = false)
          @JacksonXmlProperty(localName = "interaction")
          List<BackupInteraction> interactions,
      @JacksonXmlElementWrapper(useWrapping = false)
          @JacksonXmlProperty(localName = "task")
          List<BackupTask> tasks,
      @JacksonXmlElementWrapper(useWrapping = false)
          @JacksonXmlProperty(localName = "appointment")
          List<BackupAppointment> appointments) {

    /** Total number of records, used for the summary the interface shows after a restore. */
    public int recordCount() {
      return size(companies)
          + size(contacts)
          + size(deals)
          + size(interactions)
          + size(tasks)
          + size(appointments);
    }

    private static int size(List<?> list) {
      return list == null ? 0 : list.size();
    }
  }

  public record BackupCompany(
      Long id,
      String name,
      String vatId,
      String website,
      String email,
      String phone,
      String street,
      String postalCode,
      String city,
      String country,
      String notes,
      String owner,
      Instant createdAt,
      Instant updatedAt) {}

  public record BackupContact(
      Long id,
      String firstName,
      String lastName,
      String email,
      String phone,
      String mobile,
      String position,
      Long companyId,
      @JacksonXmlElementWrapper(useWrapping = false)
          @JacksonXmlProperty(localName = "tag")
          List<String> tags,
      String notes,
      String owner,
      Instant createdAt,
      Instant updatedAt) {}

  public record BackupDeal(
      Long id,
      String title,
      Long contactId,
      Long companyId,
      BigDecimal amount,
      String currency,
      DealStage stage,
      LocalDate expectedCloseDate,
      String notes,
      String owner,
      Instant createdAt,
      Instant updatedAt) {}

  public record BackupInteraction(
      Long id,
      InteractionType type,
      Instant occurredAt,
      String subject,
      String notes,
      Long contactId,
      Long dealId,
      String owner,
      Instant createdAt,
      Instant updatedAt) {}

  public record BackupTask(
      Long id,
      String title,
      String description,
      LocalDate dueDate,
      boolean done,
      Instant completedAt,
      TaskPriority priority,
      Long contactId,
      Long dealId,
      String owner,
      Instant createdAt,
      Instant updatedAt) {}

  public record BackupAppointment(
      Long id,
      String title,
      Instant startsAt,
      Instant endsAt,
      String timeZone,
      String location,
      String notes,
      Long contactId,
      Long dealId,
      String externalCalendarId,
      String externalEventId,
      String externalEtag,
      Instant lastSyncedAt,
      String owner,
      Instant createdAt,
      Instant updatedAt) {}
}
