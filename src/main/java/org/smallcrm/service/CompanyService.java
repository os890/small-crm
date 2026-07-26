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
import org.smallcrm.api.dto.CompanyDto;
import org.smallcrm.api.error.NotFoundException;
import org.smallcrm.domain.Company;
import org.smallcrm.domain.Contact;
import org.smallcrm.domain.Deal;
import org.smallcrm.security.CurrentUser;

/** Reads and writes companies. */
@ApplicationScoped
public class CompanyService {

  @Inject CurrentUser currentUser;
  @Inject Clock clock;

  /**
   * One page of companies, ordered by name.
   *
   * @param search optional case-insensitive fragment matched against name and city
   * @param page which page to return and how large it is
   */
  public Paged<CompanyDto> list(String search, PageRequest page) {
    Sort byName = Sort.by("name").and("id");
    PanacheQuery<Company> found;
    if (search == null || search.isBlank()) {
      found = Company.findAll(byName);
    } else {
      String pattern = "%" + search.trim().toLowerCase() + "%";
      found =
          Company.find(
              "lower(name) like ?1 or lower(coalesce(city, '')) like ?1", byName, pattern);
    }
    return Paged.of(found, page, CompanyDto::from);
  }

  public CompanyDto get(Long id) {
    return CompanyDto.from(require(id));
  }

  @Transactional
  public CompanyDto create(CompanyDto input) {
    Company company = new Company();
    apply(input, company);
    company.owner = currentUser.find().orElse(null);
    company.persist();
    return CompanyDto.from(company);
  }

  @Transactional
  public CompanyDto update(Long id, CompanyDto input) {
    Company company = require(id);
    Versions.check(input.version(), company);
    apply(input, company);
    return CompanyDto.from(company);
  }

  /**
   * Removes a company and detaches, rather than deletes, the records that point at it. Losing a
   * company must never silently lose contacts or deals.
   */
  @Transactional
  public void delete(Long id) {
    Company company = require(id);
    Instant now = Instant.now(clock);
    // "update versioned" so the detached rows get a new @Version and updatedAt: a plain
    // bulk update runs no @PreUpdate, and another transaction holding one of these rows
    // would then save over the detach without its optimistic-lock check noticing.
    Contact.update(
        "update versioned Contact set company = null, updatedAt = ?1 where company = ?2",
        now,
        company);
    Deal.update(
        "update versioned Deal set company = null, updatedAt = ?1 where company = ?2",
        now,
        company);
    company.delete();
  }

  private static void apply(CompanyDto input, Company company) {
    company.name = input.name();
    company.vatId = input.vatId();
    company.website = input.website();
    company.email = input.email();
    company.phone = input.phone();
    company.street = input.street();
    company.postalCode = input.postalCode();
    company.city = input.city();
    company.country = input.country();
    company.notes = input.notes();
  }

  private static Company require(Long id) {
    Company company = Company.findById(id);
    if (company == null) {
      throw new NotFoundException("Company", id);
    }
    return company;
  }
}
