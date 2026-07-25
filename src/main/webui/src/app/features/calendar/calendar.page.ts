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
import { Appointment, Contact, Deal } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../shared/confirm.service';
import {
  addMinutes,
  durationMinutes,
  groupByDay,
  isoToDatePart,
  isoToTimePart,
  partsToIso,
  startOfToday,
} from './calendar.util';

interface Draft {
  id?: number;
  title: string;
  date: string;
  from: string;
  to: string;
  location: string;
  notes: string;
  contactId: number | null;
  dealId: number | null;
}

const RANGE_OPTIONS = [7, 30, 90] as const;

/** Default length of a newly created appointment. */
const DEFAULT_LENGTH_MINUTES = 60;

/** Hour a new appointment starts at when the working day is already over. */
const NEXT_MORNING_HOUR = 9;

/**
 * A sensible start for a new appointment: the next full hour, or tomorrow morning when that
 * hour would no longer leave room for a normal length appointment today.
 */
function defaultStart(now = new Date()): Date {
  const start = new Date(now);
  start.setMinutes(0, 0, 0);
  start.setHours(start.getHours() + 1);
  if (start.getHours() >= 23) {
    start.setDate(start.getDate() + 1);
    start.setHours(NEXT_MORNING_HOUR, 0, 0, 0);
  }
  return start;
}

/**
 * The agenda plus the appointment dialog.
 *
 * <p>The dialog checks the chosen slot against the calendar while the user is still typing, so
 * a clash is visible before saving. Saving a clashing slot is refused by the server; the user
 * can then either change the time or confirm the parallel booking explicitly.
 */
