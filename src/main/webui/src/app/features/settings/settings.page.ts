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
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { FormatService } from '../../core/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { GoogleStatus, SyncReport } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../shared/confirm.service';

/** The signed-in user's own settings; today that means their Google connection. */
@Component({
  selector: 'app-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <h1>{{ t('settings.heading') }}</h1>

      <section class="card card-pad stack-sm" data-testid="google-card">
        <h2>{{ t('settings.google') }}</h2>

        @if (loading()) {
          <p class="muted">{{ t('common.loading') }}</p>
        } @else if (status(); as google) {
          @if (!google.available) {
            <p class="muted" data-testid="google-unavailable">
              {{ t('settings.googleUnavailable') }}
            </p>
            @if (google.unavailableReason) {
              <p class="faint">{{ google.unavailableReason }}</p>
            }
          } @else if (!google.connected) {
            <p class="muted">{{ t('settings.googleExplain') }}</p>
            <div class="row">
              <button
                type="button"
                class="btn btn-primary"
                data-testid="google-connect"
                [disabled]="busy()"
                (click)="connect()"
              >
                {{ t('settings.googleConnect') }}
              </button>
            </div>
          } @else {
            <div class="row-between line">
              <span class="muted">{{ t('settings.googleAccount') }}</span>
              <span data-testid="google-email">{{ google.email }}</span>
            </div>
            @if (google.connectedAt) {
              <div class="row-between line">
                <span class="muted">{{ t('settings.googleSince') }}</span>
                <span class="faint">{{ format.dateTime(google.connectedAt) }}</span>
              </div>
            }

            <table class="data">
              <thead>
                <tr>
                  <th>{{ t('settings.googleResource') }}</th>
                  <th>{{ t('settings.googleLastSync') }}</th>
                  <th>{{ t('common.actions') }}</th>
                </tr>
              </thead>
              <tbody data-testid="google-resources">
                @for (resource of google.resources; track resource.resource) {
                  <tr>
                    <td>{{ label('settings.googleResource', resource.resource) }}</td>
                    <td class="muted">
                      {{
                        resource.lastOkAt
                          ? format.dateTime(resource.lastOkAt)
                          : t('settings.googleNever')
                      }}
                    </td>
                    <td>
                      @if (!resource.permitted) {
                        <span class="badge">{{ t('settings.googleNotPermitted') }}</span>
                      } @else if (resource.lastError) {
                        <span class="badge badge-danger" [attr.title]="resource.lastError">
                          {{ t('settings.googleFailing', { count: resource.failures }) }}
                        </span>
                      } @else {
                        <span class="badge badge-accent">{{ t('settings.googleOk') }}</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>

            <div class="row">
              <button
                type="button"
                class="btn btn-primary"
                data-testid="google-sync"
                [disabled]="busy()"
                (click)="syncNow()"
              >
                {{ busy() ? t('settings.googleSyncing') : t('settings.googleSyncNow') }}
              </button>
              <button
                type="button"
                class="btn btn-danger"
                data-testid="google-disconnect"
                [disabled]="busy()"
                (click)="disconnect()"
              >
                {{ t('settings.googleDisconnect') }}
              </button>
            </div>

            @if (lastRun().length) {
              <ul class="list" data-testid="google-report">
                @for (report of lastRun(); track report.resource) {
                  <li class="item">
                    <span class="grow">
                      {{ label('settings.googleResource', report.resource) }}
                    </span>
                    @if (report.error) {
                      <span class="faint">{{ report.error }}</span>
                    } @else {
                      <span class="faint">
                        {{
                          t('settings.googleCounts', {
                            inbound: report.pulledIn + report.pulledUpdated,
                            outbound: report.pushedNew + report.pushedUpdated,
                            readOnly: report.readOnly,
                          })
                        }}
                      </span>
                    }
                  </li>
                }
              </ul>
            }
          }
        }
      </section>
    </div>
  `,
  styles: `
    .line {
      padding-block: var(--space-1);
      border-bottom: 1px solid var(--line);
    }
  `,
})
export class SettingsPage {
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  protected readonly format = inject(FormatService);
  protected readonly t = this.i18n.t;
  protected readonly label = this.i18n.label;

  protected readonly status = signal<GoogleStatus | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly lastRun = signal<SyncReport[]>([]);

  constructor() {
    // The browser lands back here after Google, with the outcome in the query string; there is
    // nowhere else a redirect from somebody else's site can leave a message.
    const params = this.route.snapshot.queryParamMap;
    const outcome = params.get('google');
    const failure = params.get('googleError');
    if (outcome === 'connected') {
      this.toasts.success(this.t('settings.googleConnected'));
    } else if (outcome === 'cancelled') {
      this.toasts.success(this.t('settings.googleCancelled'));
    } else if (failure) {
      this.toasts.error(this.i18n.errorMessage(failure, this.t('settings.googleFailed')));
    }
    void this.load();
  }

  protected async connect(): Promise<void> {
    this.busy.set(true);
    try {
      const { url } = await this.api.googleConnect();
      // A full navigation rather than a router hop: the next page is Google's.
      window.location.href = url;
    } catch (error) {
      this.toasts.problem(error);
      this.busy.set(false);
    }
  }

  protected async syncNow(): Promise<void> {
    this.busy.set(true);
    try {
      const reports = await this.api.googleSyncNow();
      this.lastRun.set(reports);
      await this.load();
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.busy.set(false);
    }
  }

  protected async disconnect(): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: this.t('common.confirmTitle'),
      question: this.t('settings.googleDisconnectQuestion'),
      hint: this.t('settings.googleDisconnectHint'),
      confirmLabel: this.t('settings.googleDisconnect'),
      destructive: true,
    });
    if (!confirmed) {
      return;
    }
    this.busy.set(true);
    try {
      await this.api.googleDisconnect();
      this.lastRun.set([]);
      this.toasts.success(this.t('settings.googleDisconnected'));
      await this.load();
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.status.set(await this.api.googleStatus());
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.loading.set(false);
    }
  }
}
