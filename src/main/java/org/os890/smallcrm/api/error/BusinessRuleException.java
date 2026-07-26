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

import java.util.Map;

/** Raised when input is syntactically valid but violates a domain rule; mapped to HTTP 400. */
public class BusinessRuleException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;
  private final transient Map<String, Object> details;

  public BusinessRuleException(String code, String message) {
    this(code, message, null);
  }

  public BusinessRuleException(String code, String message, Map<String, Object> details) {
    super(message);
    this.code = code;
    this.details = details;
  }

  public String code() {
    return code;
  }

  public Map<String, Object> details() {
    return details;
  }
}
