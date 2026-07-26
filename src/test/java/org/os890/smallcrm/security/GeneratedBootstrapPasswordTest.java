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

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.os890.smallcrm.domain.AppUser;

/**
 * Starting with no bootstrap password configured, which is the way the README tells operators
 * to run it: one is generated and printed once.
 *
 * <p>This exists because that path did not work. The property is declared in
 * application.properties with an empty value, and an empty value is not the same as an absent
 * one — SmallRye refuses to convert it rather than falling back to the injection point's
 * default, so the application failed to start at all. Every other test set a password, so
 * nothing noticed until a packaged build was run by hand.
 */
@QuarkusTest
@TestProfile(GeneratedBootstrapPasswordTest.NoPasswordProfile.class)
class GeneratedBootstrapPasswordTest {

  public static class NoPasswordProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "smallcrm.bootstrap.admin.password",
          "",
          // Its own database. The one the rest of the suite shares outlives each test class,
          // so the administrator another class bootstrapped would still be sitting in it and
          // the account this test is about would never be created.
          "quarkus.datasource.jdbc.url",
          "jdbc:h2:mem:smallcrm-bootstrap-test;DB_CLOSE_DELAY=-1");
    }
  }

  @Inject BootstrapAdminService bootstrap;

  @Test
  void an_administrator_is_created_with_a_password_nobody_configured() {
    // Reaching this line at all is most of the point: the injection failure took the whole
    // application down before any test body ran.
    AppUser admin = onlyAdmin();

    assertThat(admin).isNotNull();
    assertThat(admin.username).isEqualTo("admin");
    assertThat(admin.isAdmin()).isTrue();
    assertThat(admin.mustChangePassword).isTrue();
    // Hashed, and not one of the obvious guesses.
    assertThat(admin.password).startsWith("$2");
    assertThat(Passwords.matches("changeit", admin.password)).isFalse();
    assertThat(Passwords.matches("", admin.password)).isFalse();
    assertThat(Passwords.matches("admin", admin.password)).isFalse();
  }

  @Test
  void a_second_start_leaves_the_existing_account_alone() {
    AppUser before = onlyAdmin();

    assertThat(bootstrap.createAdminIfNoUsersExist()).isNull();
    assertThat(onlyAdmin().password).isEqualTo(before.password);
  }

  @Transactional
  AppUser onlyAdmin() {
    return AppUser.find("username", "admin").firstResult();
  }
}
