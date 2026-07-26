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

package org.os890.smallcrm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A follow-up to do, optionally attached to a contact or a deal.
 *
 * <p>Named {@code CrmTask} rather than {@code Task} to avoid clashing with the many other
 * {@code Task} types in scope on both the Java and the SQL side.
 */
@Entity
@Table(name = "crm_task")
public class CrmTask extends BaseEntity {

  @Column(nullable = false, length = 200)
  public String title;

  @Column(length = 4000)
  public String description;

  public LocalDate dueDate;

  @Column(nullable = false)
  public boolean done;

  public Instant completedAt;

  // Stored as plain text rather than a native H2 ENUM, so adding a constant later needs
  // no schema migration and the column stays portable.
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 10)
  public TaskPriority priority = TaskPriority.NORMAL;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contact_id")
  public Contact contact;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "deal_id")
  public Deal deal;

  /**
   * Identifier of the Google task this one mirrors, once an account is connected.
   *
   * <p>Null on everything created here and never synced, which is the normal state for an
   * installation nobody has connected Google to.
   */
  @Column(length = 200)
  public String externalId;

  /** Which Google task list it came from, so it is written back to the same one. */
  @Column(length = 200)
  public String externalListId;

  /** Google's version marker, sent back on a write so a concurrent change is refused. */
  @Column(length = 200)
  public String externalEtag;

  /** When this task and its Google counterpart were last known to agree. */
  public Instant lastSyncedAt;

  /**
   * Whether this record came from Google carrying something the CRM cannot represent.
   *
   * <p>Shown, never written back. Editing it here would flatten a recurring series or drop
   * addresses in the user's own Google account, so the API refuses the change and the interface
   * says the record is managed in Google.
   */
  @Column(nullable = false)
  public boolean externalReadOnly;

  /** A task is overdue when it is still open and its due date has already passed. */
  public boolean isOverdue(LocalDate today) {
    return !done && dueDate != null && dueDate.isBefore(today);
  }
}
