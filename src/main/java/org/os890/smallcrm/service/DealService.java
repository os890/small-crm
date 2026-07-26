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

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.os890.smallcrm.api.dto.DealDto;
import org.os890.smallcrm.api.error.BusinessRuleException;
import org.os890.smallcrm.api.error.NotFoundException;
import org.os890.smallcrm.domain.Appointment;
import org.os890.smallcrm.domain.CrmTask;
import org.os890.smallcrm.domain.Deal;
import org.os890.smallcrm.domain.DealStage;
import org.os890.smallcrm.domain.Interaction;
import org.os890.smallcrm.security.CurrentUser;

/** Reads and writes deals and moves them through the pipeline. */
@ApplicationScoped
public class DealService {

  /**
   * Orders by pipeline position, then by expected close date.
   *
   * <p>The stage column stores the constant's name, so ordering by the column itself is
   * alphabetical and reads LEAD, LOST, PROPOSAL, QUALIFIED, WON. This was previously corrected by
   * re-sorting the result in memory, which cannot work once only one page is fetched: the
   * database has to hand back the right rows in the right order in the first place.
   */
  private static final String PIPELINE_ORDER = pipelineOrder();

  @Inject CurrentUser currentUser;
  @Inject ReferenceResolver references;
  @Inject Clock clock;

  /**
   * One page of deals, ordered by pipeline stage then expected close date.
   *
   * @param stage optional restriction to one stage
   * @param openOnly when true, won and lost deals are left out
   * @param contactId optional restriction to the deals of one contact
   * @param page which page to return and how large it is
   */
  public Paged<DealDto> list(DealStage stage, boolean openOnly, Long contactId, PageRequest page) {
    StringBuilder query = new StringBuilder("1 = 1");
    Map<String, Object> parameters = new HashMap<>();
    if (stage != null) {
      query.append(" and stage = :stage");
      parameters.put("stage", stage);
    } else if (openOnly) {
      query.append(" and stage not in :closed");
      parameters.put("closed", closedStages());
    }
    if (contactId != null) {
      query.append(" and contact.id = :contactId");
      parameters.put("contactId", contactId);
    }
    PanacheQuery<Deal> found = Deal.find(query + PIPELINE_ORDER, parameters);
    return Paged.of(found, page, DealDto::from);
  }

  /**
   * Builds the {@code order by} that puts the stages in pipeline order.
   *
   * <p>Generated from the enum rather than written out, so a new stage cannot be added without
   * its position coming along.
   */
  private static String pipelineOrder() {
    StringBuilder order = new StringBuilder(" order by case stage");
    for (DealStage value : DealStage.values()) {
      // HQL wants the enum literal fully qualified. Taken from the class rather than written
      // out, so moving the package cannot leave a string here that no longer resolves.
      order
          .append(" when ")
          .append(DealStage.class.getName())
          .append('.')
          .append(value.name())
          .append(" then ")
          .append(value.order());
    }
    return order.append(" end, expectedCloseDate asc nulls last, id asc").toString();
  }

  public DealDto get(Long id) {
    return DealDto.from(require(id));
  }

  @Transactional
  public DealDto create(DealDto input) {
    Deal deal = new Deal();
    apply(input, deal);
    deal.owner = currentUser.find().orElse(null);
    deal.persist();
    return DealDto.from(deal);
  }

  @Transactional
  public DealDto update(Long id, DealDto input) {
    Deal deal = require(id);
    Versions.check(input.version(), deal);
    apply(input, deal);
    return DealDto.from(deal);
  }

  /** Moves a deal to another stage; used by the drag-free pipeline buttons in the UI. */
  @Transactional
  public DealDto changeStage(Long id, DealStage stage) {
    if (stage == null) {
      throw new BusinessRuleException("STAGE_REQUIRED", "A target stage must be given");
    }
    Deal deal = require(id);
    deal.stage = stage;
    return DealDto.from(deal);
  }

  /** Removes a deal and detaches the interactions, tasks and appointments that referenced it. */
  @Transactional
  public void delete(Long id) {
    Deal deal = require(id);
    Instant now = Instant.now(clock);
    // "update versioned" so the detached rows get a new @Version and updatedAt: a plain
    // bulk update runs no @PreUpdate, and another transaction holding one of these rows
    // would then save over the detach without its optimistic-lock check noticing.
    Interaction.update(
        "update versioned Interaction set deal = null, updatedAt = ?1 where deal = ?2", now, deal);
    CrmTask.update(
        "update versioned CrmTask set deal = null, updatedAt = ?1 where deal = ?2", now, deal);
    Appointment.update(
        "update versioned Appointment set deal = null, updatedAt = ?1 where deal = ?2", now, deal);
    deal.delete();
  }

  static List<DealStage> closedStages() {
    return List.of(DealStage.values()).stream().filter(DealStage::isClosed).toList();
  }

  private void apply(DealDto input, Deal deal) {
    deal.title = input.title();
    deal.contact = references.contact(input.contactId());
    deal.company = references.company(input.companyId());
    deal.amount = input.amount();
    deal.currency = input.currency() == null ? "EUR" : input.currency().toUpperCase();
    deal.stage = input.stage() == null ? DealStage.LEAD : input.stage();
    deal.expectedCloseDate = input.expectedCloseDate();
    deal.notes = input.notes();
  }

  private static Deal require(Long id) {
    Deal deal = Deal.findById(id);
    if (deal == null) {
      throw new NotFoundException("Deal", id);
    }
    return deal;
  }
}
