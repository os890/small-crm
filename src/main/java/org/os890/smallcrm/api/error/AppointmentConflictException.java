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

package org.os890.smallcrm.api.error;

import java.util.List;
import org.os890.smallcrm.api.dto.AppointmentDto;

/** Raised when a new or moved appointment would double book an already occupied slot. */
public class AppointmentConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<AppointmentDto> conflicts;

  public AppointmentConflictException(List<AppointmentDto> conflicts) {
    super("The requested time slot overlaps " + conflicts.size() + " existing appointment(s)");
    this.conflicts = List.copyOf(conflicts);
  }

  public List<AppointmentDto> conflicts() {
    return conflicts;
  }
}
