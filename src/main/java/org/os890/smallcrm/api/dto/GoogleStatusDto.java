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

package org.os890.smallcrm.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * What the interface needs to know about one user's Google connection.
 *
 * <p>Carries no token and no scope secret — only whether a thing is on, who it belongs to, and
 * how the last sync went.
 *
 * @param available whether the installation is configured for Google at all
 * @param unavailableReason why not, for whoever configures the installation; empty when it is
 *     available
 * @param connected whether this user has connected an account
 * @param resources one entry per sync, so a user can see that contacts work and the calendar
 *     does not
 */
public record GoogleStatusDto(
    boolean available,
    String unavailableReason,
    boolean connected,
    String email,
    Instant connectedAt,
    List<GoogleResourceStatusDto> resources) {

  /** The answer for the login screen, which may ask before anybody is signed in. */
  public static GoogleStatusDto availability(boolean available) {
    return new GoogleStatusDto(available, "", false, null, null, List.of());
  }

  /** The answer when the feature is switched off on this installation. */
  public static GoogleStatusDto unavailable(String reason) {
    return new GoogleStatusDto(false, reason, false, null, null, List.of());
  }

  /**
   * How one of the three syncs is getting on.
   *
   * @param resource CONTACTS, CALENDAR or TASKS
   * @param permitted whether the user granted the scope this one needs; consent is not
   *     all-or-nothing, so a connected account may still not be allowed to read the calendar
   */
  public record GoogleResourceStatusDto(
      String resource,
      boolean permitted,
      Instant lastOkAt,
      Instant lastRunAt,
      String lastError,
      int failures) {}
}
