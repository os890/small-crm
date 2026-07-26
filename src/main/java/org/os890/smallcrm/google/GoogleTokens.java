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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What Google's token endpoint hands back.
 *
 * <p>{@code refreshToken} is only present the first time an account consents. Asking again for
 * an account that has already agreed returns an access token and nothing else, which is why a
 * reconnection keeps the refresh token it already has rather than overwriting it with null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTokens(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expires_in") Long expiresIn,
    @JsonProperty("id_token") String idToken,
    @JsonProperty("scope") String scope,
    @JsonProperty("token_type") String tokenType) {

  /** Seconds an access token is good for, defaulting low rather than assuming an hour. */
  public long lifetimeSeconds() {
    return expiresIn == null ? 300 : expiresIn;
  }

  public String scopeOrEmpty() {
    return scope == null ? "" : scope;
  }
}
