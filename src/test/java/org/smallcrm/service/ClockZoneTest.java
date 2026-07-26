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

package org.smallcrm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.smallcrm.domain.Clocks;

/**
 * The zone a due date is judged in.
 *
 * <p>Without this, "overdue" and "due today" follow whatever zone the machine happens to be set
 * to. On a rented UTC server that is wrong for the first hour of every local day: a task the user
 * has to do today is reported as due tomorrow.
 */
@QuarkusTest
@TestProfile(ClockZoneTest.FarEastProfile.class)
class ClockZoneTest {

  /** UTC+14, far enough from anything a build machine is set to that a default would show. */
  public static final String ZONE = "Pacific/Kiritimati";

  public static class FarEastProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("smallcrm.time-zone", ZONE);
    }
  }

  @Inject Clock clock;

  @Test
  void the_configured_zone_is_the_one_dates_are_judged_in() {
    assertThat(clock.getZone()).isEqualTo(ZoneId.of(ZONE));
  }

  @Test
  void the_entity_lifecycle_callbacks_use_the_same_clock() {
    // Clocks is a static holder because @PrePersist has no injection point of its own; if the
    // wiring at startup ever stopped happening, timestamps would quietly follow the wall clock.
    assertThat(Clocks.now()).isCloseTo(clock.instant(), within(2, ChronoUnit.SECONDS));
  }
}
