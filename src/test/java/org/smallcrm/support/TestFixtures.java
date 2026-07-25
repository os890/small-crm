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

package org.smallcrm.support;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import org.smallcrm.domain.AppUser;
import org.smallcrm.domain.Appointment;
import org.smallcrm.domain.Company;
import org.smallcrm.domain.Contact;
import org.smallcrm.domain.CrmTask;
import org.smallcrm.domain.Deal;
import org.smallcrm.domain.Interaction;
import org.smallcrm.domain.InteractionType;

/**
 * Brings the shared in-memory database back to a known state between tests and creates the small
 * amount of data the individual tests build on.
 */
@ApplicationScoped
public class TestFixtures {

  /** Password the bootstrap administrator is reset to before every test. */
  public static final String ADMIN_USERNAME = "admin";

  /** Matches the bootstrap password configured for the test profile. */
  public static final String ADMIN_PASSWORD = "changeit";

  /**
   * Deletes every business record, removes extra accounts and returns the administrator to a
   * usable state (known password, no forced password change).
   */
  @Transactional
  public void reset() {
    Interaction.deleteAll();
    CrmTask.deleteAll();
    Appointment.deleteAll();
    Deal.deleteAll();
    // Contacts own a tag collection table, so they have to go through the entity lifecycle
    // instead of a bulk delete. The flush is required: the bulk delete below would otherwise
    // run before these removals reach the database and trip the foreign key.
    Contact.<Contact>listAll().forEach(Contact::delete);
    Contact.getEntityManager().flush();
    Company.deleteAll();
    AppUser.delete("username <> ?1", ADMIN_USERNAME);
    AppUser admin = AppUser.findByUsername(ADMIN_USERNAME);
    admin.password = BcryptUtil.bcryptHash(ADMIN_PASSWORD);
    admin.mustChangePassword = false;
    admin.active = true;
    admin.roles = AppUser.ROLE_ADMIN + "," + AppUser.ROLE_USER;
  }

  /** Puts the administrator back into the "must change password" state. */
  @Transactional
  public void requirePasswordChangeForAdmin() {
    AppUser.findByUsername(ADMIN_USERNAME).mustChangePassword = true;
  }

  /** Switches an account off the way an administrator would. */
  @Transactional
  public void deactivate(String username) {
    AppUser.findByUsername(username).active = false;
  }

  @Transactional
  public AppUser createUser(String username, String password, boolean admin) {
    AppUser user = new AppUser();
    user.username = username;
    user.password = BcryptUtil.bcryptHash(password);
    user.roles = admin ? AppUser.ROLE_ADMIN + "," + AppUser.ROLE_USER : AppUser.ROLE_USER;
    user.fullName = username;
    user.active = true;
    user.mustChangePassword = false;
    user.persist();
    return user;
  }

  @Transactional
  public Company createCompany(String name) {
    Company company = new Company();
    company.name = name;
    company.city = "Vienna";
    company.persist();
    return company;
  }

  @Transactional
  public Contact createContact(String firstName, String lastName, Company company) {
    Contact contact = new Contact();
    contact.firstName = firstName;
    contact.lastName = lastName;
    contact.email = (firstName + "." + lastName + "@example.org").toLowerCase();
    contact.company = company;
    contact.tags.addAll(List.of("poc"));
    contact.persist();
    return contact;
  }

  @Transactional
  public Deal createDeal(String title, Contact contact) {
    Deal deal = new Deal();
    deal.title = title;
    deal.contact = contact;
    deal.persist();
    return deal;
  }

  @Transactional
  public Interaction createInteraction(Contact contact, Instant occurredAt) {
    Interaction interaction = new Interaction();
    interaction.type = InteractionType.CALL;
    interaction.subject = "Kickoff call";
    interaction.occurredAt = occurredAt;
    interaction.contact = contact;
    interaction.persist();
    return interaction;
  }

  @Transactional
  public Appointment createAppointment(String title, Instant startsAt, Instant endsAt,
      AppUser owner) {
    Appointment appointment = new Appointment();
    appointment.title = title;
    appointment.startsAt = startsAt;
    appointment.endsAt = endsAt;
    appointment.owner = owner;
    appointment.persist();
    return appointment;
  }

  /** The administrator account, needed as the owner of pre-seeded appointments. */
  @Transactional
  public AppUser admin() {
    return AppUser.findByUsername(ADMIN_USERNAME);
  }

  /** Hands a company to another owner, to verify what happens when that owner is removed. */
  @Transactional
  public void assignCompanyOwner(Long companyId, Long ownerId) {
    Company company = Company.findById(companyId);
    company.owner = AppUser.findById(ownerId);
  }
}
