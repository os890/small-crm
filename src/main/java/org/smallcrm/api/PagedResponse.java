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

package org.smallcrm.api;

import jakarta.ws.rs.core.Response;
import org.smallcrm.service.Paged;

/**
 * Turns a {@link Paged} result into an HTTP response.
 *
 * <p>The body stays a plain JSON array and the paging information travels in headers. That keeps
 * every list endpoint's payload the shape it always had, so paging could be added without
 * rewriting each caller at the same time.
 */
public final class PagedResponse {

  /** How many records match in total, across all pages. */
  public static final String TOTAL_COUNT = "X-Total-Count";

  /** Zero-based index of the page in the body. */
  public static final String PAGE = "X-Page";

  /** How many records the page was served with. */
  public static final String PAGE_SIZE = "X-Page-Size";

  private PagedResponse() {
    // Utility class.
  }

  public static Response of(Paged<?> page) {
    return Response.ok(page.items())
        .header(TOTAL_COUNT, page.total())
        .header(PAGE, page.page())
        .header(PAGE_SIZE, page.size())
        .build();
  }
}
