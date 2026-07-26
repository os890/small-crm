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

package org.os890.smallcrm.google.sync;

/**
 * Carries the sync token a pass ended on back to whoever stores it.
 *
 * <p>A holder rather than a return value because a pass reports counts as well, and threading
 * two results out of every method is worse than handing one in.
 */
public final class SyncCursor {

  private String token;

  public void token(String token) {
    this.token = token;
  }

  /** The token to present next time, or null when the next pass must be a full one. */
  public String token() {
    return token;
  }
}
