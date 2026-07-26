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

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The Google account one user has connected.
 *
 * <p>One row per user at most, and only ever for a user that already exists: connecting Google
 * links an account, it never creates one. An installation nobody has connected leaves this table
 * empty and behaves exactly as it did before.
 *
 * <p>The tokens are stored encrypted. A refresh token is not a password hash — it is a live
 * credential that will hand out access to somebody's whole Google account for as long as it is
 * valid, so it is the one thing in this database worth protecting beyond the file permissions.
 */
@Entity
@Table(name = "google_account")
public class GoogleAccount extends PanacheEntityBase {

  /** Shares its primary key with the user it belongs to. */
  @Id
  @Column(name = "user_id")
  public Long userId;

  @OneToOne(optional = false)
  @MapsId
  @JoinColumn(name = "user_id")
  public AppUser user;

  /**
   * Google's stable identifier for the person.
   *
   * <p>Matched on rather than the e-mail address, which people change and Google reassigns
   * within a workspace.
   */
  @Column(nullable = false, unique = true, length = 255)
  public String subject;

  /** Shown in the interface so the user can see which account is connected. */
  @Column(nullable = false, length = 320)
  public String email;

  /** Encrypted. Long-lived, and the only way to obtain a fresh access token. */
  @Column(nullable = false, length = 2000)
  public String refreshToken;

  /** Encrypted. Short-lived, cached only to avoid a refresh on every single call. */
  @Column(length = 4000)
  public String accessToken;

  public Instant accessExpires;

  /** What was actually granted, space separated, which need not be what was asked for. */
  @Column(nullable = false, length = 1000)
  public String scopes;

  @Column(nullable = false)
  public Instant connectedAt;

  @Version public long version;

  @Column(nullable = false)
  public Instant updatedAt;

  /** Whether the cached access token can still be used, with a minute of slack. */
  public boolean hasUsableAccessToken(Instant now) {
    return accessToken != null
        && accessExpires != null
        && now.plusSeconds(60).isBefore(accessExpires);
  }

  public Set<String> grantedScopes() {
    return new LinkedHashSet<>(Arrays.asList(scopes.trim().split("\\s+")));
  }

  /** Whether every scope a feature needs was granted; consent screens are not all-or-nothing. */
  public boolean hasScopes(Set<String> required) {
    return grantedScopes().containsAll(required);
  }

  public static GoogleAccount findByUser(Long userId) {
    return findById(userId);
  }

  public static GoogleAccount findBySubject(String subject) {
    return find("subject", subject).firstResult();
  }

  @PrePersist
  void onCreate() {
    Instant now = Clocks.now();
    if (connectedAt == null) {
      connectedAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Clocks.now();
  }
}
