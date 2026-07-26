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

package org.os890.smallcrm.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.api.error.BusinessRuleException;

/**
 * When the integration counts as configured, and what it says when it does not.
 *
 * <p>Worth its own test because "off" is the normal state: most installations never connect
 * Google, and the difference between a feature that is off and one that is broken is a sentence
 * somebody has to read.
 */
class GoogleConfigTest {

  private static GoogleConfig with(String clientId, String secret, String redirect, String key) {
    GoogleConfig config = new GoogleConfig();
    config.clientId = Optional.ofNullable(clientId);
    config.clientSecret = Optional.ofNullable(secret);
    config.redirectUri = Optional.ofNullable(redirect);
    config.contactLabel = Optional.empty();
    config.crypto = new TokenCrypto();
    config.crypto.configuredKey = Optional.ofNullable(key);
    return config;
  }

  private static GoogleConfig complete() {
    return with(
        "id.apps.googleusercontent.com",
        "a-secret",
        "http://localhost:8080/api/google/callback",
        "a-generated-token-key-of-ample-length");
  }

  @Test
  void everything_set_means_the_feature_is_available() {
    GoogleConfig config = complete();

    assertThat(config.isEnabled()).isTrue();
    assertThat(config.disabledReason()).isEmpty();
  }

  @Test
  void a_missing_client_names_the_variables_that_are_missing() {
    GoogleConfig config = with(null, null, "http://localhost/cb", "a-key-of-ample-length-here-yes");

    assertThat(config.isEnabled()).isFalse();
    assertThat(config.disabledReason()).contains("SMALLCRM_GOOGLE_CLIENT_ID");
  }

  @Test
  void a_missing_redirect_is_named_too() {
    GoogleConfig config = with("id", "secret", null, "a-key-of-ample-length-here-indeed");

    assertThat(config.isEnabled()).isFalse();
    assertThat(config.disabledReason()).contains("SMALLCRM_GOOGLE_REDIRECT_URI");
  }

  @Test
  void a_client_without_a_token_key_is_off_rather_than_storing_credentials_in_clear() {
    GoogleConfig config = with("id", "secret", "http://localhost/cb", null);

    assertThat(config.isEnabled()).isFalse();
    assertThat(config.disabledReason()).contains("SMALLCRM_TOKEN_KEY").contains("safely");
  }

  @Test
  void blank_is_treated_as_absent_rather_than_as_a_value() {
    GoogleConfig config = with("  ", "secret", "http://localhost/cb", "a-key-of-ample-length-x");

    assertThat(config.isEnabled()).isFalse();
  }

  @Test
  void asking_for_a_setting_that_is_not_there_says_which_one() {
    GoogleConfig config = with(null, null, null, null);

    assertThatThrownBy(config::clientId)
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("SMALLCRM_GOOGLE_CLIENT_ID");
    assertThatThrownBy(config::clientSecret)
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("SMALLCRM_GOOGLE_CLIENT_SECRET");
    assertThatThrownBy(config::redirectUri)
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("SMALLCRM_GOOGLE_REDIRECT_URI");
  }

  @Test
  void the_contact_label_falls_back_to_a_sensible_default() {
    assertThat(complete().contactLabel()).isEqualTo("Small CRM");

    GoogleConfig named = complete();
    named.contactLabel = Optional.of("Kunden");
    assertThat(named.contactLabel()).isEqualTo("Kunden");

    GoogleConfig blank = complete();
    blank.contactLabel = Optional.of("   ");
    assertThat(blank.contactLabel()).isEqualTo("Small CRM");
  }

  @Test
  void the_scopes_asked_for_cover_all_three_resources() {
    assertThat(GoogleConfig.SYNC_SCOPES)
        .containsExactlyInAnyOrder(
            GoogleConfig.CONTACTS_SCOPE, GoogleConfig.CALENDAR_SCOPE, GoogleConfig.TASKS_SCOPE);
    assertThat(GoogleConfig.IDENTITY_SCOPES).contains("openid", "email");
  }
}
