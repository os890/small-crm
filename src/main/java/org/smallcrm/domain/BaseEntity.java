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

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Common state shared by every business record: identity, optimistic locking, audit timestamps
 * and the user who created the record.
 *
 * <p>All users of a Small CRM installation share one workspace, so {@code owner} is informational
 * rather than a permission boundary.
 */
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version
  public long version;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  public AppUser owner;

  /**
   * Stamps a new record, unless the caller already knows when it was created.
   *
   * <p>The guard matters for restores: a backup carries the original timestamps, and
   * overwriting them would turn "created three years ago" into "created just now" for every
   * record in the file — silently, and permanently once the next backup is written.
   */
  @PrePersist
  void onCreate() {
    Instant now = Clocks.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Clocks.now();
  }
}
