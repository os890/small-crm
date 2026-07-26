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
import java.util.List;
import java.util.function.Function;

/**
 * One page of results together with how many there are in total.
 *
 * <p>The total is what lets the interface say "51–100 of 812" and grey out the next button; a
 * page of records alone cannot tell the user whether they are seeing everything.
 *
 * @param items the records on this page, in the requested order
 * @param total how many records match the query in total
 * @param page zero-based index of this page
 * @param size the page size the query was served with
 */
public record Paged<T>(List<T> items, long total, int page, int size) {

  /** An empty page, for callers that can answer without going to the database. */
  public static <T> Paged<T> empty(PageRequest request) {
    return new Paged<>(List.of(), 0, request.page(), request.size());
  }

  /** Runs the query for one page and maps each entity with {@code toDto}. */
  public static <E, T> Paged<T> of(
      PanacheQuery<E> query, PageRequest request, Function<E, T> toDto) {
    // count() before page(): it must reflect the whole result set, not the page.
    long total = query.count();
    List<T> items =
        query.page(request.page(), request.size()).list().stream().map(toDto).toList();
    return new Paged<>(items, total, request.page(), request.size());
  }

  /** Replaces the records while keeping the paging information, for post-mapping. */
  public <R> Paged<R> map(Function<List<T>, List<R>> mapper) {
    return new Paged<>(mapper.apply(items), total, page, size);
  }
}
