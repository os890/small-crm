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

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.os890.smallcrm.domain.AppUser;

/**
 * Creates the initial administrator the first time the application starts against an empty
 * database.
 *
 * <p>The password is generated rather than defaulted. A published default would let anybody who
 * reaches the port before the operator's first login claim the installation for themselves: they
 * would be prompted to change the password, set their own, and the real owner would be locked
 * out for good, because this service never runs again once an account exists. A generated
 * password is printed once, to the operator's own console, and nowhere else.
 *
 * <p>The account is additionally flagged with {@code mustChangePassword} so the printed password
 * does not survive the first login either.
 */
@ApplicationScoped
public class BootstrapAdminService {

  private static final Logger LOG = Logger.getLogger(BootstrapAdminService.class);

  /** 24 random bytes, comfortably beyond guessing, still short enough to retype. */
  private static final int GENERATED_PASSWORD_BYTES = 24;

  private static final SecureRandom RANDOM = new SecureRandom();

  @ConfigProperty(name = "smallcrm.bootstrap.admin.username")
  String bootstrapUsername;

  /**
   * Absent means "generate one", which is the recommended way to run this.
   *
   * <p>Optional rather than a String with an empty default: the property is declared in
   * application.properties with an empty value, and SmallRye treats an empty value as no value
   * — it does not fall back to {@code defaultValue}, it refuses to convert. Injecting a String
   * here made the application fail to start on exactly the path the README recommends.
   */
  @ConfigProperty(name = "smallcrm.bootstrap.admin.password")
  Optional<String> bootstrapPassword;

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
    String configured = bootstrapPassword.filter(value -> !value.isBlank()).orElse(null);
    boolean generated = configured == null;
    String password = generated ? generatePassword() : configured;

    AppUser admin = new AppUser();
    admin.username = bootstrapUsername;
    admin.password = Passwords.hash(password);
    admin.roles = AppUser.ROLE_ADMIN + "," + AppUser.ROLE_USER;
    admin.fullName = "Administrator";
    admin.mustChangePassword = true;
    admin.active = true;
    admin.persist();

    if (generated) {
      // Deliberately the only place this ever appears. It is not written to the database in
      // clear, not returned by any endpoint and not repeated on the next start.
      LOG.infof(
          "%n%n=================== Small CRM first start ==================="
              + "%n  A single administrator account has been created."
              + "%n%n      user name: %s"
              + "%n      password:  %s"
              + "%n%n  Sign in now and choose your own password; this one stops working"
              + "%n  as soon as you do. It is not shown again."
              + "%n=============================================================%n",
          bootstrapUsername,
          password);
    } else {
      LOG.infof(
          "Created bootstrap administrator '%s' from the configured password."
              + " It must be changed at the first login.",
          bootstrapUsername);
    }
    return admin;
  }

  private static String generatePassword() {
    byte[] bytes = new byte[GENERATED_PASSWORD_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
