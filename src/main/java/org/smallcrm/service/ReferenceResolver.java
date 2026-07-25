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
import org.smallcrm.api.error.NotFoundException;
import org.smallcrm.domain.Company;
import org.smallcrm.domain.Contact;
import org.smallcrm.domain.Deal;

/**
 * Turns the identifiers that arrive in a request payload into managed entities.
 *
 * <p>A {@code null} identifier means "no relation" and yields {@code null}; a non-null identifier
 * that does not resolve is a client error and raises {@link NotFoundException}.
 */
@ApplicationScoped
public class ReferenceResolver {

  public Company company(Long id) {
    if (id == null) {
      return null;
    }
    Company company = Company.findById(id);
    if (company == null) {
      throw new NotFoundException("Company", id);
    }
    return company;
  }

  public Contact contact(Long id) {
    if (id == null) {
      return null;
    }
    Contact contact = Contact.findById(id);
    if (contact == null) {
      throw new NotFoundException("Contact", id);
    }
    return contact;
  }

  public Deal deal(Long id) {
    if (id == null) {
      return null;
    }
    Deal deal = Deal.findById(id);
    if (deal == null) {
      throw new NotFoundException("Deal", id);
    }
    return deal;
  }
}
