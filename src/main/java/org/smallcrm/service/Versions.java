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

import org.smallcrm.api.error.ConflictException;
import org.smallcrm.domain.BaseEntity;

/**
 * Rejects an update built on a copy of a record that somebody else has since changed.
 *
 * <p>The {@code @Version} column alone does not catch this. Every update loads the entity and
 * writes it inside one short transaction, so Hibernate compares the version it read a
 * microsecond earlier — never the one the user's browser was actually looking at. The conflict
 * window that matters is between loading the edit form and pressing Save, which is minutes wide,
 * and only the client echoing its version back can close it.
 */
public final class Versions {

  private Versions() {
  }

  /**
   * Compares the version the client sent with the one in the database.
   *
   * <p>A {@code null} version is accepted so that a client which does not send one still works;
   * it simply gets the previous last-write-wins behaviour rather than an error.
   *
   * @throws ConflictException if the record moved on since the client read it
   */
  public static void check(Long submitted, BaseEntity current) {
    if (submitted != null && submitted != current.version) {
      throw new ConflictException(
          "STALE_VERSION",
          "This record was changed by somebody else while you were editing it");
    }
  }
}
