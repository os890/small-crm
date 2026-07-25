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

package org.smallcrm.domain;

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

  /** A task is overdue when it is still open and its due date has already passed. */
  public boolean isOverdue(LocalDate today) {
    return !done && dueDate != null && dueDate.isBefore(today);
  }
}
