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

package org.smallcrm.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the start page shows, gathered in one request so the first screen after login needs
 * a single round trip.
 *
 * @param contactCount total number of contacts
 * @param companyCount total number of companies
 * @param openDealCount deals that are neither won nor lost
 * @param openDealValue summed value of the open deals in the main currency only; kept for the
 *     headline figure, which needs a single number
 * @param openDealValueByCurrency the same total split per currency, so a mixed pipeline is not
 *     presented as one wrong sum
 * @param overdueTasks open tasks whose due date has passed, most overdue first
 * @param tasksDueToday open tasks due today
 * @param upcomingAppointments appointments starting within the next seven days
 * @param recentInteractions the ten most recently logged interactions
 */
public record DashboardDto(
    long contactCount,
    long companyCount,
    long openDealCount,
    BigDecimal openDealValue,
    java.util.Map<String, BigDecimal> openDealValueByCurrency,
    List<CrmTaskDto> overdueTasks,
    List<CrmTaskDto> tasksDueToday,
    List<AppointmentDto> upcomingAppointments,
    List<InteractionDto> recentInteractions) {}
