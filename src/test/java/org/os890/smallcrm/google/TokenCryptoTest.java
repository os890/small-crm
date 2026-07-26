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

/** Encrypting the Google tokens: a round trip, and every way it is meant to refuse. */
class TokenCryptoTest {

  private static final String KEY = "a-generated-token-key-of-ample-length";
  private static final String TOKEN = "1//0gAbCdEfGhIjKlMnOpQrStUvWxYz-a-refresh-token";

  private static TokenCrypto with(String key) {
    TokenCrypto crypto = new TokenCrypto();
    crypto.configuredKey = Optional.ofNullable(key);
    return crypto;
  }

  @Test
  void a_token_survives_a_round_trip() {
    TokenCrypto crypto = with(KEY);

    assertThat(crypto.decrypt(crypto.encrypt(TOKEN))).isEqualTo(TOKEN);
  }

  @Test
  void the_same_token_encrypts_differently_every_time() {
    TokenCrypto crypto = with(KEY);

    String first = crypto.encrypt(TOKEN);
    String second = crypto.encrypt(TOKEN);

    // A fresh nonce per value. Identical ciphertexts would tell anyone reading the table which
    // two users had connected the same account, without decrypting anything.
    assertThat(first).isNotEqualTo(second);
    assertThat(crypto.decrypt(first)).isEqualTo(crypto.decrypt(second)).isEqualTo(TOKEN);
  }

  @Test
  void the_stored_form_does_not_contain_the_token() {
    String stored = with(KEY).encrypt(TOKEN);

    assertThat(stored).doesNotContain(TOKEN).doesNotContain("refresh-token");
  }

  @Test
  void nothing_is_stored_and_nothing_is_read_without_a_key() {
    assertThat(with(null).isConfigured()).isFalse();
    assertThat(with("   ").isConfigured()).isFalse();

    assertThatThrownBy(() -> with(null).encrypt(TOKEN))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("SMALLCRM_TOKEN_KEY");
  }

  @Test
  void a_key_short_enough_to_have_been_typed_is_refused() {
    // Not a judgement about entropy, just a floor that a hurried passphrase does not clear.
    String tooShort = "x".repeat(TokenCrypto.MIN_KEY_LENGTH - 1);

    assertThat(with(tooShort).isConfigured()).isFalse();
    assertThatThrownBy(() -> with(tooShort).encrypt(TOKEN))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void a_token_written_under_another_key_is_refused_rather_than_returned_as_rubbish() {
    String stored = with(KEY).encrypt(TOKEN);

    assertThatThrownBy(() -> with("a-different-generated-key-of-ample-length").decrypt(stored))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("Connect the Google account again");
  }

  @Test
  void a_tampered_value_is_refused() {
    TokenCrypto crypto = with(KEY);
    String stored = crypto.encrypt(TOKEN);
    // Flip a character in the middle of the ciphertext; GCM authenticates, so this must fail
    // rather than decrypt to something almost right.
    int middle = stored.length() / 2;
    char replacement = stored.charAt(middle) == 'A' ? 'B' : 'A';
    String tampered = stored.substring(0, middle) + replacement + stored.substring(middle + 1);

    assertThatThrownBy(() -> crypto.decrypt(tampered)).isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void a_short_or_unreadable_value_is_refused_rather_than_crashing() {
    TokenCrypto crypto = with(KEY);

    assertThatThrownBy(() -> crypto.decrypt("not base64 at all !!"))
        .isInstanceOf(BusinessRuleException.class);
    assertThatThrownBy(() -> crypto.decrypt("c2hvcnQ="))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void null_passes_through_so_an_absent_token_is_not_an_error() {
    TokenCrypto crypto = with(KEY);

    assertThat(crypto.encrypt(null)).isNull();
    assertThat(crypto.decrypt(null)).isNull();
  }
}
