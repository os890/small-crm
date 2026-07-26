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

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.os890.smallcrm.api.error.BusinessRuleException;

/**
 * Encrypts the Google tokens before they are stored.
 *
 * <p>Everything else in this database is either the user's own business data or a bcrypt hash,
 * which is useless to whoever steals the file. A Google refresh token is neither: it is a live
 * credential that will keep handing out access to somebody's entire Google account — their mail,
 * their calendar, their contacts — until it is revoked. Storing it in clear next to the CRM data
 * would make the database file far more valuable than the CRM itself.
 *
 * <p>AES-GCM with a random nonce per value, and the ciphertext carries its nonce so nothing has
 * to be tracked alongside. The key comes from {@code SMALLCRM_TOKEN_KEY} and there is no
 * fallback: without one the Google integration refuses to work rather than quietly writing
 * credentials in clear. That is the same stance the session key mistake taught — a default that
 * ships inside the artefact is not a key.
 */
@ApplicationScoped
public class TokenCrypto {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final SecureRandom RANDOM = new SecureRandom();

  /** Shortest key worth accepting; anything less is a passphrase somebody typed in a hurry. */
  static final int MIN_KEY_LENGTH = 32;

  @ConfigProperty(name = "smallcrm.google.token-key")
  Optional<String> configuredKey;

  /** Whether a usable key is configured, which is what gates the whole integration. */
  public boolean isConfigured() {
    return configuredKey.filter(key -> key.trim().length() >= MIN_KEY_LENGTH).isPresent();
  }

  public String encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }
    try {
      byte[] nonce = new byte[NONCE_BYTES];
      RANDOM.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[nonce.length + ciphertext.length];
      System.arraycopy(nonce, 0, combined, 0, nonce.length);
      System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
      return Base64.getEncoder().encodeToString(combined);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not encrypt a Google token", e);
    }
  }

  /**
   * Reverses {@link #encrypt}.
   *
   * @throws BusinessRuleException if the value cannot be read back, which in practice means the
   *     key was changed or the row was tampered with — both of which the user fixes by
   *     connecting the account again, not by retrying
   */
  public String decrypt(String stored) {
    if (stored == null) {
      return null;
    }
    try {
      byte[] combined = Base64.getDecoder().decode(stored);
      if (combined.length <= NONCE_BYTES) {
        throw new GeneralSecurityException("stored value is too short to be a token");
      }
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          key(),
          new GCMParameterSpec(TAG_BITS, combined, 0, NONCE_BYTES));
      byte[] plaintext =
          cipher.doFinal(combined, NONCE_BYTES, combined.length - NONCE_BYTES);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new BusinessRuleException(
          "GOOGLE_TOKEN_UNREADABLE",
          "The stored Google credentials cannot be read with the current key."
              + " Connect the Google account again.");
    }
  }

  /**
   * Derives the AES key from the configured secret.
   *
   * <p>SHA-256 of the configured value, so any length of key material produces the 256 bits AES
   * wants. This is not password stretching and does not need to be: the input is a generated
   * secret from the operator's configuration, not something a person chose.
   */
  private SecretKeySpec key() {
    String secret =
        configuredKey
            .filter(value -> value.trim().length() >= MIN_KEY_LENGTH)
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        "GOOGLE_NOT_CONFIGURED",
                        "The Google integration needs SMALLCRM_TOKEN_KEY set to at least "
                            + MIN_KEY_LENGTH
                            + " characters before it can store credentials."));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(digest, "AES");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
