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

package org.smallcrm.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Objects;
import org.smallcrm.api.dto.ChangePasswordRequest;
import org.smallcrm.api.dto.CreateUserRequest;
import org.smallcrm.api.dto.ResetPasswordRequest;
import org.smallcrm.api.dto.UpdateUserRequest;
import org.smallcrm.api.dto.UserDto;
import org.smallcrm.api.error.BusinessRuleException;
import org.smallcrm.api.error.NotFoundException;
import org.smallcrm.domain.AppUser;
import org.smallcrm.domain.Appointment;
import org.smallcrm.domain.Company;
import org.smallcrm.domain.Contact;
import org.smallcrm.domain.CrmTask;
import org.smallcrm.domain.Deal;
import org.smallcrm.domain.Interaction;
import org.smallcrm.security.CurrentUser;

/**
 * Manages accounts.
 *
 * <p>Every mutation is guarded so an installation can never be left without a usable
 * administrator, which for a single-person business would mean a permanent lockout.
 */
@ApplicationScoped
public class UserService {

  @Inject CurrentUser currentUser;

  public List<UserDto> list() {
    List<AppUser> users = AppUser.listAll(Sort.by("username"));
    return users.stream().map(UserDto::from).toList();
  }

  public UserDto get(Long id) {
    return UserDto.from(require(id));
  }

  /** The profile of the signed-in account. */
  public UserDto me() {
    return UserDto.from(currentUser.get());
  }

  @Transactional
  public UserDto create(CreateUserRequest request) {
    String username = request.username().trim();
    if (AppUser.findByUsername(username) != null) {
      throw new BusinessRuleException(
          "USERNAME_TAKEN", "The username '" + username + "' is already in use");
    }
    AppUser user = new AppUser();
    user.username = username;
    user.password = BcryptUtil.bcryptHash(request.password());
    user.roles = rolesFor(request.admin());
    user.fullName = request.fullName();
    user.email = request.email();
    user.active = true;
    // The administrator picked the initial password, so the new user has to replace it.
    user.mustChangePassword = true;
    user.persist();
    return UserDto.from(user);
  }

  @Transactional
  public UserDto update(Long id, UpdateUserRequest request) {
    AppUser user = require(id);
    boolean losesAdmin = user.isAdmin() && !request.admin();
    boolean losesAccess = user.active && !request.active();
    if (losesAdmin || losesAccess) {
      requireAnotherAdminRemains(user);
    }
    if (losesAccess && isSelf(user)) {
      throw new BusinessRuleException(
          "SELF_DEACTIVATION", "You cannot deactivate your own account");
    }
    user.fullName = request.fullName();
    user.email = request.email();
    user.roles = rolesFor(request.admin());
    user.active = request.active();
    return UserDto.from(user);
  }

  /** Sets a temporary password for another account and forces a change at the next login. */
  @Transactional
  public UserDto resetPassword(Long id, ResetPasswordRequest request) {
    AppUser user = require(id);
    user.password = BcryptUtil.bcryptHash(request.newPassword());
    user.mustChangePassword = true;
    return UserDto.from(user);
  }

  /** Replaces the signed-in user's own password after verifying the current one. */
  @Transactional
  public UserDto changeOwnPassword(ChangePasswordRequest request) {
    AppUser user = currentUser.get();
    if (!BcryptUtil.matches(request.currentPassword(), user.password)) {
      throw new BusinessRuleException(
          "CURRENT_PASSWORD_WRONG", "The current password is not correct");
    }
    if (BcryptUtil.matches(request.newPassword(), user.password)) {
      throw new BusinessRuleException(
          "PASSWORD_UNCHANGED", "The new password must differ from the current one");
    }
    user.password = BcryptUtil.bcryptHash(request.newPassword());
    user.mustChangePassword = false;
    return UserDto.from(user);
  }

  /** Deletes an account; the records it created stay behind without an owner. */
  @Transactional
  public void delete(Long id) {
    AppUser user = require(id);
    if (isSelf(user)) {
      throw new BusinessRuleException("SELF_DELETION", "You cannot delete your own account");
    }
    if (user.isAdmin()) {
      requireAnotherAdminRemains(user);
    }
    Company.update("owner = null where owner = ?1", user);
    Contact.update("owner = null where owner = ?1", user);
    Deal.update("owner = null where owner = ?1", user);
    Interaction.update("owner = null where owner = ?1", user);
    CrmTask.update("owner = null where owner = ?1", user);
    Appointment.update("owner = null where owner = ?1", user);
    user.delete();
  }

  private boolean isSelf(AppUser user) {
    return currentUser.find().map(me -> Objects.equals(me.id, user.id)).orElse(false);
  }

  private static void requireAnotherAdminRemains(AppUser about) {
    long remaining =
        AppUser.count("active = true and roles like ?1 and id <> ?2", "%" + AppUser.ROLE_ADMIN
            + "%", about.id);
    if (remaining == 0) {
      throw new BusinessRuleException(
          "LAST_ADMIN", "At least one active administrator must remain");
    }
  }

  private static String rolesFor(boolean admin) {
    return admin ? AppUser.ROLE_ADMIN + "," + AppUser.ROLE_USER : AppUser.ROLE_USER;
  }

  private static AppUser require(Long id) {
    AppUser user = AppUser.findById(id);
    if (user == null) {
      throw new NotFoundException("User", id);
    }
    return user;
  }
}
