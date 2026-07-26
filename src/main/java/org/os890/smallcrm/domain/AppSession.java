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
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One signed-in session.
 *
 * <p>The browser holds a random token; only its SHA-256 is stored here, so reading this table
 * does not yield a usable session. Because the record is server side, signing out really ends a
 * session, and a password change can end every session of an account at once.
 */
@Entity
@Table(name = "app_session")
public class AppSession extends PanacheEntityBase {

  /** SHA-256 of the token the browser holds, hex encoded. */
  @Id
  @Column(name = "token_hash", length = 64)
  public String tokenHash;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  public AppUser user;

  @Column(nullable = false)
  public Instant createdAt;

  /** Moved forward as the session is used; drives the idle timeout. */
  @Column(nullable = false)
  public Instant lastSeenAt;

  /** Absolute end of life, fixed when the session is created. */
  @Column(nullable = false)
  public Instant expiresAt;

  /** Whether this session may still be used at the given moment. */
  public boolean isUsableAt(Instant now, java.time.Duration idleTimeout) {
    return now.isBefore(expiresAt) && now.isBefore(lastSeenAt.plus(idleTimeout));
  }
}
