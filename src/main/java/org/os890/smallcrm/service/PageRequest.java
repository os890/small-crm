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

import org.os890.smallcrm.api.error.BusinessRuleException;

/**
 * One page of a list request.
 *
 * <p>Every list endpoint is paged, so no single request can ever ask the database for a table
 * that has grown without limit. The activity log is the one that really needs it: it gains a row
 * for every call and e-mail ever logged and is never pruned.
 *
 * @param page zero-based page index
 * @param size number of records on the page, never above {@link #MAX_SIZE}
 */
public record PageRequest(int page, int size) {

  /** Page size used when the caller does not ask for one. */
  public static final int DEFAULT_SIZE = 50;

  /**
   * Largest page a caller may ask for.
   *
   * <p>A request for more is served with this many rather than rejected: the total count in the
   * response tells the caller there is more, and failing an otherwise valid request over an
   * ambitious page size would be unhelpful.
   */
  public static final int MAX_SIZE = 200;

  /** The whole of a list that is known to be small, used by internal callers. */
  public static PageRequest unpagedUpToMax() {
    return new PageRequest(0, MAX_SIZE);
  }

  /**
   * Reads the {@code page} and {@code size} query parameters.
   *
   * @throws BusinessRuleException if either is present but not a usable number
   */
  public static PageRequest of(Integer page, Integer size) {
    int index = page == null ? 0 : page;
    int length = size == null ? DEFAULT_SIZE : size;
    if (index < 0) {
      throw new BusinessRuleException("INVALID_PAGE", "'page' cannot be negative");
    }
    if (length < 1) {
      throw new BusinessRuleException("INVALID_PAGE_SIZE", "'size' must be at least 1");
    }
    return new PageRequest(index, Math.min(length, MAX_SIZE));
  }
}
