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

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.smallcrm.api.dto.DealDto;
import org.smallcrm.api.error.BusinessRuleException;
import org.smallcrm.api.error.NotFoundException;
import org.smallcrm.domain.Appointment;
import org.smallcrm.domain.CrmTask;
import org.smallcrm.domain.Deal;
import org.smallcrm.domain.DealStage;
import org.smallcrm.domain.Interaction;
import org.smallcrm.security.CurrentUser;

/** Reads and writes deals and moves them through the pipeline. */
@ApplicationScoped
public class DealService {

  @Inject CurrentUser currentUser;
  @Inject ReferenceResolver references;

  /**
   * Deals ordered by pipeline stage then expected close date.
   *
   * @param stage optional restriction to one stage
   * @param openOnly when true, won and lost deals are left out
   */
  public List<DealDto> list(DealStage stage, boolean openOnly) {
    Sort order = Sort.by("stage").and("expectedCloseDate").and("id");
    List<Deal> deals;
    if (stage != null) {
      deals = Deal.list("stage", order, stage);
    } else if (openOnly) {
      deals = Deal.list("stage not in ?1", order, DealService.closedStages());
    } else {
      deals = Deal.listAll(order);
    }
    return deals.stream().map(DealDto::from).toList();
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
    Interaction.update("deal = null where deal = ?1", deal);
    CrmTask.update("deal = null where deal = ?1", deal);
    Appointment.update("deal = null where deal = ?1", deal);
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
