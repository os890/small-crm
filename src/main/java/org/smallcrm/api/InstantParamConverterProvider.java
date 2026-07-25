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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.smallcrm.api.error.ApiError;

/**
 * Lets query parameters be declared as {@link Instant}, parsed from ISO-8601 (for example
 * {@code 2026-07-25T09:00:00Z}), which is exactly what the browser's {@code toISOString()}
 * produces.
 */
@Provider
public class InstantParamConverterProvider implements ParamConverterProvider {

  @Override
  @SuppressWarnings("unchecked")
  public <T> ParamConverter<T> getConverter(
      Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (!Instant.class.equals(rawType)) {
      return null;
    }
    return (ParamConverter<T>) new InstantParamConverter();
  }

  /** Converts between the ISO-8601 text form and {@link Instant}. */
  static final class InstantParamConverter implements ParamConverter<Instant> {

    @Override
    public Instant fromString(String value) {
      if (value == null || value.isBlank()) {
        return null;
      }
      try {
        return Instant.parse(value.trim());
      } catch (DateTimeParseException e) {
        // A WebApplicationException is passed through untouched, whereas any other exception
        // from a parameter converter is turned into a misleading 404 by the JAX-RS runtime.
        throw new WebApplicationException(
            Response.status(Response.Status.BAD_REQUEST)
                .entity(
                    ApiError.of(
                        "INVALID_TIMESTAMP", "'" + value + "' is not an ISO-8601 instant"))
                .build());
      }
    }

    @Override
    public String toString(Instant value) {
      return value == null ? null : value.toString();
    }
  }
}
