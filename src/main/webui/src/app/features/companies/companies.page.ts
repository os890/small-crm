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

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { Company } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../shared/confirm.service';

function blankCompany(): Company {
  return { name: '' };
}

/** Company list with an inline create and edit dialog. */
@Component({
  selector: 'app-companies',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="stack">
      <div class="row-between">
        <h1>{{ t('companies.heading') }}</h1>
        <button type="button" class="btn btn-primary" data-testid="new-company" (click)="open()">
          + {{ t('companies.new') }}
        </button>
      </div>

      <div class="row">
        <input
          class="search"
          type="search"
          data-testid="company-search"
          [placeholder]="t('companies.searchPlaceholder')"
          [ngModel]="search()"
          (ngModelChange)="onSearch($event)"
        />
      </div>

      <div class="card table-wrap">
        @if (loading()) {
          <p class="empty-state">{{ t('common.loading') }}</p>
        } @else if (companies().length === 0) {
          <p class="empty-state" data-testid="companies-empty">
            {{ search() ? t('common.noResults') : t('companies.empty') }}
          </p>
        } @else {
          <table class="data">
            <thead>
              <tr>
                <th>{{ t('companies.name') }}</th>
                <th>{{ t('companies.city') }}</th>
                <th>{{ t('contacts.email') }}</th>
                <th>{{ t('contacts.phone') }}</th>
                <th class="actions">{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody data-testid="company-rows">
              @for (company of companies(); track company.id) {
                <tr>
                  <td>{{ company.name }}</td>
                  <td class="muted">{{ company.city }}</td>
                  <td class="muted">{{ company.email }}</td>
                  <td class="muted">{{ company.phone }}</td>
                  <td class="actions">
                    <button type="button" class="btn btn-sm" (click)="open(company)">
                      {{ t('action.edit') }}
                    </button>
                    <button type="button" class="btn btn-sm btn-danger" (click)="remove(company)">
                      {{ t('action.delete') }}
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    </div>

    @if (editing(); as draft) {
      <div class="backdrop">
        <form class="dialog" (ngSubmit)="save()" data-testid="company-dialog">
          <div class="dialog-head">
            <h2>{{ draft.id ? t('companies.edit') : t('companies.new') }}</h2>
          </div>
          <div class="dialog-body">
            <div class="field" [class.invalid]="errors()['name']">
              <label for="company-name">{{ t('companies.name') }} *</label>
              <input
                id="company-name"
                name="name"
                data-testid="company-name"
                required
                [(ngModel)]="draft.name"
              />
              @if (errors()['name']; as message) {
                <span class="field-error">{{ message }}</span>
              }
            </div>

            <div class="field-grid">
              <div class="field">
                <label for="company-email">{{ t('contacts.email') }}</label>
                <input id="company-email" name="email" type="email" [(ngModel)]="draft.email" />
              </div>
              <div class="field">
                <label for="company-phone">{{ t('contacts.phone') }}</label>
                <input id="company-phone" name="phone" [(ngModel)]="draft.phone" />
              </div>
              <div class="field">
                <label for="company-website">{{ t('companies.website') }}</label>
                <input id="company-website" name="website" [(ngModel)]="draft.website" />
              </div>
              <div class="field">
                <label for="company-vat">{{ t('companies.vatId') }}</label>
                <input id="company-vat" name="vatId" [(ngModel)]="draft.vatId" />
              </div>
              <div class="field">
                <label for="company-street">{{ t('companies.street') }}</label>
                <input id="company-street" name="street" [(ngModel)]="draft.street" />
              </div>
              <div class="field">
                <label for="company-postal">{{ t('companies.postalCode') }}</label>
                <input id="company-postal" name="postalCode" [(ngModel)]="draft.postalCode" />
              </div>
              <div class="field">
                <label for="company-city">{{ t('companies.city') }}</label>
                <input id="company-city" name="city" [(ngModel)]="draft.city" />
              </div>
              <div class="field">
                <label for="company-country">{{ t('companies.country') }}</label>
                <input id="company-country" name="country" [(ngModel)]="draft.country" />
              </div>
            </div>

            <div class="field">
              <label for="company-notes">{{ t('common.notes') }}</label>
              <textarea id="company-notes" name="notes" [(ngModel)]="draft.notes"></textarea>
            </div>
          </div>
          <div class="dialog-foot">
            <button type="button" class="btn" (click)="cancel()">{{ t('action.cancel') }}</button>
            <button
              type="submit"
              class="btn btn-primary"
              data-testid="company-save"
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
    .search {
      font: inherit;
      min-height: 42px;
      padding: 0 var(--space-3);
      border: 1px solid var(--line-strong);
      border-radius: var(--radius-sm);
      width: min(340px, 100%);
    }
  `,
})
export class CompaniesPage {
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  protected readonly companies = signal<Company[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly search = signal('');
  protected readonly editing = signal<Company | null>(null);
  protected readonly errors = signal<Record<string, string>>({});

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    void this.load();
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    // Debounced so typing does not fire a request per keystroke.
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => void this.load(), 250);
  }

  protected open(company?: Company): void {
    this.errors.set({});
    this.editing.set(company ? { ...company } : blankCompany());
  }

  protected cancel(): void {
    this.editing.set(null);
  }

  protected async save(): Promise<void> {
    const draft = this.editing();
    if (!draft || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.errors.set({});
    try {
      await this.api.saveCompany(draft);
      this.editing.set(null);
      this.toasts.success(this.t('common.saved'));
      await this.load();
    } catch (error) {
      this.errors.set(this.toasts.problem(error).fieldErrors);
    } finally {
      this.saving.set(false);
    }
  }

  protected async remove(company: Company): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: this.t('common.confirmTitle'),
      question: this.t('common.deleteQuestion', { name: company.name }),
      hint: this.t('companies.deleteHint'),
      confirmLabel: this.t('action.delete'),
      destructive: true,
    });
    if (!confirmed || !company.id) {
      return;
    }
    try {
      await this.api.deleteCompany(company.id);
      this.toasts.success(this.t('common.deleted'));
      await this.load();
    } catch (error) {
      this.toasts.problem(error);
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.companies.set(await this.api.listCompanies(this.search()));
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.loading.set(false);
    }
  }
}
