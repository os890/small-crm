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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.api.error.BusinessRuleException;

/**
 * Reading who Google says somebody is, and refusing everything that does not say it clearly.
 *
 * <p>This is the value the whole sign-in hangs on, so every way it can be malformed has to end
 * in a refusal rather than in a half-populated account.
 */
class GoogleIdentityTokenTest {

  private static GoogleOAuthClient client() {
    GoogleOAuthClient client = new GoogleOAuthClient();
    client.json = new ObjectMapper();
    return client;
  }

  private static String token(String payloadJson) {
    String encode =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    return "header." + encode + ".signature";
  }

  @Test
  void a_well_formed_token_yields_the_subject_and_the_address() {
    var identity =
        client()
            .readIdentity(
                token("{\"sub\":\"123\",\"email\":\"maria@example.org\",\"email_verified\":true}"));

    assertThat(identity.subject()).isEqualTo("123");
    assertThat(identity.email()).isEqualTo("maria@example.org");
    assertThat(identity.emailVerified()).isTrue();
  }

  @Test
  void an_unverified_address_is_reported_as_such_rather_than_assumed() {
    var identity =
        client()
            .readIdentity(token("{\"sub\":\"123\",\"email\":\"m@example.org\"}"));

    // Absent means not verified. Defaulting the other way would accept an address nobody proved.
    assertThat(identity.emailVerified()).isFalse();
  }

  @Test
  void a_missing_token_is_refused() {
    assertThatThrownBy(() -> client().readIdentity(null))
        .isInstanceOf(BusinessRuleException.class);
    assertThatThrownBy(() -> client().readIdentity("  "))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void something_that_is_not_a_jwt_is_refused() {
    assertThatThrownBy(() -> client().readIdentity("not-a-token"))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("not readable");
  }

  @Test
  void a_payload_that_is_not_json_is_refused() {
    assertThatThrownBy(() -> client().readIdentity(token("this is not json")))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void a_token_naming_no_account_is_refused() {
    assertThatThrownBy(() -> client().readIdentity(token("{\"email\":\"m@example.org\"}")))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("named no account");
    assertThatThrownBy(() -> client().readIdentity(token("{\"sub\":\"123\"}")))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void a_token_response_without_a_lifetime_is_treated_as_short_lived() {
    // Assuming an hour when Google did not say so would keep a dead token in use.
    GoogleTokens silent = new GoogleTokens("a", null, null, null, null, "Bearer");
    assertThat(silent.lifetimeSeconds()).isLessThanOrEqualTo(300);
    assertThat(silent.scopeOrEmpty()).isEmpty();

    GoogleTokens told = new GoogleTokens("a", "r", 3599L, null, "openid", "Bearer");
    assertThat(told.lifetimeSeconds()).isEqualTo(3599);
    assertThat(told.scopeOrEmpty()).isEqualTo("openid");
  }
}
