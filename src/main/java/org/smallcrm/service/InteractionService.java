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
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.smallcrm.api.dto.InteractionDto;
import org.smallcrm.api.error.BusinessRuleException;
import org.smallcrm.api.error.NotFoundException;
import org.smallcrm.domain.Contact;
import org.smallcrm.domain.Interaction;
import org.smallcrm.security.CurrentUser;

/** Reads and writes the activity log. */
@ApplicationScoped
public class InteractionService {

  @Inject CurrentUser currentUser;
  @Inject ReferenceResolver references;
  @Inject Clock clock;

  /**
   * One page of logged interactions, most recent first.
   *
   * <p>This is the list that grows without bound — a row per call, e-mail and meeting for as long
   * as the installation lives — so it is never returned whole.
   *
   * @param contactId optional restriction to one contact
   * @param dealId optional restriction to one deal
   * @param page which page to return and how large it is
   */
  public Paged<InteractionDto> list(Long contactId, Long dealId, PageRequest page) {
    Sort newestFirst = Sort.by("occurredAt", Sort.Direction.Descending).and("id",
        Sort.Direction.Descending);
    PanacheQuery<Interaction> found;
    if (contactId != null && dealId != null) {
      found =
          Interaction.find("contact.id = ?1 and deal.id = ?2", newestFirst, contactId, dealId);
    } else if (contactId != null) {
      found = Interaction.find("contact.id", newestFirst, contactId);
    } else if (dealId != null) {
      found = Interaction.find("deal.id", newestFirst, dealId);
    } else {
      found = Interaction.findAll(newestFirst);
    }
    return Paged.of(found, page, InteractionDto::from);
  }

  /** The most recently logged interactions across all contacts, used by the dashboard. */
  public List<InteractionDto> recent(int limit) {
    Sort newestFirst = Sort.by("occurredAt", Sort.Direction.Descending).and("id",
        Sort.Direction.Descending);
    List<Interaction> interactions =
        Interaction.findAll(newestFirst).page(Page.ofSize(limit)).list();
    return interactions.stream().map(InteractionDto::from).toList();
  }

  public InteractionDto get(Long id) {
    return InteractionDto.from(require(id));
  }

  @Transactional
  public InteractionDto create(InteractionDto input) {
    Interaction interaction = new Interaction();
    apply(input, interaction);
    interaction.owner = currentUser.find().orElse(null);
    interaction.persist();
    return InteractionDto.from(interaction);
  }

  @Transactional
  public InteractionDto update(Long id, InteractionDto input) {
    Interaction interaction = require(id);
    Versions.check(input.version(), interaction);
    apply(input, interaction);
    return InteractionDto.from(interaction);
  }

  @Transactional
  public void delete(Long id) {
    require(id).delete();
  }

  private void apply(InteractionDto input, Interaction interaction) {
    Contact contact = references.contact(input.contactId());
    if (contact == null) {
      throw new BusinessRuleException(
          "CONTACT_REQUIRED", "An interaction must be attached to a contact");
    }
    Instant occurredAt = input.occurredAt();
    if (occurredAt != null && occurredAt.isAfter(Instant.now(clock))) {
      throw new BusinessRuleException(
          "OCCURRED_AT_IN_FUTURE",
          "An interaction cannot be logged for the future; schedule an appointment instead");
    }
    interaction.type = input.type();
    interaction.occurredAt = occurredAt;
    interaction.subject = input.subject();
    interaction.notes = input.notes();
    interaction.contact = contact;
    interaction.deal = references.deal(input.dealId());
  }

  private static Interaction require(Long id) {
    Interaction interaction = Interaction.findById(id);
    if (interaction == null) {
      throw new NotFoundException("Interaction", id);
    }
    return interaction;
  }
}
