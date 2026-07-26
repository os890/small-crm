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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * How far one user's sync of one resource has got.
 *
 * <p>Google hands back an opaque token saying "you have seen everything up to here"; presenting
 * it next time asks only for what changed since. The tokens expire, and Google's answer when one
 * does is a 410, whose only remedy is a full pass — so the last error is kept rather than
 * retried blindly, and repeated failures are counted so that "it has been broken since March" is
 * a question the interface can answer.
 */
@Entity
@Table(name = "google_sync_state")
@IdClass(GoogleSyncState.Key.class)
public class GoogleSyncState extends PanacheEntityBase {

  /** The three things that are kept in step, each with its own token and its own failures. */
  public enum Resource {
    CONTACTS,
    CALENDAR,
    TASKS
  }

  /** Composite key: one row per user per resource. */
  public static class Key implements Serializable {
    public Long userId;
    public Resource resource;

    public Key() {
      // Required by JPA.
    }

    public Key(Long userId, Resource resource) {
      this.userId = userId;
      this.resource = resource;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(userId, key.userId)
          && resource == key.resource;
    }

    @Override
    public int hashCode() {
      return Objects.hash(userId, resource);
    }
  }

  @Id
  @Column(name = "user_id")
  public Long userId;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Resource resource;

  /** Opaque to us; only Google gives it meaning. Null means the next pass is a full one. */
  @Column(length = 4000)
  public String syncToken;

  public Instant lastRunAt;

  /** Last time the pass finished without an error, which is what the interface should show. */
  public Instant lastOkAt;

  @Column(length = 1000)
  public String lastError;

  @Column(nullable = false)
  public int failures;

  @Version public long version;

  @Column(nullable = false)
  public Instant updatedAt;

  public static GoogleSyncState of(Long userId, Resource resource) {
    GoogleSyncState state = findById(new Key(userId, resource));
    if (state == null) {
      state = new GoogleSyncState();
      state.userId = userId;
      state.resource = resource;
    }
    return state;
  }

  public void succeeded(String nextToken, Instant now) {
    syncToken = nextToken;
    lastRunAt = now;
    lastOkAt = now;
    lastError = null;
    failures = 0;
  }

  /**
   * Records a failed pass.
   *
   * @param dropToken true when the token itself was rejected, so the next pass must be full
   */
  public void failed(String message, boolean dropToken, Instant now) {
    lastRunAt = now;
    lastError =
        message == null ? "unknown error" : message.substring(0, Math.min(1000, message.length()));
    failures++;
    if (dropToken) {
      syncToken = null;
    }
  }

  @PrePersist
  @PreUpdate
  void stamp() {
    updatedAt = Clocks.now();
  }
}
