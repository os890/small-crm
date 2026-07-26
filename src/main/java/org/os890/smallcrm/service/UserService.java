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

package org.os890.smallcrm.service;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.os890.smallcrm.api.dto.ChangePasswordRequest;
import org.os890.smallcrm.api.dto.CreateUserRequest;
import org.os890.smallcrm.api.dto.ResetPasswordRequest;
import org.os890.smallcrm.api.dto.UpdateUserRequest;
import org.os890.smallcrm.api.dto.UserDto;
import org.os890.smallcrm.api.error.BusinessRuleException;
import org.os890.smallcrm.api.error.ConflictException;
import org.os890.smallcrm.api.error.NotFoundException;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.domain.Appointment;
import org.os890.smallcrm.domain.Company;
import org.os890.smallcrm.domain.Contact;
import org.os890.smallcrm.domain.CrmTask;
import org.os890.smallcrm.domain.Deal;
import org.os890.smallcrm.domain.Interaction;
import org.os890.smallcrm.security.CurrentUser;
import org.os890.smallcrm.security.Passwords;
import org.os890.smallcrm.security.SessionService;

/**
 * Manages accounts.
 *
 * <p>Every mutation is guarded so an installation can never be left without a usable
 * administrator, which for a single-person business would mean a permanent lockout. Anything
 * that weakens an account — a new password, deactivation, deletion — also ends that account's
 * sessions, so a session stolen earlier does not survive the countermeasure.
 */
@ApplicationScoped
public class UserService {

  @Inject CurrentUser currentUser;
  @Inject SessionService sessions;

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

  /** The signed-in account itself, for callers that need the entity. */
  public Optional<AppUser> currentAccount() {
    return currentUser.find();
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
    user.password = Passwords.hash(request.password());
    user.roles = rolesFor(request.admin());
    user.fullName = request.fullName();
    user.email = request.email();
    user.active = true;
    // The administrator picked the initial password, so the new user has to replace it.
    user.mustChangePassword = true;
    try {
      user.persist();
      // Surfaces the unique constraint here rather than as a 500 from a later flush, which is
      // what happens when two administrators add the same name at the same moment.
      user.getEntityManager().flush();
    } catch (PersistenceException e) {
      throw new ConflictException(
          "USERNAME_TAKEN", "The username '" + username + "' is already in use");
    }
    return UserDto.from(user);
  }

  @Transactional
  public UserDto update(Long id, UpdateUserRequest request) {
    AppUser user = require(id);
    checkVersion(request.version(), user);

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
    if (losesAccess) {
      // A deactivated account must stop working now, not when its session happens to expire.
      sessions.revokeAllFor(user);
    }
    return UserDto.from(user);
  }

  /**
   * Sets a temporary password for another account and forces a change at the next login.
   *
   * <p>Requires the acting administrator's own password: without it, a single hijacked admin
   * session could quietly take over every other account in the installation.
   */
  @Transactional
  public UserDto resetPassword(Long id, ResetPasswordRequest request) {
    AppUser acting = currentUser.get();
    if (!Passwords.matches(request.currentPassword(), acting.password)) {
      throw new BusinessRuleException(
          "CURRENT_PASSWORD_WRONG", "Your own password is not correct");
    }
    AppUser user = require(id);
    user.password = Passwords.hash(request.newPassword());
    user.mustChangePassword = true;
    // Whoever was signed in as that account is signed out by the reset.
    sessions.revokeAllFor(user);
    return UserDto.from(user);
  }

  /** Replaces the signed-in user's own password after verifying the current one. */
  @Transactional
  public UserDto changeOwnPassword(ChangePasswordRequest request) {
    AppUser user = currentUser.get();
    if (!Passwords.matches(request.currentPassword(), user.password)) {
      throw new BusinessRuleException(
          "CURRENT_PASSWORD_WRONG", "The current password is not correct");
    }
    if (Passwords.matches(request.newPassword(), user.password)) {
      throw new BusinessRuleException(
          "PASSWORD_UNCHANGED", "The new password must differ from the current one");
    }
    user.password = Passwords.hash(request.newPassword());
    user.mustChangePassword = false;
    // The usual reason to change a password is suspecting somebody else has it. Ending every
    // session of this account is the only thing that actually acts on that suspicion.
    sessions.revokeAllFor(user);
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
    sessions.revokeAllFor(user);
    // "update versioned" bumps @Version and keeps optimistic locking honest; a plain bulk
    // update would leave stale versions in other transactions looking current.
    Company.update("update versioned Company set owner = null, updatedAt = ?1 where owner = ?2",
        java.time.Instant.now(), user);
    Contact.update("update versioned Contact set owner = null, updatedAt = ?1 where owner = ?2",
        java.time.Instant.now(), user);
    Deal.update("update versioned Deal set owner = null, updatedAt = ?1 where owner = ?2",
        java.time.Instant.now(), user);
    Interaction.update(
        "update versioned Interaction set owner = null, updatedAt = ?1 where owner = ?2",
        java.time.Instant.now(), user);
    CrmTask.update("update versioned CrmTask set owner = null, updatedAt = ?1 where owner = ?2",
        java.time.Instant.now(), user);
    Appointment.update(
        "update versioned Appointment set owner = null, updatedAt = ?1 where owner = ?2",
        java.time.Instant.now(), user);
    user.delete();
  }

  private boolean isSelf(AppUser user) {
    return currentUser.find().map(me -> Objects.equals(me.id, user.id)).orElse(false);
  }

  /**
   * Rejects an edit based on a copy of the record that somebody else has since changed.
   *
   * @param submitted the version the client loaded, or {@code null} from an older client
   */
  private static void checkVersion(Long submitted, AppUser user) {
    if (submitted != null && submitted != user.version) {
      throw new ConflictException(
          "STALE_VERSION", "This account was changed by somebody else while you were editing it");
    }
  }

  /**
   * Refuses a change that would leave the installation with no administrator.
   *
   * <p>Counting and then acting is two statements: two administrators demoting each other at the
   * same moment would each see the other still in place, and both changes would land. The rows
   * are read under a write lock so the pair is atomic — there are never more than a handful of
   * administrators, so locking them all costs nothing.
   */
  private static void requireAnotherAdminRemains(AppUser about) {
    List<AppUser> remaining =
        AppUser.getEntityManager()
            .createQuery(
                "select u from AppUser u where u.active = true and u.roles like :role"
                    + " and u.id <> :id",
                AppUser.class)
            .setParameter("role", "%" + AppUser.ROLE_ADMIN + "%")
            .setParameter("id", about.id)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
    if (remaining.isEmpty()) {
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
