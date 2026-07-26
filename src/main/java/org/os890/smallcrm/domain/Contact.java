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

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;

/** A person the self-employed user does business with. */
@Entity
@Table(name = "contact")
public class Contact extends BaseEntity {

  @Column(nullable = false, length = 100)
  public String firstName;

  @Column(nullable = false, length = 100)
  public String lastName;

  @Column(length = 200)
  public String email;

  @Column(length = 50)
  public String phone;

  @Column(length = 50)
  public String mobile;

  @Column(length = 150)
  public String position;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id")
  public Company company;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "contact_tag", joinColumns = @JoinColumn(name = "contact_id"))
  @Column(name = "tag", length = 50)
  public Set<String> tags = new LinkedHashSet<>();

  @Column(length = 4000)
  public String notes;

  public String displayName() {
    return firstName + " " + lastName;
  }
}
