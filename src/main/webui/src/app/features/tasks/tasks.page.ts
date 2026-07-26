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
import { FormatService } from '../../core/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { CrmTask, TASK_PRIORITIES } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../shared/confirm.service';
import {
  EntityPickerComponent,
  PickerOption,
  PickerResult,
} from '../../shared/entity-picker.component';
import { PagerComponent } from '../../shared/pager.component';

/** The to-do list, with a one-click completion checkbox. */
@Component({
  selector: 'app-tasks',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, EntityPickerComponent, PagerComponent],
  template: `
    <div class="stack">
      <div class="row-between">
        <h1>{{ t('tasks.heading') }}</h1>
        <button type="button" class="btn btn-primary" data-testid="new-task" (click)="open()">
          + {{ t('tasks.new') }}
        </button>
      </div>

      <label class="checkbox">
        <input
          type="checkbox"
          data-testid="tasks-open-only"
          [checked]="openOnly()"
          (change)="toggleOpenOnly()"
        />
        <span>{{ t('tasks.openOnly') }}</span>
      </label>

      <div class="card">
        @if (loading()) {
          <p class="empty-state">{{ t('common.loading') }}</p>
        } @else if (tasks().length === 0) {
          <p class="empty-state" data-testid="tasks-empty">{{ t('tasks.empty') }}</p>
        } @else {
          <ul class="list" data-testid="task-rows">
            @for (task of tasks(); track task.id) {
              <li class="item" [class.done]="task.done">
                <label class="checkbox">
                  <input
                    type="checkbox"
                    [attr.data-testid]="'task-done-' + task.id"
                    [attr.aria-label]="task.done ? t('tasks.markOpen') : t('tasks.markDone')"
                    [checked]="task.done"
                    (change)="setDone(task, !task.done)"
                  />
                </label>
                <div class="grow">
                  <div class="title">{{ task.title }}</div>
                  @if (task.description) {
                    <div class="faint">{{ task.description }}</div>
                  }
                  <div class="row meta">
                    @if (task.overdue) {
                      <span class="badge badge-danger">{{ t('tasks.overdue') }}</span>
                    }
                    <span class="faint">
                      {{ task.dueDate ? format.date(task.dueDate) : t('tasks.noDueDate') }}
                    </span>
                    <span class="badge">{{
                      label('tasks.priority', task.priority ?? 'NORMAL')
                    }}</span>
                    @if (task.contactName) {
                      <span class="faint">· {{ task.contactName }}</span>
                    }
                    @if (task.dealTitle) {
                      <span class="faint">· {{ task.dealTitle }}</span>
                    }
                  </div>
                </div>
                <div class="row">
                  <button type="button" class="btn btn-sm" (click)="open(task)">
                    {{ t('action.edit') }}
                  </button>
                  <button type="button" class="btn btn-sm btn-danger" (click)="remove(task)">
                    {{ t('action.delete') }}
                  </button>
                </div>
              </li>
            }
          </ul>
          <app-pager
            [page]="page()"
            [size]="pageSize()"
            [total]="total()"
            [shown]="tasks().length"
            (goTo)="goToPage($event)"
          />
        }
      </div>
    </div>

    @if (editing(); as draft) {
      <div class="backdrop">
        <form class="dialog" (ngSubmit)="save()" data-testid="task-dialog">
          <div class="dialog-head">
            <h2>{{ draft.id ? t('tasks.edit') : t('tasks.new') }}</h2>
          </div>
          <div class="dialog-body">
            <div class="field" [class.invalid]="errors()['title']">
              <label for="task-title">{{ t('tasks.title') }} *</label>
              <input
                id="task-title"
                name="title"
                data-testid="task-title"
                required
                [(ngModel)]="draft.title"
              />
              @if (errors()['title']; as message) {
                <span class="field-error">{{ message }}</span>
              }
            </div>

            <div class="field-grid">
              <div class="field">
                <label for="task-due">{{ t('tasks.dueDate') }}</label>
                <input
                  id="task-due"
                  name="dueDate"
                  type="date"
                  data-testid="task-due"
                  [(ngModel)]="draft.dueDate"
                />
              </div>
              <div class="field">
                <label for="task-priority">{{ t('tasks.priority') }}</label>
                <select id="task-priority" name="priority" [(ngModel)]="draft.priority">
                  @for (priority of priorities; track priority) {
                    <option [ngValue]="priority">{{ label('tasks.priority', priority) }}</option>
                  }
                </select>
              </div>
              <app-entity-picker
                testId="task-contact"
                [label]="t('tasks.contact')"
                [value]="draft.contactId ?? null"
                [valueLabel]="draft.contactName ?? null"
                [search]="searchContacts"
                (selected)="pickContact(draft, $event)"
              />
              <app-entity-picker
                testId="task-deal"
                [label]="t('tasks.deal')"
                [value]="draft.dealId ?? null"
                [valueLabel]="draft.dealTitle ?? null"
                [search]="searchDeals"
                (selected)="pickDeal(draft, $event)"
              />
            </div>

            <div class="field">
              <label for="task-description">{{ t('tasks.description') }}</label>
              <textarea
                id="task-description"
                name="description"
                [(ngModel)]="draft.description"
              ></textarea>
            </div>
          </div>
          <div class="dialog-foot">
            <button type="button" class="btn" (click)="editing.set(null)">
              {{ t('action.cancel') }}
            </button>
            <button
              type="submit"
              class="btn btn-primary"
              data-testid="task-save"
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
    .list {
      list-style: none;
      margin: 0;
      padding: 0;
    }

    .item {
      display: flex;
      align-items: flex-start;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-4);
      border-bottom: 1px solid var(--line);
    }

    .item:last-child {
      border-bottom: none;
    }

    .item.done .title {
      text-decoration: line-through;
      color: var(--ink-faint);
    }

    .title {
      font-weight: 550;
    }

    .meta {
      gap: var(--space-2);
      margin-top: var(--space-1);
    }
  `,
})
export class TasksPage {
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly i18n = inject(I18nService);
  protected readonly format = inject(FormatService);
  protected readonly t = this.i18n.t;
  protected readonly label = this.i18n.label;
  protected readonly priorities = TASK_PRIORITIES;

