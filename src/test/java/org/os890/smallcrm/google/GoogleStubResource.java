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

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;

/**
 * Starts {@link GoogleStub} and points the application's Google endpoints at it.
 *
 * <p>The stub takes a free port, which is only known once it is running, so the configuration
 * has to be produced here rather than written in a properties file.
 */
public class GoogleStubResource implements QuarkusTestResourceLifecycleManager {

  /** The one the running test talks to; set before the application starts. */
  public static GoogleStub stub;

  @Override
  public Map<String, String> start() {
    stub = new GoogleStub();
    Map<String, String> config = new HashMap<>();
    config.put("smallcrm.google.client-id", "test-client-id.apps.googleusercontent.com");
    config.put("smallcrm.google.client-secret", "test-client-secret");
    config.put("smallcrm.google.redirect-uri", "http://localhost:8081/api/google/callback");
    config.put("smallcrm.google.token-key", "a-generated-token-key-for-the-test-suite");
    config.put("smallcrm.google.contact-label", "Small CRM");
    config.put("smallcrm.google.auth-uri", stub.baseUrl() + "/o/oauth2/v2/auth");
    config.put("smallcrm.google.token-uri", stub.baseUrl() + "/token");
    config.put("smallcrm.google.api-base", stub.baseUrl());
    config.put("smallcrm.google.people-base", stub.baseUrl());
    return config;
  }

  @Override
  public void stop() {
    if (stub != null) {
      stub.close();
      stub = null;
    }
  }
}
