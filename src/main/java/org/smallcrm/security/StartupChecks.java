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

package org.smallcrm.security;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Runs the checks that must pass before the application accepts any traffic.
 *
 * <p>Observes {@link StartupEvent} at the highest priority so a misconfiguration is reported
 * before the bootstrap administrator is created.
 *
 * <p>There is deliberately no check for a session encryption key: the application issues random
 * session tokens and keeps the sessions in the database, so it has no shared secret that could
 * be left at a default and used to forge a login.
 */
@ApplicationScoped
public class StartupChecks {

  @ConfigProperty(name = "smallcrm.data-dir", defaultValue = DataDirectoryCheck.DEFAULT_DIR)
  String dataDir;

  @ConfigProperty(name = "smallcrm.allow-new-database", defaultValue = "false")
  boolean allowNewDatabase;

  @ConfigProperty(name = "quarkus.profile", defaultValue = "prod")
  String profile;

  void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent event) {
    // Tests and dev runs use throwaway directories all the time; the guard would only be noise.
    if ("prod".equals(profile)) {
      DataDirectoryCheck.verify(dataDir, allowNewDatabase);
    }
  }
}
