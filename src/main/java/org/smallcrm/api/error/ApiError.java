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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * The single error shape every failing endpoint returns.
 *
 * @param code stable machine readable identifier the frontend maps to a translated message
 * @param message English fallback text, useful in logs and for developers
 * @param details optional structured extras, for example per-field validation messages
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, Object> details) {

  public static ApiError of(String code, String message) {
    return new ApiError(code, message, null);
  }
}
