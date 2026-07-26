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

import java.time.Clock;
import java.time.Instant;

/**
 * The clock the entity lifecycle callbacks read.
 *
 * <p>Services take a {@link Clock} by injection so a test can fix time, but a JPA lifecycle
 * callback has no injection point and used {@code Instant.now()} directly — so a test with a
 * fixed clock still got wall-clock timestamps on the records it created, and nothing about the
 * inconsistency was visible. This gives the callbacks the same clock, set once at startup.
 */
public final class Clocks {

  private static volatile Clock clock = Clock.systemDefaultZone();

  private Clocks() {
  }

  /** The current instant, according to the configured clock. */
  public static Instant now() {
    return Instant.now(clock);
  }

  /** Installs the application's clock. Called once during startup. */
  public static void use(Clock replacement) {
    clock = replacement;
  }
}