@Component({
  selector: 'app-calendar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="stack">
      <div class="row-between">
        <h1>{{ t('calendar.heading') }}</h1>
        <button
          type="button"
          class="btn btn-primary"
          data-testid="new-appointment"
          (click)="open()"
        >
          + {{ t('calendar.new') }}
        </button>
      </div>

      <div class="row">
        @for (option of rangeOptions; track option) {
          <button
            type="button"
            class="btn btn-sm"
            [class.btn-primary]="rangeDays() === option"
            [attr.data-testid]="'range-' + option"
            (click)="setRange(option)"
          >
            {{ t('calendar.rangeDays', { days: option }) }}
          </button>
        }
      </div>

      @if (loading()) {
        <p class="muted">{{ t('common.loading') }}</p>
      } @else if (days().length === 0) {
        <p class="card empty-state" data-testid="calendar-empty">{{ t('calendar.empty') }}</p>
      } @else {
        <div class="stack" data-testid="agenda">
          @for (day of days(); track day.date) {
            <section class="card">
              <header class="day-head">{{ format.dayHeading(day.date) }}</header>
              @for (appointment of day.appointments; track appointment.id) {
                <div class="slot" data-testid="appointment-row">
                  <div class="when">
                    <strong>{{ format.time(appointment.startsAt) }}</strong>
                    <span class="faint">{{ format.time(appointment.endsAt) }}</span>
                  </div>
                  <div class="grow">
                    <div class="title">{{ appointment.title }}</div>
                    <div class="row faint">
                      <span>{{ t('calendar.duration', { minutes: minutes(appointment) }) }}</span>
                      @if (appointment.location) {
                        <span>· {{ appointment.location }}</span>
                      }
                      @if (appointment.contactName) {
                        <span>· {{ appointment.contactName }}</span>
                      }
                    </div>
                    @if (appointment.notes) {
                      <div class="faint">{{ appointment.notes }}</div>
                    }
                  </div>
                  <div class="row">
                    <button type="button" class="btn btn-sm" (click)="open(appointment)">
                      {{ t('action.edit') }}
                    </button>
                    <button
                      type="button"
                      class="btn btn-sm btn-danger"
                      (click)="remove(appointment)"
                    >
                      {{ t('action.delete') }}
                    </button>
                  </div>
                </div>
              }
            </section>
          }
        </div>
      }
    </div>

    @if (draft(); as entry) {
      <div class="backdrop">
        <form class="dialog" (ngSubmit)="save(false)" data-testid="appointment-dialog">
          <div class="dialog-head">
            <h2>{{ entry.id ? t('calendar.edit') : t('calendar.new') }}</h2>
          </div>
          <div class="dialog-body">
            <div class="field" [class.invalid]="errors()['title']">
              <label for="appointment-title">{{ t('calendar.title') }} *</label>
              <input
                id="appointment-title"
                name="title"
                data-testid="appointment-title"
                required
                [(ngModel)]="entry.title"
              />
              @if (errors()['title']; as message) {
                <span class="field-error">{{ message }}</span>
              }
            </div>

            <div class="field-grid">
              <div class="field">
                <label for="appointment-date">{{ t('calendar.date') }} *</label>
                <input
                  id="appointment-date"
                  name="date"
                  type="date"
                  data-testid="appointment-date"
                  required
                  [ngModel]="entry.date"
                  (ngModelChange)="onDateChange($event)"
                />
              </div>
              <div class="field">
                <label for="appointment-from">{{ t('calendar.from') }} *</label>
                <input
                  id="appointment-from"
                  name="from"
                  type="time"
                  data-testid="appointment-from"
                  required
                  [ngModel]="entry.from"
                  (ngModelChange)="onFromChange($event)"
                />
              </div>
              <div class="field" [class.invalid]="endBeforeStart()">
                <label for="appointment-to">{{ t('calendar.to') }} *</label>
                <input
                  id="appointment-to"
                  name="to"
                  type="time"
                  data-testid="appointment-to"
                  required
                  [ngModel]="entry.to"
                  (ngModelChange)="onToChange($event)"
                />
                @if (endBeforeStart()) {
                  <span class="field-error" data-testid="end-before-start">
                    {{ t('calendar.endBeforeStart') }}
                  </span>
                }
              </div>
            </div>

            @if (endBeforeStart()) {
              <!-- No point checking availability for a slot that is not valid yet. -->
            } @else if (conflicts().length) {
              <div class="notice notice-warning" data-testid="conflict-warning">
                <strong>{{ t('calendar.conflictWarning') }}</strong>
                <ul class="conflicts">
                  @for (conflict of conflicts(); track conflict.id) {
                    <li>
                      {{ conflict.title }} —
                      {{ format.date(conflict.startsAt) }}
                      {{ format.time(conflict.startsAt) }}–{{ format.time(conflict.endsAt) }}
                    </li>
                  }
                </ul>
              </div>
            } @else if (checked()) {
              <p class="notice notice-success" data-testid="no-conflict">
                {{ t('calendar.noConflict') }}
              </p>
            }

            <div class="field-grid">
              <div class="field">
                <label for="appointment-location">{{ t('calendar.location') }}</label>
                <input id="appointment-location" name="location" [(ngModel)]="entry.location" />
              </div>
              <div class="field">
                <label for="appointment-contact">{{ t('calendar.contact') }}</label>
                <select
                  id="appointment-contact"
                  name="contactId"
                  data-testid="appointment-contact"
                  [(ngModel)]="entry.contactId"
                >
                  <option [ngValue]="null">{{ t('common.none') }}</option>
                  @for (contact of contacts(); track contact.id) {
                    <option [ngValue]="contact.id">{{ contact.displayName }}</option>
                  }
                </select>
              </div>
              <div class="field">
                <label for="appointment-deal">{{ t('calendar.deal') }}</label>
                <select id="appointment-deal" name="dealId" [(ngModel)]="entry.dealId">
                  <option [ngValue]="null">{{ t('common.none') }}</option>
                  @for (deal of deals(); track deal.id) {
                    <option [ngValue]="deal.id">{{ deal.title }}</option>
                  }
                </select>
              </div>
            </div>

            <div class="field">
              <label for="appointment-notes">{{ t('common.notes') }}</label>
              <textarea id="appointment-notes" name="notes" [(ngModel)]="entry.notes"></textarea>
            </div>

            @if (blocked()) {
              <p class="notice notice-danger" role="alert" data-testid="conflict-blocked">
                {{ t('calendar.conflictBlocked') }}
              </p>
            }
          </div>
          <div class="dialog-foot">
            <button type="button" class="btn" (click)="draft.set(null)">
              {{ t('action.cancel') }}
            </button>
            @if (blocked()) {
              <button
                type="button"
                class="btn"
                data-testid="appointment-save-anyway"
                [disabled]="saving()"
                (click)="save(true)"
              >
                {{ t('action.saveAnyway') }}
              </button>
            }
            <button
              type="submit"
              class="btn btn-primary"
              data-testid="appointment-save"
              [disabled]="saving() || endBeforeStart() || !entry.title"
            >
              {{ t('action.save') }}
            </button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .day-head {
      padding: var(--space-3) var(--space-4);
      border-bottom: 1px solid var(--line);
      background: var(--canvas);
      font-weight: 650;
      border-radius: var(--radius) var(--radius) 0 0;
    }

    .slot {
      display: flex;
      align-items: flex-start;
      gap: var(--space-4);
      padding: var(--space-3) var(--space-4);
      border-bottom: 1px solid var(--line);
    }

    .slot:last-child {
      border-bottom: none;
    }

    .when {
      display: flex;
      flex-direction: column;
      min-width: 62px;
    }

    .title {
      font-weight: 550;
    }

    .conflicts {
      margin: var(--space-2) 0 0;
      padding-inline-start: var(--space-5);
    }
  `,
})
export class CalendarPage {
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly i18n = inject(I18nService);
  protected readonly format = inject(FormatService);
  protected readonly t = this.i18n.t;
  protected readonly rangeOptions = RANGE_OPTIONS;

  protected readonly appointments = signal<Appointment[]>([]);
  protected readonly contacts = signal<Contact[]>([]);
  protected readonly deals = signal<Deal[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly rangeDays = signal<number>(30);
  protected readonly draft = signal<Draft | null>(null);
  protected readonly conflicts = signal<Appointment[]>([]);
  protected readonly checked = signal(false);
  protected readonly blocked = signal(false);
  protected readonly errors = signal<Record<string, string>>({});

  protected readonly days = computed(() => groupByDay(this.appointments()));

  private conflictTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    void this.load();
    void this.loadPickers();
  }

  protected minutes(appointment: Appointment): number {
    return durationMinutes(appointment);
  }

  protected endBeforeStart(): boolean {
    const entry = this.draft();
    return entry ? entry.to <= entry.from : false;
  }

  protected setRange(days: number): void {
    this.rangeDays.set(days);
    void this.load();
  }

  protected open(appointment?: Appointment): void {
    this.errors.set({});
    this.conflicts.set([]);
    this.checked.set(false);
    this.blocked.set(false);

    if (appointment) {
      this.draft.set({
        id: appointment.id,
        title: appointment.title,
        date: isoToDatePart(appointment.startsAt),
        from: isoToTimePart(appointment.startsAt),
        to: isoToTimePart(appointment.endsAt),
        location: appointment.location ?? '',
        notes: appointment.notes ?? '',
        contactId: appointment.contactId ?? null,
        dealId: appointment.dealId ?? null,
      });
    } else {
      const start = defaultStart();
      const from = `${`${start.getHours()}`.padStart(2, '0')}:00`;
      this.draft.set({
        title: '',
        date: isoToDatePart(start.toISOString()),
        from,
        to: addMinutes(from, DEFAULT_LENGTH_MINUTES),
        location: '',
        notes: '',
        contactId: null,
        dealId: null,
      });
    }
    this.scheduleConflictCheck();
  }

  protected onDateChange(date: string): void {
    this.patch({ date });
  }

  protected onFromChange(from: string): void {
    const entry = this.draft();
    // Keep the length of the appointment when the start moves, which is what a user expects.
    const keptLength =
      entry && entry.to > entry.from
        ? minutesBetween(entry.from, entry.to)
        : DEFAULT_LENGTH_MINUTES;
    this.patch({ from, to: addMinutes(from, keptLength) });
  }

  protected onToChange(to: string): void {
    this.patch({ to });
  }

  protected async save(allowConflict: boolean): Promise<void> {
    const entry = this.draft();
    if (!entry || this.saving() || this.endBeforeStart()) {
      return;
    }
    this.saving.set(true);
    this.errors.set({});
    try {
      await this.api.saveAppointment(
        {
          id: entry.id,
          title: entry.title,
          startsAt: partsToIso(entry.date, entry.from),
          endsAt: partsToIso(entry.date, entry.to),
          timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
          location: entry.location || null,
          notes: entry.notes || null,
          contactId: entry.contactId,
          dealId: entry.dealId,
        },
        allowConflict,
      );
      this.draft.set(null);
      this.toasts.success(this.t('common.saved'));
      await this.load();
    } catch (error) {
      const problem = this.toasts.problem(error);
      this.errors.set(problem.fieldErrors);
      if (problem.code === 'APPOINTMENT_CONFLICT') {
        this.conflicts.set(problem.conflicts);
        this.checked.set(true);
        this.blocked.set(true);
      }
    } finally {
      this.saving.set(false);
    }
  }

  protected async remove(appointment: Appointment): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: this.t('common.confirmTitle'),
      question: this.t('common.deleteQuestion', { name: appointment.title }),
      confirmLabel: this.t('action.delete'),
      destructive: true,
    });
    if (!confirmed || !appointment.id) {
      return;
    }
    try {
      await this.api.deleteAppointment(appointment.id);
      this.toasts.success(this.t('common.deleted'));
      await this.load();
    } catch (error) {
      this.toasts.problem(error);
    }
  }

  private patch(change: Partial<Draft>): void {
    const entry = this.draft();
    if (entry) {
      this.draft.set({ ...entry, ...change });
      this.blocked.set(false);
      this.scheduleConflictCheck();
    }
  }

  private scheduleConflictCheck(): void {
    clearTimeout(this.conflictTimer);
    this.conflictTimer = setTimeout(() => void this.checkConflicts(), 300);
  }

  private async checkConflicts(): Promise<void> {
    const entry = this.draft();
    if (!entry || this.endBeforeStart()) {
      this.conflicts.set([]);
      this.checked.set(false);
      return;
    }
    try {
      const found = await this.api.appointmentConflicts(
        partsToIso(entry.date, entry.from),
        partsToIso(entry.date, entry.to),
        entry.id ?? null,
      );
      // The user may have kept typing while the request was in flight.
      if (this.draft() === entry) {
        this.conflicts.set(found);
        this.checked.set(true);
      }
    } catch {
      // The live hint is a convenience; the server still refuses a real clash on save.
      this.checked.set(false);
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    const from = startOfToday();
    const to = new Date(from);
    to.setDate(to.getDate() + this.rangeDays());
    try {
      this.appointments.set(await this.api.listAppointments(from, to));
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadPickers(): Promise<void> {
    try {
      const [contacts, deals] = await Promise.all([
        this.api.listContacts(),
        this.api.listDeals(false),
      ]);
      this.contacts.set(contacts);
      this.deals.set(deals);
    } catch {
      // Pickers stay empty; an appointment can be saved without a link.
    }
  }
}

function minutesBetween(from: string, to: string): number {
  const [fromHours, fromMinutes] = from.split(':').map(Number);
  const [toHours, toMinutes] = to.split(':').map(Number);
  return toHours * 60 + toMinutes - (fromHours * 60 + fromMinutes);
}
