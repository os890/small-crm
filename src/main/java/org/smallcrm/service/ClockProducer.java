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

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.smallcrm.domain.Clocks;

/** Supplies the clock the services read "now" from, so tests can substitute a fixed one. */
@ApplicationScoped
public class ClockProducer {

  @Inject Clock clock;

  /**
   * The zone "today", "overdue" and "due today" are judged in.
   *
   * <p>Defaults to the machine's own zone, which is right when the application runs on the
   * owner's computer. It is configurable because that stops being true the moment it runs on a
   * rented server: a UTC host would tell a user in Vienna that a task due today is due tomorrow
   * for the first hour of every day.
   */
  @ConfigProperty(name = "smallcrm.time-zone")
  Optional<String> configuredZone;

  @Produces
  @ApplicationScoped
  public Clock systemClock() {
    return Clock.system(
        configuredZone.filter(zone -> !zone.isBlank()).map(ZoneId::of).orElseGet(
            ZoneId::systemDefault));
  }

  /**
   * Hands the same clock to the entity lifecycle callbacks, which have no injection point of
   * their own and would otherwise read the wall clock regardless of what the tests configured.
   */
  void onStart(@Observes StartupEvent event) {
    Clocks.use(clock);
  }
}
