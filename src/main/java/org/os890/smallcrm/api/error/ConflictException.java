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

/**
 * Raised when a request cannot be applied because the world moved underneath it; mapped to
 * HTTP 409.
 *
 * <p>Used for an edit based on a copy somebody else has since changed, and for a uniqueness
 * clash that only two simultaneous requests could produce.
 */
public class ConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  public ConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
