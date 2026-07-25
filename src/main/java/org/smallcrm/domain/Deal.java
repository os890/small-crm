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
import java.math.BigDecimal;
import java.time.LocalDate;

/** A piece of potential business tracked through the pipeline. */
@Entity
@Table(name = "deal")
public class Deal extends BaseEntity {

  @Column(nullable = false, length = 200)
  public String title;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contact_id")
  public Contact contact;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id")
  public Company company;

  /** Named "amount" because "value" is a reserved word in H2. */
  @Column(name = "amount", precision = 15, scale = 2)
  public BigDecimal amount;

  @Column(nullable = false, length = 3)
  public String currency = "EUR";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public DealStage stage = DealStage.LEAD;

  public LocalDate expectedCloseDate;

  @Column(length = 4000)
  public String notes;
}