  protected readonly tasks = signal<CrmTask[]>([]);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly pageSize = signal(0);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly openOnly = signal(true);
  protected readonly editing = signal<CrmTask | null>(null);
  protected readonly errors = signal<Record<string, string>>({});

  constructor() {
    void this.load();
  }

  protected goToPage(page: number): void {
    this.page.set(page);
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

  protected readonly searchDeals = async (term: string): Promise<PickerResult> => {
    // Deals have no search parameter of their own; the first page of open deals is what the
    // field offers, and a to-do can always be saved without one.
    const found = await this.api.listDeals(false, undefined, undefined, { size: 20 });
    const matching = found.items.filter((deal) =>
      deal.title.toLowerCase().includes(term.toLowerCase()),
    );
    return {
      options: matching.map((deal) => ({
        id: deal.id ?? 0,
        label: deal.title,
        hint: deal.contactName,
      })),
      total: term ? matching.length : found.total,
    };
  };

  protected pickContact(draft: CrmTask, option: PickerOption | null): void {
    draft.contactId = option?.id ?? null;
    draft.contactName = option?.label ?? null;
  }

  protected pickDeal(draft: CrmTask, option: PickerOption | null): void {
    draft.dealId = option?.id ?? null;
    draft.dealTitle = option?.label ?? null;
  }

  protected toggleOpenOnly(): void {
    this.openOnly.set(!this.openOnly());
    this.page.set(0);
    void this.load();
  }

  protected open(task?: CrmTask): void {
    this.errors.set({});
    this.editing.set(
      task
        ? { ...task }
        : { title: '', done: false, priority: 'NORMAL', contactId: null, dealId: null },
    );
  }

  protected async setDone(task: CrmTask, done: boolean): Promise<void> {
    if (!task.id) {
      return;
    }
    try {
      await this.api.setTaskDone(task.id, done);
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
      await this.api.saveTask(draft);
      this.editing.set(null);
      this.toasts.success(this.t('common.saved'));
      await this.load();
    } catch (error) {
      this.errors.set(this.toasts.problem(error).fieldErrors);
    } finally {
      this.saving.set(false);
    }
  }

  protected async remove(task: CrmTask): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: this.t('common.confirmTitle'),
      question: this.t('common.deleteQuestion', { name: task.title }),
      confirmLabel: this.t('action.delete'),
      destructive: true,
    });
    if (!confirmed || !task.id) {
      return;
    }
    try {
      await this.api.deleteTask(task.id);
      this.toasts.success(this.t('common.deleted'));
      await this.load();
    } catch (error) {
      this.toasts.problem(error);
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const found = await this.api.listTasks(this.openOnly(), undefined, undefined, {
        page: this.page(),
      });
      this.tasks.set(found.items);
      this.total.set(found.total);
      this.pageSize.set(found.size);
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.loading.set(false);
    }
  }
}
