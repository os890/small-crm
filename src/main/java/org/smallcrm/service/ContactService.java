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

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.smallcrm.api.dto.ContactDto;
import org.smallcrm.api.error.NotFoundException;
import org.smallcrm.domain.Appointment;
import org.smallcrm.domain.Contact;
import org.smallcrm.domain.CrmTask;
import org.smallcrm.domain.Deal;
import org.smallcrm.domain.Interaction;
import org.smallcrm.security.CurrentUser;

/** Reads and writes contacts. */
@ApplicationScoped
public class ContactService {

  @Inject CurrentUser currentUser;
  @Inject ReferenceResolver references;
  @Inject Clock clock;

  /**
   * One page of contacts, ordered by last name then first name.
   *
   * @param search optional case-insensitive fragment matched against name, e-mail and phone
   * @param companyId optional restriction to one company
   * @param page which page to return and how large it is
   */
  public Paged<ContactDto> list(String search, Long companyId, PageRequest page) {
    Sort byName = Sort.by("lastName").and("firstName");
    StringBuilder query = new StringBuilder("1 = 1");
    Map<String, Object> parameters = new HashMap<>();
    if (search != null && !search.isBlank()) {
      query.append(
          " and (lower(firstName) like :term or lower(lastName) like :term"
              + " or lower(coalesce(email, '')) like :term"
              + " or lower(coalesce(phone, '')) like :term)");
      parameters.put("term", "%" + search.trim().toLowerCase() + "%");
    }
    if (companyId != null) {
      query.append(" and company.id = :companyId");
      parameters.put("companyId", companyId);
    }
    PanacheQuery<Contact> found = Contact.find(query.toString(), byName, parameters);
    return Paged.of(found, page, ContactDto::from);
  }

  public ContactDto get(Long id) {
    return ContactDto.from(require(id));
  }

  @Transactional
  public ContactDto create(ContactDto input) {
    Contact contact = new Contact();
    apply(input, contact);
    contact.owner = currentUser.find().orElse(null);
    contact.persist();
    return ContactDto.from(contact);
  }

  @Transactional
  public ContactDto update(Long id, ContactDto input) {
    Contact contact = require(id);
    Versions.check(input.version(), contact);
    apply(input, contact);
    return ContactDto.from(contact);
  }

  /**
   * Removes a contact together with its interaction history, and detaches deals, tasks and
   * appointments that referenced it.
   */
  @Transactional
  public void delete(Long id) {
    Contact contact = require(id);
    Instant now = Instant.now(clock);
    Interaction.delete("contact = ?1", contact);
    // "update versioned" so the detached rows get a new @Version and updatedAt: a plain
    // bulk update runs no @PreUpdate, and another transaction holding one of these rows
    // would then save over the detach without its optimistic-lock check noticing.
    Deal.update(
        "update versioned Deal set contact = null, updatedAt = ?1 where contact = ?2",
        now,
        contact);
    CrmTask.update(
        "update versioned CrmTask set contact = null, updatedAt = ?1 where contact = ?2",
        now,
        contact);
    Appointment.update(
        "update versioned Appointment set contact = null, updatedAt = ?1 where contact = ?2",
        now,
        contact);
    contact.delete();
  }

  /** Every tag in use, sorted, so the UI can offer them for reuse. */
  public List<String> allTags() {
    return Contact.getEntityManager()
        .createQuery("select distinct t from Contact c join c.tags t order by t", String.class)
        .getResultList();
  }

  private void apply(ContactDto input, Contact contact) {
    contact.firstName = input.firstName();
    contact.lastName = input.lastName();
    contact.email = input.email();
    contact.phone = input.phone();
    contact.mobile = input.mobile();
    contact.position = input.position();
    contact.company = references.company(input.companyId());
    contact.notes = input.notes();
    contact.tags.clear();
    contact.tags.addAll(cleanTags(input.tags()));
  }

  private static Set<String> cleanTags(Set<String> raw) {
    Set<String> cleaned = new LinkedHashSet<>();
    if (raw == null) {
      return cleaned;
    }
    for (String tag : raw) {
      if (tag != null && !tag.isBlank()) {
        cleaned.add(tag.trim());
      }
    }
    return cleaned;
  }

  private static Contact require(Long id) {
    Contact contact = Contact.findById(id);
    if (contact == null) {
      throw new NotFoundException("Contact", id);
    }
    return contact;
  }
}
