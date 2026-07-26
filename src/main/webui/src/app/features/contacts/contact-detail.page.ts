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

import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { FormatService } from '../../core/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import {
  Contact,
  CrmTask,
  Deal,
  INTERACTION_TYPES,
  Interaction,
  InteractionType,
} from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../shared/confirm.service';

function nowLocalInput(): string {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 16);
}

/** One contact with everything attached to it: activity, deals and open to-dos. */
@Component({
  selector: 'app-contact-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="stack">
      <a routerLink="/contacts" class="faint">&larr; {{ t('contacts.heading') }}</a>

      @if (contact(); as person) {
        <div class="row-between">
          <div>
            <h1 data-testid="contact-name">{{ person.displayName }}</h1>
            <p class="muted">
              {{ person.position }}
              @if (person.companyName) {
                <span>· {{ person.companyName }}</span>
              }
            </p>
          </div>
        </div>

        <div class="columns">
          <section class="card card-pad stack-sm">
            <h2>{{ t('contacts.detail') }}</h2>
            @if (person.email) {
              <div class="row-between line">
                <span class="muted">{{ t('contacts.email') }}</span>
                <a href="mailto:{{ person.email }}">{{ person.email }}</a>
              </div>
            }
            @if (person.phone) {
              <div class="row-between line">
                <span class="muted">{{ t('contacts.phone') }}</span>
                <span>{{ person.phone }}</span>
              </div>
            }
            @if (person.mobile) {
              <div class="row-between line">
                <span class="muted">{{ t('contacts.mobile') }}</span>
                <span>{{ person.mobile }}</span>
              </div>
            }
            @if ((person.tags ?? []).length) {
              <div class="row line">
                @for (tag of person.tags ?? []; track tag) {
                  <span class="badge badge-accent">{{ tag }}</span>
                }
              </div>
            }
            @if (person.notes) {
              <p class="muted">{{ person.notes }}</p>
            }
          </section>

          <section class="card card-pad stack-sm">
            <div class="row-between">
              <h2>{{ t('contacts.history') }}</h2>
              <button
                type="button"
                class="btn btn-sm btn-primary"
                data-testid="new-interaction"
                (click)="startInteraction()"
              >
                + {{ t('action.new') }}
              </button>
            </div>

            @if (draft(); as entry) {
              <form class="stack-sm log-form" (ngSubmit)="saveInteraction()">
                <div class="field-grid">
                  <div class="field">
                    <label for="log-type">{{ t('contacts.history') }}</label>
                    <select id="log-type" name="type" [(ngModel)]="entry.type">
                      @for (type of types; track type) {
                        <option [ngValue]="type">{{ typeLabel(type) }}</option>
                      }
                    </select>
                  </div>
                  <div class="field">
                    <label for="log-when">{{ t('calendar.date') }}</label>
                    <input
                      id="log-when"
                      name="occurredAt"
                      type="datetime-local"
                      data-testid="interaction-when"
                      [(ngModel)]="occurredAtLocal"
                    />
                  </div>
                </div>
                <div class="field">
                  <label for="log-subject">{{ t('calendar.title') }} *</label>
                  <input
                    id="log-subject"
                    name="subject"
                    data-testid="interaction-subject"
                    required
                    [(ngModel)]="entry.subject"
                  />
                </div>
                <div class="field">
                  <label for="log-notes">{{ t('common.notes') }}</label>
                  <textarea id="log-notes" name="notes" [(ngModel)]="entry.notes"></textarea>
                </div>
                <div class="row">
                  <button
                    type="submit"
                    class="btn btn-primary btn-sm"
                    data-testid="interaction-save"
                    [disabled]="!entry.subject || savingInteraction()"
                  >
                    {{ t('action.save') }}
                  </button>
                  <button type="button" class="btn btn-sm" (click)="draft.set(null)">
                    {{ t('action.cancel') }}
                  </button>
                </div>
              </form>
            }

            @if (interactions().length) {
              @for (entry of interactions(); track entry.id) {
                <div class="row-between line" data-testid="interaction-row">
                  <span class="grow">
                    <span class="badge">{{ typeLabel(entry.type) }}</span>
                    {{ entry.subject }}
                    @if (entry.notes) {
                      <div class="faint">{{ entry.notes }}</div>
                    }
                  </span>
                  <span class="faint">{{ format.dateTime(entry.occurredAt) }}</span>
                  <button
                    type="button"
                    class="btn btn-sm btn-quiet"
                    [attr.aria-label]="t('action.delete')"
                    (click)="removeInteraction(entry)"
                  >
                    &times;
                  </button>
                </div>
              }
            } @else {
              <p class="faint">{{ t('common.nothingHere') }}</p>
            }
          </section>

          <section class="card card-pad stack-sm">
            <h2>{{ t('contacts.openDeals') }}</h2>
            @if (deals().length) {
              @for (deal of deals(); track deal.id) {
                <div class="row-between line">
                  <a routerLink="/deals" class="grow truncate">{{ deal.title }}</a>
                  <span class="badge badge-accent">{{ label('deals.stage', deal.stage) }}</span>
                  <span class="faint">{{ format.money(deal.amount, deal.currency) }}</span>
                </div>
              }
            } @else {
              <p class="faint">{{ t('common.nothingHere') }}</p>
            }
          </section>

          <section class="card card-pad stack-sm">
            <h2>{{ t('contacts.openTasks') }}</h2>
            @if (tasks().length) {
              @for (task of tasks(); track task.id) {
                <div class="row-between line">
                  <a routerLink="/tasks" class="grow truncate">{{ task.title }}</a>
                  <span class="faint">
                    {{ task.dueDate ? format.date(task.dueDate) : t('tasks.noDueDate') }}
                  </span>
                </div>
              }
            } @else {
              <p class="faint">{{ t('common.nothingHere') }}</p>
            }
          </section>
        </div>
      } @else if (!loading()) {
        <p class="notice notice-danger">{{ t('error.notFound') }}</p>
      }
    </div>
  `,
  styles: `
    .columns {
      display: grid;
      gap: var(--space-4);
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      align-items: start;
    }

    .line {
      padding-block: var(--space-1);
      border-bottom: 1px solid var(--line);
    }

    .line:last-child {
      border-bottom: none;
    }

    .log-form {
      padding: var(--space-3);
      background: var(--canvas);
      border-radius: var(--radius-sm);
    }
  `,
})
export class ContactDetailPage {
  /** Bound from the `:id` route segment by `withComponentInputBinding`. */
  readonly id = input.required<string>();

  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly i18n = inject(I18nService);
  protected readonly format = inject(FormatService);
  protected readonly t = this.i18n.t;
  protected readonly label = this.i18n.label;
  protected readonly types = INTERACTION_TYPES;

  protected readonly contact = signal<Contact | null>(null);
  protected readonly interactions = signal<Interaction[]>([]);
  protected readonly deals = signal<Deal[]>([]);
  protected readonly tasks = signal<CrmTask[]>([]);
  protected readonly loading = signal(true);
  protected readonly draft = signal<Interaction | null>(null);
  protected readonly savingInteraction = signal(false);
  protected occurredAtLocal = nowLocalInput();

  constructor() {
    // An effect rather than a one-off call: the component is reused when the route changes to
    // another contact, and a constructor-only load would leave the previous one on screen.
    effect(() => {
      const id = this.id();
      queueMicrotask(() => void this.load(id));
    });
  }

  protected typeLabel(type: InteractionType): string {
    // Interaction types are short and identical in both languages, so they are shown as is
    // rather than carrying four extra keys per language.
    return type.charAt(0) + type.slice(1).toLowerCase();
  }

  protected startInteraction(): void {
    const contactId = this.contact()?.id;
    if (!contactId) {
      return;
    }
    this.occurredAtLocal = nowLocalInput();
    this.draft.set({
      type: 'CALL',
      subject: '',
      occurredAt: new Date().toISOString(),
      contactId,
    });
  }

  protected async saveInteraction(): Promise<void> {
    const entry = this.draft();
    if (!entry || this.savingInteraction()) {
      // Every other form in the application guards like this; without it a double click logs
      // the same activity twice.
      return;
    }
    this.savingInteraction.set(true);
    try {
      await this.api.saveInteraction({
        ...entry,
        occurredAt: new Date(this.occurredAtLocal).toISOString(),
      });
      this.draft.set(null);
      this.toasts.success(this.t('common.saved'));
      await this.loadInteractions();
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.savingInteraction.set(false);
    }
  }

  protected async removeInteraction(entry: Interaction): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: this.t('common.confirmTitle'),
      question: this.t('common.deleteQuestion', { name: entry.subject }),
      confirmLabel: this.t('action.delete'),
      destructive: true,
    });
    if (!confirmed || !entry.id) {
      return;
    }
    try {
      await this.api.deleteInteraction(entry.id);
      await this.loadInteractions();
    } catch (error) {
      this.toasts.problem(error);
    }
  }

  private async load(routeId: string): Promise<void> {
    const contactId = Number(routeId);
    if (!Number.isFinite(contactId)) {
      this.loading.set(false);
      return;
    }
    try {
      const [contact, interactions, deals, tasks] = await Promise.all([
        this.api.getContact(contactId),
        this.api.listInteractions(contactId),
        // Filtered by the server. This used to fetch every deal in the installation and sift
        // through them here, which is both the slowest request on the page and wrong as soon as
        // the deals of this contact fall outside the first page.
        this.api.listDeals(false, undefined, contactId),
        this.api.listTasks(true, contactId),
      ]);
      this.contact.set(contact);
      this.interactions.set(interactions.items);
      this.deals.set(deals.items);
      this.tasks.set(tasks.items);
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadInteractions(): Promise<void> {
    const contactId = this.contact()?.id;
    if (contactId) {
      this.interactions.set((await this.api.listInteractions(contactId)).items);
    }
  }
}
