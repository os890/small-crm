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

package org.smallcrm.api.error;

/** Raised when a referenced record does not exist; mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String entity;

  public NotFoundException(String entity, Object id) {
    super(entity + " " + id + " does not exist");
    this.entity = entity;
  }

  public String entity() {
    return entity;
  }
}
