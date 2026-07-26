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
import jakarta.persistence.Table;

/** An organisation a contact works for and deals can be attached to. */
@Entity
@Table(name = "company")
public class Company extends BaseEntity {

  @Column(nullable = false, length = 200)
  public String name;

  @Column(length = 50)
  public String vatId;

  @Column(length = 200)
  public String website;

  @Column(length = 200)
  public String email;

  @Column(length = 50)
  public String phone;

  @Column(length = 200)
  public String street;

  @Column(length = 20)
  public String postalCode;

  @Column(length = 100)
  public String city;

  @Column(length = 100)
  public String country;

  @Column(length = 4000)
  public String notes;
}
