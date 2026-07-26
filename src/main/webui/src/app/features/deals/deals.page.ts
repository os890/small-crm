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

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { FormatService } from '../../core/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { DEAL_STAGES, Deal, DealStage, OPEN_DEAL_STAGES } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../shared/confirm.service';
import {
  EntityPickerComponent,
  PickerOption,
  PickerResult,
} from '../../shared/entity-picker.component';

/**
 * How many deals the board fetches at once.
 *
 * <p>Matches the API's maximum page size, so the board asks for everything it is allowed to.
 */
const MAX_BOARD_SIZE = 200;

/**
 * The pipeline as a column per stage.
 *
 * <p>Deliberately buttons instead of drag and drop: a click target that says where it moves the
 * card is far easier to use than a drag gesture, especially on a touch screen.
 */
@Component({
  selector: 'app-deals',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, EntityPickerComponent],
  template: `
    <div class="stack">
      <div class="row-between">
        <h1>{{ t('deals.heading') }}</h1>
        <button type="button" class="btn btn-primary" data-testid="new-deal" (click)="open()">
          + {{ t('deals.new') }}
        </button>
      </div>

      <label class="checkbox">
        <input
          type="checkbox"
          data-testid="deals-open-only"
          [checked]="openOnly()"
          (change)="toggleOpenOnly()"
        />
        <span>{{ t('deals.openOnly') }}</span>
      </label>

      @if (loading()) {
        <p class="muted">{{ t('common.loading') }}</p>
      } @else if (deals().length === 0) {
        <p class="card empty-state" data-testid="deals-empty">{{ t('deals.empty') }}</p>
      } @else {
        @if (truncated() > 0) {
          <p class="notice" data-testid="deals-truncated">
            {{ t('deals.showingFirst', { count: deals().length, total: total() }) }}
          </p>
        }
        <div class="board">
          @for (stage of visibleStages(); track stage) {
            <section class="column" [attr.data-testid]="'column-' + stage">
              <header class="column-head">
                <h2>{{ label('deals.stage', stage) }}</h2>
                <span class="faint">
                  {{ byStage()[stage].length }}
                  @for (total of stageTotals(stage); track total.currency) {
                    <span>· {{ format.money(total.amount, total.currency) }}</span>
                  }
                </span>
              </header>

              @for (deal of byStage()[stage]; track deal.id) {
                <article class="card card-pad deal" data-testid="deal-card">
                  <div class="row-between">
                    <strong class="grow">{{ deal.title }}</strong>
                    <span>{{ format.money(deal.amount, deal.currency) }}</span>
                  </div>
                  @if (deal.contactName || deal.companyName) {
                    <p class="faint">{{ deal.contactName || deal.companyName }}</p>
                  }
                  @if (deal.expectedCloseDate) {
                    <p class="faint">
                      {{ t('deals.expectedClose') }}: {{ format.date(deal.expectedCloseDate) }}
                    </p>
                  }
                  <div class="row">
                    <label class="grow">
                      <span class="visually-hidden">{{ t('deals.moveTo') }}</span>
                      <!--
                        The current stage is marked on the option rather than bound on the
                        select: Angular applies property bindings before the @for renders the
                        options, so a [value] on the select is discarded and every dropdown
                        falls back to showing the first stage.
                      -->
                      <select
                        class="move"
                        [attr.data-testid]="'deal-move-' + deal.id"
                        (change)="move(deal, $event)"
                      >
                        @for (target of stages; track target) {
                          <option [value]="target" [selected]="target === deal.stage">
                            {{ label('deals.stage', target) }}
                          </option>
                        }
                      </select>
                    </label>
                    <button type="button" class="btn btn-sm" (click)="open(deal)">
                      {{ t('action.edit') }}
                    </button>
                    <button type="button" class="btn btn-sm btn-danger" (click)="remove(deal)">
                      {{ t('action.delete') }}
                    </button>
                  </div>
                </article>
              }
            </section>
          }
        </div>
      }
    </div>

    @if (editing(); as draft) {
      <div class="backdrop">
        <form class="dialog" (ngSubmit)="save()" data-testid="deal-dialog">
          <div class="dialog-head">
            <h2>{{ draft.id ? t('deals.edit') : t('deals.new') }}</h2>
          </div>
          <div class="dialog-body">
            <div class="field" [class.invalid]="errors()['title']">
              <label for="deal-title">{{ t('deals.title') }} *</label>
              <input
                id="deal-title"
                name="title"
                data-testid="deal-title"
                required
                [(ngModel)]="draft.title"
              />
              @if (errors()['title']; as message) {
                <span class="field-error">{{ message }}</span>
              }
            </div>

            <div class="field-grid">
              <div class="field" [class.invalid]="errors()['amount']">
                <label for="deal-amount">{{ t('deals.amount') }}</label>
                <input
                  id="deal-amount"
                  name="amount"
                  type="number"
                  min="0"
                  step="0.01"
                  data-testid="deal-amount"
                  [(ngModel)]="draft.amount"
                />
                @if (errors()['amount']; as message) {
                  <span class="field-error">{{ message }}</span>
                }
              </div>
              <div class="field">
                <label for="deal-currency">{{ t('deals.currency') }}</label>
                <input
                  id="deal-currency"
                  name="currency"
                  maxlength="3"
                  [(ngModel)]="draft.currency"
                />
              </div>
              <div class="field">
                <label for="deal-stage">{{ t('deals.stage') }}</label>
                <select id="deal-stage" name="stage" [(ngModel)]="draft.stage">
                  @for (stage of stages; track stage) {
                    <option [ngValue]="stage">{{ label('deals.stage', stage) }}</option>
                  }
                </select>
              </div>
              <div class="field">
                <label for="deal-close">{{ t('deals.expectedClose') }}</label>
                <input
                  id="deal-close"
                  name="expectedCloseDate"
                  type="date"
                  [(ngModel)]="draft.expectedCloseDate"
                />
              </div>
              <app-entity-picker
                testId="deal-contact"
                [label]="t('deals.contact')"
                [value]="draft.contactId ?? null"
                [valueLabel]="draft.contactName ?? null"
                [search]="searchContacts"
                (selected)="pickContact(draft, $event)"
              />
              <app-entity-picker
                testId="deal-company"
                [label]="t('deals.company')"
                [value]="draft.companyId ?? null"
                [valueLabel]="draft.companyName ?? null"
                [search]="searchCompanies"
                (selected)="pickCompany(draft, $event)"
              />
            </div>

            <div class="field">
              <label for="deal-notes">{{ t('common.notes') }}</label>
              <textarea id="deal-notes" name="notes" [(ngModel)]="draft.notes"></textarea>
            </div>
          </div>
          <div class="dialog-foot">
            <button type="button" class="btn" (click)="editing.set(null)">
              {{ t('action.cancel') }}
            </button>
            <button
              type="submit"
              class="btn btn-primary"
              data-testid="deal-save"
              [disabled]="saving()"
            >
              {{ t('action.save') }}
            </button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .board {
      display: grid;
      gap: var(--space-4);
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      align-items: start;
    }

    .column {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }

    .column-head {
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding-inline: var(--space-1);
    }

    .deal {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
    }

    .move {
      font: inherit;
      min-height: 34px;
      width: 100%;
      border: 1px solid var(--line-strong);
      border-radius: var(--radius-sm);
      background: var(--surface);
      color: var(--ink);
      padding: 0 var(--space-2);
    }
  `,
})
export class DealsPage {
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly i18n = inject(I18nService);
  protected readonly format = inject(FormatService);
  protected readonly t = this.i18n.t;
  protected readonly label = this.i18n.label;
  protected readonly stages = DEAL_STAGES;

