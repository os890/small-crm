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
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { FormatService } from '../../core/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { Dashboard } from '../../core/models';
import { ToastService } from '../../core/toast.service';

/** The start page: counts, what is due, and what is coming up. */
@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="stack">
      <h1>{{ t('dashboard.greeting', { name: auth.displayName() }) }}</h1>

      @if (loading()) {
        <p class="muted">{{ t('common.loading') }}</p>
      } @else if (data(); as summary) {
        <div class="tiles">
          <a class="card card-pad tile" routerLink="/contacts">
            <span class="tile-value" data-testid="tile-contacts">{{ summary.contactCount }}</span>
            <span class="muted">{{ t('dashboard.contacts') }}</span>
          </a>
          <a class="card card-pad tile" routerLink="/companies">
            <span class="tile-value">{{ summary.companyCount }}</span>
            <span class="muted">{{ t('dashboard.companies') }}</span>
          </a>
          <a class="card card-pad tile" routerLink="/deals">
            <span class="tile-value">{{ summary.openDealCount }}</span>
            <span class="muted">{{ t('dashboard.openDeals') }}</span>
          </a>
          <a class="card card-pad tile" routerLink="/deals">
            <span class="tile-value">{{ format.money(summary.openDealValue, 'EUR') }}</span>
            <span class="muted">{{ t('dashboard.openValue') }}</span>
          </a>
        </div>

        @if (allClear()) {
          <p class="notice notice-success" data-testid="all-clear">
            {{ t('dashboard.allClear') }}
          </p>
        }

        <div class="panels">
          @if (summary.overdueTasks.length) {
            <section class="card card-pad stack-sm" data-testid="panel-overdue">
              <h2>{{ t('dashboard.overdue') }}</h2>
              @for (task of summary.overdueTasks; track task.id) {
                <div class="row-between line">
                  <a routerLink="/tasks" class="grow truncate">{{ task.title }}</a>
                  <span class="badge badge-danger">{{ format.date(task.dueDate) }}</span>
                </div>
              }
            </section>
          }

          @if (summary.tasksDueToday.length) {
            <section class="card card-pad stack-sm" data-testid="panel-today">
              <h2>{{ t('dashboard.dueToday') }}</h2>
              @for (task of summary.tasksDueToday; track task.id) {
                <div class="row-between line">
                  <a routerLink="/tasks" class="grow truncate">{{ task.title }}</a>
                  <span class="badge badge-warning">
                    {{ label('tasks.priority', task.priority ?? 'NORMAL') }}
                  </span>
                </div>
              }
            </section>
          }

          <section class="card card-pad stack-sm" data-testid="panel-upcoming">
            <h2>{{ t('dashboard.upcoming') }}</h2>
            @if (summary.upcomingAppointments.length) {
              @for (appointment of summary.upcomingAppointments; track appointment.id) {
                <div class="row-between line">
                  <a routerLink="/calendar" class="grow truncate">{{ appointment.title }}</a>
                  <span class="faint">
                    {{ format.date(appointment.startsAt) }} {{ format.time(appointment.startsAt) }}
                  </span>
                </div>
              }
            } @else {
              <p class="faint">{{ t('calendar.empty') }}</p>
            }
          </section>

          <section class="card card-pad stack-sm" data-testid="panel-recent">
            <h2>{{ t('dashboard.recent') }}</h2>
            @if (summary.recentInteractions.length) {
              @for (interaction of summary.recentInteractions; track interaction.id) {
                <div class="row-between line">
                  <span class="grow truncate">
                    {{ interaction.subject }}
                    <span class="faint">— {{ interaction.contactName }}</span>
                  </span>
                  <span class="faint">{{ format.date(interaction.occurredAt) }}</span>
                </div>
              }
            } @else {
              <p class="faint">{{ t('common.nothingHere') }}</p>
            }
          </section>
        </div>
      }
    </div>
  `,
  styles: `
    .tiles {
      display: grid;
      gap: var(--space-4);
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    }

    .tile {
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
      text-decoration: none;
      color: inherit;
    }

    .tile:hover {
      border-color: var(--accent);
    }

    .tile-value {
      font-size: 1.75rem;
      font-weight: 700;
      line-height: 1.1;
    }

    .panels {
      display: grid;
      gap: var(--space-4);
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      align-items: start;
    }

    .line {
      padding-block: var(--space-1);
      border-bottom: 1px solid var(--line);
    }

    .line:last-child {
      border-bottom: none;
    }
  `,
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly format = inject(FormatService);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  protected readonly label = this.i18n.label;

  protected readonly data = signal<Dashboard | null>(null);
  protected readonly loading = signal(true);

  protected readonly allClear = computed(() => {
    const summary = this.data();
    return (
      summary !== null && summary.overdueTasks.length === 0 && summary.tasksDueToday.length === 0
    );
  });

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.data.set(await this.api.dashboard());
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.loading.set(false);
    }
  }
}
