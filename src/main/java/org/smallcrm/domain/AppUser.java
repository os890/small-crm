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
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * An account that can sign in to Small CRM.
 *
 * <p>Authentication is handled by the application's own session mechanism rather than by
 * {@code quarkus-security-jpa}: the password is verified in the login endpoint, and a session
 * row is issued. That keeps the checks that must gate a login — deactivation, lock-out after
 * repeated failures — in one place instead of splitting them between the framework and a filter
 * that only covers JAX-RS paths.
 */
@Entity
@Table(name = "app_user")
public class AppUser extends PanacheEntityBase {

  /** Role granted to administrators; may manage other accounts. */
  public static final String ROLE_ADMIN = "ADMIN";

  /** Role granted to every account; may use all CRM features. */
  public static final String ROLE_USER = "USER";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true, length = 100)
  public String username;

  /** BCrypt hash in modular crypt format. Never exposed through the API. */
  @Column(nullable = false, length = 200)
  public String password;

  /** Comma separated role names; administrators hold {@code ADMIN,USER}. */
  @Column(nullable = false, length = 100)
  public String roles;

  @Column(length = 150)
  public String fullName;

  @Column(length = 200)
  public String email;

  /** Blocks every request except reading the profile and setting a new password. */
  @Column(nullable = false)
  public boolean mustChangePassword;

  /** Deactivated accounts keep their history but can no longer sign in. */
  @Column(nullable = false)
  public boolean active = true;

  /** Consecutive failed sign-in attempts; reset by a successful one. */
  @Column(nullable = false)
  public int failedLoginCount;

  /** While set and in the future, sign-in is refused even with the correct password. */
  public Instant lockedUntil;

  @Version
  public long version;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;

  public static AppUser findByUsername(String username) {
    return find("username", username).firstResult();
  }

  public boolean isAdmin() {
    return roles != null && roles.contains(ROLE_ADMIN);
  }

  /** Whether sign-in is currently blocked by the lock-out. */
  public boolean isLockedAt(Instant now) {
    return lockedUntil != null && now.isBefore(lockedUntil);
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
