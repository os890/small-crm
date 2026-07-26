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
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.os890.smallcrm.api.dto.CrmTaskDto;
import org.os890.smallcrm.api.error.NotFoundException;
import org.os890.smallcrm.domain.CrmTask;
import org.os890.smallcrm.domain.TaskPriority;
import org.os890.smallcrm.security.CurrentUser;

/** Reads and writes follow-up tasks. */
@ApplicationScoped
public class CrmTaskService {

  @Inject CurrentUser currentUser;
  @Inject ReferenceResolver references;
  @Inject Clock clock;

  /**
   * One page of tasks ordered by due date, with tasks that have no due date last.
   *
   * @param openOnly when true, completed tasks are left out
   * @param contactId optional restriction to one contact
   * @param dealId optional restriction to one deal
   * @param page which page to return and how large it is
   */
  public Paged<CrmTaskDto> list(boolean openOnly, Long contactId, Long dealId, PageRequest page) {
    StringBuilder query = new StringBuilder("1 = 1");
    Map<String, Object> parameters = new HashMap<>();
    if (openOnly) {
      query.append(" and done = false");
    }
    if (contactId != null) {
      query.append(" and contact.id = :contactId");
      parameters.put("contactId", contactId);
    }
    if (dealId != null) {
      query.append(" and deal.id = :dealId");
      parameters.put("dealId", dealId);
    }
    Sort byDueDate = Sort.by("dueDate", Sort.Direction.Ascending, Sort.NullPrecedence.NULLS_LAST)
        .and("id");
    PanacheQuery<CrmTask> found = CrmTask.find(query.toString(), byDueDate, parameters);
    LocalDate today = today();
    return Paged.of(found, page, task -> CrmTaskDto.from(task, today));
  }

  /** Open tasks whose due date has already passed, most overdue first. */
  public List<CrmTaskDto> overdue() {
    List<CrmTask> tasks =
        CrmTask.list("done = false and dueDate < ?1", Sort.by("dueDate"), today());
    return toDtos(tasks);
  }

  /** Open tasks due exactly today. */
  public List<CrmTaskDto> dueToday() {
    List<CrmTask> tasks = CrmTask.list("done = false and dueDate = ?1", Sort.by("id"), today());
    return toDtos(tasks);
  }

  public CrmTaskDto get(Long id) {
    return CrmTaskDto.from(require(id), today());
  }

  @Transactional
  public CrmTaskDto create(CrmTaskDto input) {
    CrmTask task = new CrmTask();
    apply(input, task);
    task.owner = currentUser.find().orElse(null);
    task.persist();
    return CrmTaskDto.from(task, today());
  }

  @Transactional
  public CrmTaskDto update(Long id, CrmTaskDto input) {
    CrmTask task = require(id);
    Versions.check(input.version(), task);
    apply(input, task);
    return CrmTaskDto.from(task, today());
  }

  /** Ticks a task off or reopens it, keeping {@code completedAt} consistent either way. */
  @Transactional
  public CrmTaskDto setDone(Long id, boolean done) {
    CrmTask task = require(id);
    applyDone(task, done);
    return CrmTaskDto.from(task, today());
  }

  @Transactional
  public void delete(Long id) {
    require(id).delete();
  }

  private void apply(CrmTaskDto input, CrmTask task) {
    task.title = input.title();
    task.description = input.description();
    task.dueDate = input.dueDate();
    task.priority = input.priority() == null ? TaskPriority.NORMAL : input.priority();
    task.contact = references.contact(input.contactId());
    task.deal = references.deal(input.dealId());
    applyDone(task, input.done());
  }

  private void applyDone(CrmTask task, boolean done) {
    if (done && !task.done) {
      task.completedAt = Instant.now(clock);
    } else if (!done) {
      task.completedAt = null;
    }
    task.done = done;
  }

  private List<CrmTaskDto> toDtos(List<CrmTask> tasks) {
    LocalDate today = today();
    return tasks.stream().map(task -> CrmTaskDto.from(task, today)).toList();
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private static CrmTask require(Long id) {
    CrmTask task = CrmTask.findById(id);
    if (task == null) {
      throw new NotFoundException("Task", id);
    }
    return task;
  }
}
