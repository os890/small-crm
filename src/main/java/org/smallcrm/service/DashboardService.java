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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import org.smallcrm.api.dto.DashboardDto;
import org.smallcrm.domain.Company;
import org.smallcrm.domain.Contact;
import org.smallcrm.domain.Deal;
import org.smallcrm.domain.DealStage;

/** Assembles the start page in a single call. */
@ApplicationScoped
public class DashboardService {

  private static final int UPCOMING_DAYS = 7;
  private static final int RECENT_INTERACTIONS = 10;

  @Inject CrmTaskService taskService;
  @Inject AppointmentService appointmentService;
  @Inject InteractionService interactionService;

  public DashboardDto summary() {
    List<DealStage> closed = DealService.closedStages();
    long openDeals = Deal.count("stage not in ?1", closed);
    BigDecimal openValue =
        Deal.getEntityManager()
            .createQuery(
                "select coalesce(sum(d.amount), 0) from Deal d where d.stage not in :stages",
                BigDecimal.class)
            .setParameter("stages", closed)
            .getSingleResult();

    return new DashboardDto(
        Contact.count(),
        Company.count(),
        openDeals,
        openValue,
        taskService.overdue(),
        taskService.dueToday(),
        appointmentService.upcoming(UPCOMING_DAYS),
        interactionService.recent(RECENT_INTERACTIONS));
  }
}
