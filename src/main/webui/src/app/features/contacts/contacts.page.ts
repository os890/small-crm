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
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { Contact } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../shared/confirm.service';
import {
  EntityPickerComponent,
  PickerOption,
  PickerResult,
} from '../../shared/entity-picker.component';
import { PagerComponent } from '../../shared/pager.component';
import { splitTags } from './contacts.util';

/** Contact list with search and an inline create and edit dialog. */
@Component({
  selector: 'app-contacts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, EntityPickerComponent, PagerComponent],
  template: `
    <div class="stack">
      <div class="row-between">
        <h1>{{ t('contacts.heading') }}</h1>
        <button type="button" class="btn btn-primary" data-testid="new-contact" (click)="open()">
          + {{ t('contacts.new') }}
        </button>
      </div>

      <div class="row">
        <input
          class="search"
          type="search"
          data-testid="contact-search"
          [placeholder]="t('contacts.searchPlaceholder')"
          [ngModel]="search()"
          (ngModelChange)="onSearch($event)"
        />
      </div>

      <div class="card table-wrap">
        @if (loading()) {
          <p class="empty-state">{{ t('common.loading') }}</p>
        } @else if (contacts().length === 0) {
          <p class="empty-state" data-testid="contacts-empty">
            {{ search() ? t('common.noResults') : t('contacts.empty') }}
          </p>
        } @else {
          <table class="data">
            <thead>
              <tr>
                <th>{{ t('contacts.lastName') }}</th>
                <th>{{ t('contacts.company') }}</th>
                <th>{{ t('contacts.email') }}</th>
                <th>{{ t('contacts.phone') }}</th>
                <th>{{ t('contacts.tags') }}</th>
                <th class="actions">{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody data-testid="contact-rows">
              @for (contact of contacts(); track contact.id) {
                <tr>
                  <td>
                    <a [routerLink]="['/contacts', contact.id]">{{ contact.displayName }}</a>
                    @if (contact.position) {
                      <div class="faint">{{ contact.position }}</div>
                    }
                  </td>
                  <td class="muted">{{ contact.companyName }}</td>
                  <td class="muted">{{ contact.email }}</td>
                  <td class="muted">{{ contact.phone || contact.mobile }}</td>
                  <td>
                    @for (tag of contact.tags ?? []; track tag) {
                      <span class="badge badge-accent">{{ tag }}</span>
                    }
                  </td>
                  <td class="actions">
                    <button
                      type="button"
                      class="btn btn-sm"
                      [attr.data-testid]="'contact-edit-' + contact.id"
                      (click)="open(contact)"
                    >
                      {{ t('action.edit') }}
                    </button>
                    <button type="button" class="btn btn-sm btn-danger" (click)="remove(contact)">
                      {{ t('action.delete') }}
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
          <app-pager
            [page]="page()"
            [size]="pageSize()"
            [total]="total()"
            [shown]="contacts().length"
            (goTo)="goToPage($event)"
          />
        }
      </div>
    </div>

    @if (editing(); as draft) {
      <div class="backdrop">
        <form class="dialog" (ngSubmit)="save()" data-testid="contact-dialog">
          <div class="dialog-head">
            <h2>{{ draft.id ? t('contacts.edit') : t('contacts.new') }}</h2>
          </div>
          <div class="dialog-body">
            <div class="field-grid">
              <div class="field" [class.invalid]="errors()['firstName']">
                <label for="first-name">{{ t('contacts.firstName') }} *</label>
                <input
                  id="first-name"
                  name="firstName"
                  data-testid="contact-first-name"
                  required
                  [(ngModel)]="draft.firstName"
                />
                @if (errors()['firstName']; as message) {
                  <span class="field-error">{{ message }}</span>
                }
              </div>
              <div class="field" [class.invalid]="errors()['lastName']">
                <label for="last-name">{{ t('contacts.lastName') }} *</label>
                <input
                  id="last-name"
                  name="lastName"
                  data-testid="contact-last-name"
                  required
                  [(ngModel)]="draft.lastName"
                />
                @if (errors()['lastName']; as message) {
                  <span class="field-error">{{ message }}</span>
                }
              </div>
              <div class="field" [class.invalid]="errors()['email']">
                <label for="contact-email">{{ t('contacts.email') }}</label>
                <input
                  id="contact-email"
                  name="email"
                  type="email"
                  data-testid="contact-email"
                  [(ngModel)]="draft.email"
                />
                @if (errors()['email']; as message) {
                  <span class="field-error">{{ message }}</span>
                }
              </div>
              <div class="field">
                <label for="contact-phone">{{ t('contacts.phone') }}</label>
                <input id="contact-phone" name="phone" [(ngModel)]="draft.phone" />
              </div>
              <div class="field">
                <label for="contact-mobile">{{ t('contacts.mobile') }}</label>
                <input id="contact-mobile" name="mobile" [(ngModel)]="draft.mobile" />
              </div>
              <div class="field">
                <label for="contact-position">{{ t('contacts.position') }}</label>
                <input id="contact-position" name="position" [(ngModel)]="draft.position" />
              </div>
              <app-entity-picker
                testId="contact-company"
                [label]="t('contacts.company')"
                [value]="draft.companyId ?? null"
                [valueLabel]="draft.companyName ?? null"
                [search]="searchCompanies"
                (selected)="pickCompany(draft, $event)"
              />
              <div class="field">
                <label for="contact-tags">{{ t('contacts.tags') }}</label>
                <input
                  id="contact-tags"
                  name="tags"
                  data-testid="contact-tags"
                  [ngModel]="tagText"
                  (ngModelChange)="tagText = $event"
                />
                <span class="field-hint">{{ t('contacts.tagsHint') }}</span>
              </div>
            </div>

            <div class="field">
              <label for="contact-notes">{{ t('common.notes') }}</label>
              <textarea id="contact-notes" name="notes" [(ngModel)]="draft.notes"></textarea>
            </div>
          </div>
          <div class="dialog-foot">
            <button type="button" class="btn" (click)="cancel()">{{ t('action.cancel') }}</button>
            <button
              type="submit"
              class="btn btn-primary"
              data-testid="contact-save"
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

    .badge + .badge {
      margin-inline-start: var(--space-1);
    }
  `,
})
export class ContactsPage {
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  protected readonly contacts = signal<Contact[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly pageSize = signal(0);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly search = signal('');
  protected readonly editing = signal<Contact | null>(null);
  protected readonly errors = signal<Record<string, string>>({});
  protected tagText = '';

  private searchTimer: ReturnType<typeof setTimeout> | undefined;
  /**
   * Counts the searches issued, so a slower earlier response cannot overwrite a newer one.
   * Typing "mü" then "ller" otherwise leaves the table showing results for "mü".
   */
  private searchSequence = 0;

  constructor() {
    void this.load();
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    // Back to the first page: page 4 of the previous search says nothing about this one.
    this.page.set(0);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => void this.load(), 250);
  }

