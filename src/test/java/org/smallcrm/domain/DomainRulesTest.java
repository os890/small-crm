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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Plain unit tests for the rules that live on the entities themselves. */
class DomainRulesTest {

  private static final Instant TEN = Instant.parse("2026-07-25T10:00:00Z");
  private static final Instant ELEVEN = Instant.parse("2026-07-25T11:00:00Z");

  @ParameterizedTest(name = "{0} .. {1} overlaps 10:00..11:00 -> {2}")
  @CsvSource({
    // identical slot
    "2026-07-25T10:00:00Z, 2026-07-25T11:00:00Z, true",
    // starts inside
    "2026-07-25T10:30:00Z, 2026-07-25T11:30:00Z, true",
    // ends inside
    "2026-07-25T09:30:00Z, 2026-07-25T10:30:00Z, true",
    // fully contains
    "2026-07-25T09:00:00Z, 2026-07-25T12:00:00Z, true",
    // fully contained
    "2026-07-25T10:15:00Z, 2026-07-25T10:45:00Z, true",
    // touches the end boundary
    "2026-07-25T11:00:00Z, 2026-07-25T12:00:00Z, false",
    // touches the start boundary
    "2026-07-25T09:00:00Z, 2026-07-25T10:00:00Z, false",
    // clearly before
    "2026-07-25T08:00:00Z, 2026-07-25T09:00:00Z, false",
    // clearly after
    "2026-07-25T12:00:00Z, 2026-07-25T13:00:00Z, false"
  })
  void appointment_overlap_is_exclusive_at_the_boundaries(
      Instant otherStart, Instant otherEnd, boolean expected) {
    Appointment appointment = new Appointment();
    appointment.startsAt = TEN;
    appointment.endsAt = ELEVEN;

    assertThat(appointment.overlaps(otherStart, otherEnd)).isEqualTo(expected);
  }

  @Test
  void a_task_is_overdue_only_while_it_is_open_and_past_its_due_date() {
    LocalDate today = LocalDate.of(2026, 7, 25);
    CrmTask task = new CrmTask();
    task.dueDate = today.minusDays(1);

    assertThat(task.isOverdue(today)).isTrue();

    task.done = true;
    assertThat(task.isOverdue(today)).isFalse();

    task.done = false;
    task.dueDate = today;
    assertThat(task.isOverdue(today)).isFalse();

    task.dueDate = null;
    assertThat(task.isOverdue(today)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(DealStage.class)
  void only_won_and_lost_count_as_closed(DealStage stage) {
    assertThat(stage.isClosed())
        .isEqualTo(stage == DealStage.WON || stage == DealStage.LOST);
  }

  @Test
  void a_contact_displays_as_first_name_then_last_name() {
    Contact contact = new Contact();
    contact.firstName = "Maria";
    contact.lastName = "Huber";

    assertThat(contact.displayName()).isEqualTo("Maria Huber");
  }

  @Test
  void an_account_is_an_administrator_when_it_carries_the_admin_role() {
    AppUser user = new AppUser();
    assertThat(user.isAdmin()).isFalse();

    user.roles = AppUser.ROLE_USER;
    assertThat(user.isAdmin()).isFalse();

    user.roles = AppUser.ROLE_ADMIN + "," + AppUser.ROLE_USER;
    assertThat(user.isAdmin()).isTrue();
  }
}
