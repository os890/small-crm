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

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.smallcrm.domain.AppUser;

/**
 * Creates the initial administrator the first time the application starts against an empty
 * database.
 *
 * <p>The account is flagged with {@code mustChangePassword} so the configured bootstrap password
 * cannot survive the first login.
 */
@ApplicationScoped
public class BootstrapAdminService {

  private static final Logger LOG = Logger.getLogger(BootstrapAdminService.class);

  @ConfigProperty(name = "smallcrm.bootstrap.admin.username")
  String bootstrapUsername;

  @ConfigProperty(name = "smallcrm.bootstrap.admin.password")
  String bootstrapPassword;

  void onStart(@Observes StartupEvent event) {
    createAdminIfNoUsersExist();
  }

  /**
   * Adds the bootstrap administrator when the user table is empty.
   *
   * @return the created account, or {@code null} if accounts already existed
   */
  @Transactional
  public AppUser createAdminIfNoUsersExist() {
    if (AppUser.count() > 0) {
      return null;
    }
    AppUser admin = new AppUser();
    admin.username = bootstrapUsername;
    admin.password = BcryptUtil.bcryptHash(bootstrapPassword);
    admin.roles = AppUser.ROLE_ADMIN + "," + AppUser.ROLE_USER;
    admin.fullName = "Administrator";
    admin.mustChangePassword = true;
    admin.active = true;
    admin.persist();
    LOG.infof(
        "Created bootstrap administrator '%s'. The password must be changed on first login.",
        bootstrapUsername);
    return admin;
  }
}
