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

package org.smallcrm.api;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.smallcrm.api.error.ApiError;

/**
 * Turns a bad enum query parameter into a helpful 400 instead of a bare 404.
 *
 * <p>When a parameter conversion throws, the JAX-RS runtime answers 404, so
 * {@code GET /api/deals?stage=OPEN} — a typo — told the client the route did not exist. The
 * same trap was already worked around for {@code Instant}; this covers every enum the API
 * exposes, and names the values that would have worked.
 */
@Provider
public class EnumParamConverterProvider implements ParamConverterProvider {

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public <T> ParamConverter<T> getConverter(
      Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (!rawType.isEnum()) {
      return null;
    }
    return (ParamConverter<T>) new EnumParamConverter(rawType);
  }

  /** Parses one enum constant, case-insensitively. */
  static final class EnumParamConverter<T extends Enum<T>> implements ParamConverter<T> {

    private final Class<T> type;

    EnumParamConverter(Class<T> type) {
      this.type = type;
    }

    @Override
    public T fromString(String value) {
      if (value == null || value.isBlank()) {
        return null;
      }
      try {
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        // A WebApplicationException passes through untouched; anything else becomes a 404.
        throw new WebApplicationException(
            Response.status(Response.Status.BAD_REQUEST)
                .entity(
                    ApiError.of(
                        "INVALID_VALUE",
                        "'" + value + "' is not one of: " + allowed()))
                .build());
      }
    }

    @Override
    public String toString(T value) {
      return value == null ? null : value.name();
    }

    private String allowed() {
      return Arrays.stream(type.getEnumConstants())
          .map(Enum::name)
          .collect(Collectors.joining(", "));
    }
  }
}
