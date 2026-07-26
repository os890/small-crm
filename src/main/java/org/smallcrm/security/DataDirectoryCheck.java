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

import io.quarkus.runtime.configuration.ConfigurationException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Refuses to start on an empty database directory that was clearly meant to hold data.
 *
 * <p>H2 creates the file silently when it is missing, so a typo in {@code SMALLCRM_DATA_DIR}
 * produces a healthy looking application with a brand new, empty installation. For a
 * self-employed user that is the most plausible way to lose everything: they see a first-run
 * login, conclude their data is gone, and may start entering it again into the wrong file while
 * the real database sits untouched one directory away.
 *
 * <p>The rule is deliberately narrow, so a genuine first run is never blocked: complain only
 * when the operator pointed somewhere specific, that place has no database, and
 * {@code SMALLCRM_ALLOW_NEW_DATABASE} was not set to say "yes, create one".
 */
public final class DataDirectoryCheck {

  /** The path used when the operator sets nothing, where a first run is entirely expected. */
  static final String DEFAULT_DIR = "./data";

  static final String DATABASE_FILE = "smallcrm.mv.db";

  private DataDirectoryCheck() {
  }

  /**
   * Validates the configured data directory.
   *
   * @param configuredDir the value of {@code smallcrm.data-dir}
   * @param allowNew whether {@code SMALLCRM_ALLOW_NEW_DATABASE} permits creating one here
   * @throws ConfigurationException if a non-default directory holds no database
   */
  public static void verify(String configuredDir, boolean allowNew) {
    String dir = configuredDir == null || configuredDir.isBlank() ? DEFAULT_DIR : configuredDir;
    if (allowNew || isDefault(dir)) {
      return;
    }
    Path database = Path.of(dir).resolve(DATABASE_FILE);
    if (Files.exists(database)) {
      return;
    }
    throw new ConfigurationException(
        "No database found at "
            + database.toAbsolutePath().normalize()
            + "\n\nSMALLCRM_DATA_DIR points somewhere that holds no Small CRM database. Starting"
            + "\nanyway would create an empty one and look like a fresh installation, which is"
            + "\nhow existing data gets lost: it is still where it always was, under a different"
            + "\npath."
            + "\n\n  - Check the path for a typo, or"
            + "\n  - set SMALLCRM_ALLOW_NEW_DATABASE=true to create a new installation here.");
  }

  private static boolean isDefault(String dir) {
    Path configured = Path.of(dir).toAbsolutePath().normalize();
    return configured.equals(Path.of(DEFAULT_DIR).toAbsolutePath().normalize());
  }
}