  protected goToPage(page: number): void {
    this.page.set(page);
    void this.load();
  }

  /** Bound as a field so the template hands the picker a stable reference. */
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

  protected pickCompany(draft: Contact, option: PickerOption | null): void {
    draft.companyId = option?.id ?? null;
    draft.companyName = option?.label ?? null;
  }

  protected open(contact?: Contact): void {
    this.errors.set({});
    this.tagText = (contact?.tags ?? []).join(', ');
    this.editing.set(
      contact ? { ...contact } : { firstName: '', lastName: '', companyId: null, tags: [] },
    );
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
      await this.api.saveContact({ ...draft, tags: splitTags(this.tagText) });
      this.editing.set(null);
      this.toasts.success(this.t('common.saved'));
      await this.load();
    } catch (error) {
      this.errors.set(this.toasts.problem(error).fieldErrors);
    } finally {
      this.saving.set(false);
    }
  }

  protected async remove(contact: Contact): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: this.t('common.confirmTitle'),
      question: this.t('common.deleteQuestion', { name: contact.displayName ?? '' }),
      confirmLabel: this.t('action.delete'),
      destructive: true,
    });
    if (!confirmed || !contact.id) {
      return;
    }
    try {
      await this.api.deleteContact(contact.id);
      this.toasts.success(this.t('common.deleted'));
      await this.load();
    } catch (error) {
      this.toasts.problem(error);
    }
  }

  private async load(): Promise<void> {
    const sequence = ++this.searchSequence;
    this.loading.set(true);
    try {
      const result = await this.api.listContacts(this.search(), undefined, { page: this.page() });
      if (sequence === this.searchSequence) {
        this.contacts.set(result.items);
        this.total.set(result.total);
        this.pageSize.set(result.size);
      }
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      if (sequence === this.searchSequence) {
        this.loading.set(false);
      }
    }
  }
}
