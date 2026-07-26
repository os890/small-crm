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
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A scheduled appointment in the user's calendar.
 *
 * <p>The {@code external*} fields are unused by the proof of concept but are already part of the
 * schema so that a later Google Calendar synchronisation can adopt existing records instead of
 * requiring a migration.
 */
@Entity
@Table(
    name = "appointment",
    indexes = {@Index(name = "idx_appointment_slot", columnList = "startsAt, endsAt")})
public class Appointment extends BaseEntity {

  @Column(nullable = false, length = 200)
  public String title;

  @Column(nullable = false)
  public Instant startsAt;

  @Column(nullable = false)
  public Instant endsAt;

  /** IANA zone the appointment was entered in, kept for correct display and future sync. */
  @Column(nullable = false, length = 60)
  public String timeZone = "UTC";

  @Column(length = 200)
  public String location;

  @Column(length = 4000)
  public String notes;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contact_id")
  public Contact contact;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "deal_id")
  public Deal deal;

  /** Identifier of the remote calendar this appointment belongs to, once sync exists. */
  @Column(length = 200)
  public String externalCalendarId;

  @Column(length = 200)
  public String externalEventId;

  @Column(length = 200)
  public String externalEtag;

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

  /**
   * Whether this appointment shares any part of its timespan with the given one.
   *
   * <p>Boundaries do not count as an overlap: an appointment ending at 10:00 does not collide with
   * one starting at 10:00.
   */
  public boolean overlaps(Instant otherStart, Instant otherEnd) {
    return startsAt.isBefore(otherEnd) && endsAt.isAfter(otherStart);
  }
}