  protected readonly deals = signal<Deal[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly openOnly = signal(true);
  protected readonly editing = signal<Deal | null>(null);
  protected readonly errors = signal<Record<string, string>>({});

  protected readonly visibleStages = computed<readonly DealStage[]>(() =>
    this.openOnly() ? OPEN_DEAL_STAGES : DEAL_STAGES,
  );

  protected readonly byStage = computed(() => {
    const grouped = {} as Record<DealStage, Deal[]>;
    for (const stage of DEAL_STAGES) {
      grouped[stage] = [];
    }
    for (const deal of this.deals()) {
      grouped[deal.stage ?? 'LEAD'].push(deal);
    }
    return grouped;
  });

  /**
   * How many deals exist beyond the ones on the board.
   *
   * <p>The board is not paged: splitting a pipeline across pages would leave columns looking
   * empty when they are not. It fetches as much as the API will serve at once and says plainly
   * when that was not everything.
   */
  protected readonly truncated = computed(() => Math.max(0, this.total() - this.deals().length));

  constructor() {
    void this.load();
  }

  /** Bound as fields so the templates hand the pickers a stable reference. */
  protected readonly searchContacts = async (term: string): Promise<PickerResult> => {
    const found = await this.api.listContacts(term, undefined, { size: 10 });
    return {
      options: found.items.map((contact) => ({
        id: contact.id ?? 0,
        label: contact.displayName ?? '',
        hint: contact.companyName,
      })),
      total: found.total,
    };
  };

  protected readonly searchCompanies = async (term: string): Promise<PickerResult> => {
    const found = await this.api.listCompanies(term, { size: 10 });
    return {
      options: found.items.map((company) => ({
        id: company.id ?? 0,
        label: company.name,
        hint: company.city,
      })),
      total: found.total,
    };
  };

  protected pickContact(draft: Deal, option: PickerOption | null): void {
    draft.contactId = option?.id ?? null;
    draft.contactName = option?.label ?? null;
  }

  protected pickCompany(draft: Deal, option: PickerOption | null): void {
    draft.companyId = option?.id ?? null;
    draft.companyName = option?.label ?? null;
  }

  /**
   * Column totals, one per currency present.
   *
   * <p>A single sum was wrong whenever a stage held more than one currency: 10.000 USD next to
   * 5.000 EUR was displayed as "€15.000,00".
   */
  protected stageTotals(stage: DealStage): { currency: string; amount: number }[] {
    const totals = new Map<string, number>();
    for (const deal of this.byStage()[stage]) {
      const currency = (deal.currency || 'EUR').toUpperCase();
      totals.set(currency, (totals.get(currency) ?? 0) + (deal.amount ?? 0));
    }
    return [...totals.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([currency, amount]) => ({ currency, amount }));
  }

  protected toggleOpenOnly(): void {
    this.openOnly.set(!this.openOnly());
    void this.load();
  }

  protected open(deal?: Deal): void {
    this.errors.set({});
    this.editing.set(
      deal
        ? { ...deal }
        : {
            title: '',
            stage: 'LEAD',
            currency: 'EUR',
            contactId: null,
            companyId: null,
            amount: null,
          },
    );
  }

  protected async move(deal: Deal, event: Event): Promise<void> {
    const stage = (event.target as HTMLSelectElement).value as DealStage;
    if (!deal.id || stage === deal.stage) {
      return;
    }
    try {
      await this.api.moveDeal(deal.id, stage);
      this.toasts.success(this.t('common.saved'));
      await this.load();
    } catch (error) {
      this.toasts.problem(error);
      await this.load();
    }
  }

  protected async save(): Promise<void> {
    const draft = this.editing();
    if (!draft || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.errors.set({});
    try {
      await this.api.saveDeal(draft);
      this.editing.set(null);
      this.toasts.success(this.t('common.saved'));
      await this.load();
    } catch (error) {
      this.errors.set(this.toasts.problem(error).fieldErrors);
    } finally {
      this.saving.set(false);
    }
  }

  protected async remove(deal: Deal): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: this.t('common.confirmTitle'),
      question: this.t('common.deleteQuestion', { name: deal.title }),
      confirmLabel: this.t('action.delete'),
      destructive: true,
    });
    if (!confirmed || !deal.id) {
      return;
    }
    try {
      await this.api.deleteDeal(deal.id);
      this.toasts.success(this.t('common.deleted'));
      await this.load();
    } catch (error) {
      this.toasts.problem(error);
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const found = await this.api.listDeals(this.openOnly(), undefined, undefined, {
        size: MAX_BOARD_SIZE,
      });
      this.deals.set(found.items);
      this.total.set(found.total);
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.loading.set(false);
    }
  }
}
