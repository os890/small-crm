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

package org.os890.smallcrm.security;

import io.quarkus.elytron.security.common.BcryptUtil;

/**
 * The single place passwords are turned into hashes and checked against them.
 *
 * <p>BCrypt's library default cost is 10, the floor of current guidance rather than a target.
 * This uses 12, roughly four times the work for an attacker cracking a leaked hash and still only
 * a couple of hundred milliseconds once per login. Existing hashes carry their own cost inside
 * the modular crypt string, so raising this invalidates nothing; accounts move up the next time
 * their password is set.
 *
 * <p>Passwords are <em>not</em> pre-hashed to work around BCrypt's 72 byte limit, tempting as
 * that is. Login is verified by the Quarkus security-jpa mechanism against the raw submitted
 * password, so any transformation applied here and not there would lock every account out. The
 * limit is handled where it belongs instead: the request DTOs cap a password at
 * {@link #MAX_LENGTH} characters, so nothing a user types is ever silently discarded.
 */
public final class Passwords {

  /** BCrypt work factor. */
  public static final int COST = 12;

  /**
   * Longest password accepted. BCrypt hashes at most 72 bytes and ignores the rest without
   * complaining, so the field is capped rather than truncated behind the user's back.
   */
  public static final int MAX_LENGTH = 72;

  /** Shortest password accepted. */
  public static final int MIN_LENGTH = 12;

  private Passwords() {
  }

  /** Hashes a password for storage. */
  public static String hash(String rawPassword) {
    return BcryptUtil.bcryptHash(rawPassword, COST);
  }

  /** Checks a password against a stored hash, the same way the login mechanism does. */
  public static boolean matches(String rawPassword, String storedHash) {
    if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
      return false;
    }
    return BcryptUtil.matches(rawPassword, storedHash);
  }
}
