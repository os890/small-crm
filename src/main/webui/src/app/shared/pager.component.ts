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

import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';

/**
 * Moves through a paged list.
 *
 * <p>Deliberately says "51–100 of 812" rather than "page 2 of 17": the question someone actually
 * has in front of a truncated table is whether they are seeing everything, and a page number
 * does not answer it.
 *
 * <p>Renders nothing at all while everything fits on one page, so short lists — which is what
 * most of this application's lists are — look exactly as they did before.
 */
@Component({
  selector: 'app-pager',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (total() > size()) {
      <div class="pager" data-testid="pager">
        <span class="muted" data-testid="pager-range">
          {{ t('pager.range', { from: from(), to: to(), total: total() }) }}
        </span>
        <div class="row">
          <button
            type="button"
            class="btn btn-sm"
            data-testid="pager-previous"
            [disabled]="page() === 0"
            (click)="goTo.emit(page() - 1)"
          >
            &larr; {{ t('pager.previous') }}
          </button>
          <button
            type="button"
            class="btn btn-sm"
            data-testid="pager-next"
            [disabled]="!hasNext()"
            (click)="goTo.emit(page() + 1)"
          >
            {{ t('pager.next') }} &rarr;
          </button>
        </div>
      </div>
    }
  `,
  styles: `
    .pager {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      flex-wrap: wrap;
      padding: var(--space-2) var(--space-3);
      border-top: 1px solid var(--line);
    }
  `,
})
export class PagerComponent {
  /** Zero-based index of the page on screen. */
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly total = input.required<number>();
  /** How many records the page on screen actually holds; the last page is usually short. */
  readonly shown = input.required<number>();

  /** Emits the zero-based page the user asked for. */
  readonly goTo = output<number>();

  protected readonly t = inject(I18nService).t;

  protected readonly from = computed(() =>
    this.shown() === 0 ? 0 : this.page() * this.size() + 1,
  );
  protected readonly to = computed(() => this.page() * this.size() + this.shown());
  protected readonly hasNext = computed(() => this.to() < this.total());
}
