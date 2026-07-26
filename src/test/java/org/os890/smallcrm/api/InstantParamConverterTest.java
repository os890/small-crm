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

package org.os890.smallcrm.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.ParamConverter;
import java.lang.annotation.Annotation;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.api.error.ApiError;

/** Unit tests for the ISO-8601 query parameter conversion. */
class InstantParamConverterTest {

  private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];

  private final InstantParamConverterProvider provider = new InstantParamConverterProvider();

  @Test
  void only_instant_parameters_get_a_converter() {
    assertThat(provider.getConverter(Instant.class, Instant.class, NO_ANNOTATIONS)).isNotNull();
    assertThat(provider.getConverter(String.class, String.class, NO_ANNOTATIONS)).isNull();
  }

  @Test
  void iso_text_round_trips() {
    ParamConverter<Instant> converter = converter();
    Instant parsed = converter.fromString("2026-07-25T09:30:00Z");

    assertThat(parsed).isEqualTo(Instant.parse("2026-07-25T09:30:00Z"));
    assertThat(converter.toString(parsed)).isEqualTo("2026-07-25T09:30:00Z");
  }

  @Test
  void blank_and_null_become_null() {
    ParamConverter<Instant> converter = converter();

    assertThat(converter.fromString(null)).isNull();
    assertThat(converter.fromString("  ")).isNull();
    assertThat(converter.toString(null)).isNull();
  }

  @Test
  void malformed_text_is_reported_as_a_client_error() {
    assertThatThrownBy(() -> converter().fromString("yesterday"))
        .isInstanceOfSatisfying(
            WebApplicationException.class,
            exception -> {
              assertThat(exception.getResponse().getStatus()).isEqualTo(400);
              ApiError error = (ApiError) exception.getResponse().getEntity();
              assertThat(error.code()).isEqualTo("INVALID_TIMESTAMP");
              assertThat(error.message()).contains("ISO-8601");
            });
  }

  private ParamConverter<Instant> converter() {
    return provider.getConverter(Instant.class, Instant.class, NO_ANNOTATIONS);
  }
}
