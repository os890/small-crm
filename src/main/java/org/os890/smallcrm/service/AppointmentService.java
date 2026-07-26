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

package org.os890.smallcrm.service;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.os890.smallcrm.api.dto.AppointmentDto;
import org.os890.smallcrm.api.error.AppointmentConflictException;
import org.os890.smallcrm.api.error.BusinessRuleException;
import org.os890.smallcrm.api.error.NotFoundException;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.domain.Appointment;
import org.os890.smallcrm.security.CurrentUser;

/**
 * Reads and writes appointments and keeps a single person's calendar free of double bookings.
 *
 * <p>Two appointments collide when their timespans genuinely intersect. Touching boundaries are
 * fine: a slot ending at 10:00 and one starting at 10:00 can both be kept.
 */
@ApplicationScoped
public class AppointmentService {

  /** Widest window the agenda will return in one request, guarding against runaway queries. */
  private static final Duration MAX_RANGE = Duration.ofDays(400);

  @Inject CurrentUser currentUser;
  @Inject ReferenceResolver references;
  @Inject Clock clock;

  /**
   * Appointments that intersect the given window, earliest first.
   *
   * @param from inclusive lower bound; defaults to the start of the current day
   * @param to exclusive upper bound; defaults to 30 days after {@code from}
   */
  public List<AppointmentDto> list(Instant from, Instant to) {
    Instant start = from != null ? from : startOfToday();
    Instant end = to != null ? to : start.plus(Duration.ofDays(30));
    if (!end.isAfter(start)) {
      throw new BusinessRuleException("INVALID_RANGE", "'to' must be after 'from'");
    }
    if (Duration.between(start, end).compareTo(MAX_RANGE) > 0) {
      throw new BusinessRuleException(
          "RANGE_TOO_WIDE", "At most " + MAX_RANGE.toDays() + " days can be requested at once");
    }
    List<Appointment> appointments =
        Appointment.list(
            "startsAt < ?1 and endsAt > ?2", Sort.by("startsAt").and("id"), end, start);
    return appointments.stream().map(AppointmentDto::from).toList();
  }

  /** Local midnight of the current day, the agenda's default lower bound. */
  private Instant startOfToday() {
    return LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
  }

  /** Appointments starting from now, earliest first, used by the dashboard. */
  public List<AppointmentDto> upcoming(int days) {
    Instant now = Instant.now(clock);
    List<Appointment> appointments =
        Appointment.list(
            "endsAt > ?1 and startsAt < ?2",
            Sort.by("startsAt").and("id"),
            now,
            now.plus(Duration.ofDays(days)));
    return appointments.stream().map(AppointmentDto::from).toList();
  }

  public AppointmentDto get(Long id) {
    return AppointmentDto.from(require(id));
  }

  /**
   * Appointments of the current user that would collide with the given slot.
   *
   * @param excludeId appointment being edited, so it does not conflict with itself
   */
  public List<AppointmentDto> conflicts(Long excludeId, Instant startsAt, Instant endsAt) {
    validateSlot(startsAt, endsAt);
    return overlapping(currentUser.find().orElse(null), startsAt, endsAt, excludeId).stream()
        .map(AppointmentDto::from)
        .toList();
  }

  /**
   * Stores a new appointment.
   *
   * @param allowConflict when true the overlap check is reported but not enforced, so the user can
   *     deliberately keep two parallel entries
   * @throws AppointmentConflictException if the slot is taken and {@code allowConflict} is false
   */
  @Transactional
  public AppointmentDto create(AppointmentDto input, boolean allowConflict) {
    Appointment appointment = new Appointment();
    AppUser owner = currentUser.find().orElse(null);
    apply(input, appointment);
    appointment.owner = owner;
    lockCalendarOf(owner);
    guardSlot(owner, appointment.startsAt, appointment.endsAt, null, allowConflict);
    appointment.persist();
    return AppointmentDto.from(appointment);
  }

  /**
   * Updates an existing appointment.
   *
   * @param allowConflict see {@link #create(AppointmentDto, boolean)}
   */
  @Transactional
  public AppointmentDto update(Long id, AppointmentDto input, boolean allowConflict) {
    Appointment appointment = require(id);
    Versions.check(input.version(), appointment);
    apply(input, appointment);
    lockCalendarOf(appointment.owner);
    guardSlot(appointment.owner, appointment.startsAt, appointment.endsAt, id, allowConflict);
    return AppointmentDto.from(appointment);
  }

  @Transactional
  public void delete(Long id) {
    require(id).delete();
  }

  /**
   * Serialises everybody writing to one person's calendar.
   *
   * <p>Checking for an overlap and then inserting are two statements. Under the database's
   * default isolation two simultaneous requests for the same slot both see an empty calendar,
   * both insert, and both are told the slot was free — the one case the whole feature exists
   * for. Taking a write lock on the owner's account row first makes the pair atomic with
   * respect to that calendar, while leaving other users' calendars fully concurrent.
   *
   * <p>An appointment with no owner cannot be locked against; that only happens for records
   * whose owner was deleted, which no longer take part in conflict checks anyway.
   */
  private void lockCalendarOf(AppUser owner) {
    if (owner != null) {
      AppUser.getEntityManager().lock(owner, LockModeType.PESSIMISTIC_WRITE);
    }
  }

  private void guardSlot(
      AppUser owner, Instant startsAt, Instant endsAt, Long excludeId, boolean allowConflict) {
    if (allowConflict) {
      return;
    }
    List<Appointment> collisions = overlapping(owner, startsAt, endsAt, excludeId);
    if (!collisions.isEmpty()) {
      throw new AppointmentConflictException(
          collisions.stream().map(AppointmentDto::from).toList());
    }
  }

  private List<Appointment> overlapping(
      AppUser owner, Instant startsAt, Instant endsAt, Long excludeId) {
    StringBuilder query = new StringBuilder("startsAt < :endsAt and endsAt > :startsAt");
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("startsAt", startsAt);
    parameters.put("endsAt", endsAt);
    if (owner == null) {
      query.append(" and owner is null");
    } else {
      query.append(" and owner.id = :ownerId");
      parameters.put("ownerId", owner.id);
    }
    if (excludeId != null) {
      query.append(" and id <> :excludeId");
      parameters.put("excludeId", excludeId);
    }
    return Appointment.list(query.toString(), Sort.by("startsAt"), parameters);
  }

  private void apply(AppointmentDto input, Appointment appointment) {
    validateSlot(input.startsAt(), input.endsAt());
    appointment.title = input.title();
    appointment.startsAt = input.startsAt();
    appointment.endsAt = input.endsAt();
    appointment.timeZone = input.timeZone() == null ? "UTC" : input.timeZone();
    appointment.location = input.location();
    appointment.notes = input.notes();
    appointment.contact = references.contact(input.contactId());
    appointment.deal = references.deal(input.dealId());
  }

  private static void validateSlot(Instant startsAt, Instant endsAt) {
    if (startsAt == null || endsAt == null) {
      throw new BusinessRuleException(
          "SLOT_REQUIRED", "An appointment needs both a start and an end");
    }
    if (!endsAt.isAfter(startsAt)) {
      throw new BusinessRuleException("END_BEFORE_START", "The end must be after the start");
    }
  }

  private static Appointment require(Long id) {
    Appointment appointment = Appointment.findById(id);
    if (appointment == null) {
      throw new NotFoundException("Appointment", id);
    }
    return appointment;
  }
}
