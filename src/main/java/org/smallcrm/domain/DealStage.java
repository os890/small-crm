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

/** The stages a deal moves through, in pipeline order. */
public enum DealStage {
  LEAD(false),
  QUALIFIED(false),
  PROPOSAL(false),
  WON(true),
  LOST(true);

  private final boolean closed;

  DealStage(boolean closed) {
    this.closed = closed;
  }

  /** Whether no further pipeline work is expected for a deal in this stage. */
  public boolean isClosed() {
    return closed;
  }

  /**
   * Position in the pipeline, for sorting.
   *
   * <p>The column stores the constant's name, so ordering by it in SQL is alphabetical and puts
   * LOST between LEAD and PROPOSAL. Queries order by this instead.
   */
  public int order() {
    return ordinal();
  }
}
