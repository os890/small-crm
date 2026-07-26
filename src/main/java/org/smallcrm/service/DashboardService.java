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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.smallcrm.api.dto.DashboardDto;
import org.smallcrm.domain.Company;
import org.smallcrm.domain.Contact;
import org.smallcrm.domain.Deal;
import org.smallcrm.domain.DealStage;

/** Assembles the start page in a single call. */
@ApplicationScoped
public class DashboardService {

  /** The currency the headline figure is expressed in. */
  private static final String MAIN_CURRENCY = "EUR";

  private static final int UPCOMING_DAYS = 7;
  private static final int RECENT_INTERACTIONS = 10;

  @Inject CrmTaskService taskService;
  @Inject AppointmentService appointmentService;
  @Inject InteractionService interactionService;

  public DashboardDto summary() {
    List<DealStage> closed = DealService.closedStages();
    long openDeals = Deal.count("stage not in ?1", closed);

    // Grouped rather than summed outright: currency is a free field per deal, so adding
    // 10.000 EUR to 10.000 USD produced one number that was simply wrong, with nothing on
    // screen to say so.
    Map<String, BigDecimal> byCurrency = new LinkedHashMap<>();
    List<Object[]> rows =
        Deal.getEntityManager()
            .createQuery(
                "select coalesce(d.currency, 'EUR'), coalesce(sum(d.amount), 0) from Deal d"
                    + " where d.stage not in :stages group by d.currency order by d.currency",
                Object[].class)
            .setParameter("stages", closed)
            .getResultList();
    for (Object[] row : rows) {
      byCurrency.merge((String) row[0], (BigDecimal) row[1], BigDecimal::add);
    }
    BigDecimal headline = byCurrency.getOrDefault(MAIN_CURRENCY, BigDecimal.ZERO);

    return new DashboardDto(
        Contact.count(),
        Company.count(),
        openDeals,
        headline,
        byCurrency,
        taskService.overdue(),
        taskService.dueToday(),
        appointmentService.upcoming(UPCOMING_DAYS),
        interactionService.recent(RECENT_INTERACTIONS));
  }
}
