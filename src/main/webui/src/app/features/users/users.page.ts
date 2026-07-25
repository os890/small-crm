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
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { User } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../shared/confirm.service';

interface UserDraft {
  id?: number;
  username: string;
  password: string;
  fullName: string;
  email: string;
  admin: boolean;
  active: boolean;
}

/** Account administration, reachable only for administrators. */
@Component({
  selector: 'app-users',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="stack">
      <div class="row-between">
        <h1>{{ t('users.heading') }}</h1>
        <button type="button" class="btn btn-primary" data-testid="new-user" (click)="open()">
          + {{ t('users.new') }}
        </button>
      </div>

      <div class="card table-wrap">
        @if (loading()) {
          <p class="empty-state">{{ t('common.loading') }}</p>
        } @else {
          <table class="data">
            <thead>
              <tr>
                <th>{{ t('users.username') }}</th>
                <th>{{ t('users.fullName') }}</th>
                <th>{{ t('users.email') }}</th>
                <th>{{ t('users.admin') }}</th>
                <th>{{ t('users.active') }}</th>
                <th class="actions">{{ t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody data-testid="user-rows">
              @for (user of users(); track user.id) {
                <tr>
                  <td>
                    {{ user.username }}
                    @if (isSelf(user)) {
                      <span class="badge">{{ t('users.you') }}</span>
                    }
                    @if (user.mustChangePassword) {
                      <span class="badge badge-warning">{{ t('users.mustChange') }}</span>
                    }
                  </td>
                  <td class="muted">{{ user.fullName }}</td>
                  <td class="muted">{{ user.email }}</td>
                  <td>{{ user.admin ? t('common.yes') : t('common.no') }}</td>
                  <td>
                    <span
                      class="badge"
                      [class.badge-success]="user.active"
                      [class.badge-danger]="!user.active"
                    >
                      {{ user.active ? t('common.yes') : t('common.no') }}
                    </span>
                  </td>
                  <td class="actions">
                    <button type="button" class="btn btn-sm" (click)="open(user)">
                      {{ t('action.edit') }}
                    </button>
                    <button type="button" class="btn btn-sm" (click)="startReset(user)">
                      {{ t('users.resetPassword') }}
                    </button>
                    <button
                      type="button"
                      class="btn btn-sm btn-danger"
                      [disabled]="isSelf(user)"
                      (click)="remove(user)"
                    >
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
        <form class="dialog" (ngSubmit)="save()" data-testid="user-dialog">
          <div class="dialog-head">
            <h2>{{ draft.id ? t('users.edit') : t('users.new') }}</h2>
          </div>
          <div class="dialog-body">
            @if (!draft.id) {
              <div class="field" [class.invalid]="errors()['username']">
                <label for="user-username">{{ t('users.username') }} *</label>
                <input
                  id="user-username"
                  name="username"
                  data-testid="user-username"
                  required
                  [(ngModel)]="draft.username"
                />
                @if (errors()['username']; as message) {
                  <span class="field-error">{{ message }}</span>
                }
              </div>
              <div class="field" [class.invalid]="errors()['password']">
                <label for="user-password">{{ t('users.password') }} *</label>
                <input
                  id="user-password"
                  name="password"
                  type="password"
                  data-testid="user-password"
                  autocomplete="new-password"
                  required
                  [(ngModel)]="draft.password"
                />
                <span class="field-hint">{{ t('users.passwordHint') }}</span>
                @if (errors()['password']; as message) {
                  <span class="field-error">{{ message }}</span>
                }
              </div>
            }

            <div class="field-grid">
              <div class="field">
                <label for="user-fullname">{{ t('users.fullName') }}</label>
                <input id="user-fullname" name="fullName" [(ngModel)]="draft.fullName" />
              </div>
              <div class="field" [class.invalid]="errors()['email']">
                <label for="user-email">{{ t('users.email') }}</label>
                <input id="user-email" name="email" type="email" [(ngModel)]="draft.email" />
                @if (errors()['email']; as message) {
                  <span class="field-error">{{ message }}</span>
                }
              </div>
            </div>

            <label class="checkbox">
              <input
                type="checkbox"
                name="admin"
                data-testid="user-admin"
                [(ngModel)]="draft.admin"
              />
              <span>{{ t('users.admin') }}</span>
            </label>

            @if (draft.id) {
              <label class="checkbox">
                <input type="checkbox" name="active" [(ngModel)]="draft.active" />
                <span>{{ t('users.active') }}</span>
              </label>
            }
          </div>
          <div class="dialog-foot">
            <button type="button" class="btn" (click)="editing.set(null)">
              {{ t('action.cancel') }}
            </button>
            <button
              type="submit"
              class="btn btn-primary"
              data-testid="user-save"
              [disabled]="saving()"
            >
              {{ t('action.save') }}
            </button>
          </div>
        </form>
      </div>
    }

    @if (resetting(); as target) {
      <div class="backdrop">
        <form class="dialog dialog-narrow" (ngSubmit)="applyReset()" data-testid="reset-dialog">
          <div class="dialog-head">
            <h2>{{ t('users.resetPasswordFor', { name: target.username }) }}</h2>
          </div>
          <div class="dialog-body">
            <div class="field" [class.invalid]="errors()['newPassword']">
              <label for="reset-password">{{ t('password.new') }} *</label>
              <input
                id="reset-password"
                name="newPassword"
                type="password"
                data-testid="reset-password"
                autocomplete="new-password"
                minlength="8"
                required
                [(ngModel)]="newPassword"
              />
              <span class="field-hint">{{ t('users.passwordHint') }}</span>
              @if (errors()['newPassword']; as message) {
                <span class="field-error">{{ message }}</span>
              }
            </div>
          </div>
          <div class="dialog-foot">
            <button type="button" class="btn" (click)="resetting.set(null)">
              {{ t('action.cancel') }}
            </button>
            <button
              type="submit"
              class="btn btn-primary"
              data-testid="reset-save"
              [disabled]="saving() || newPassword.length < 8"
            >
              {{ t('action.save') }}
            </button>
          </div>
        </form>
      </div>
    }
  `,
  styles: `
    .badge + .badge {
      margin-inline-start: var(--space-1);
    }
  `,
})
export class UsersPage {
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  protected readonly users = signal<User[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly editing = signal<UserDraft | null>(null);
  protected readonly resetting = signal<User | null>(null);
  protected readonly errors = signal<Record<string, string>>({});
  protected newPassword = '';

  constructor() {
    void this.load();
  }

  protected isSelf(user: User): boolean {
    return this.auth.user()?.id === user.id;
  }

  protected open(user?: User): void {
    this.errors.set({});
    this.editing.set({
      id: user?.id,
      username: user?.username ?? '',
      password: '',
      fullName: user?.fullName ?? '',
      email: user?.email ?? '',
      admin: user?.admin ?? false,
      active: user?.active ?? true,
    });
  }

  protected startReset(user: User): void {
    this.errors.set({});
    this.newPassword = '';
    this.resetting.set(user);
  }

  protected async save(): Promise<void> {
    const draft = this.editing();
    if (!draft || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.errors.set({});
    try {
      if (draft.id) {
        await this.api.updateUser(draft.id, {
          fullName: draft.fullName || null,
          email: draft.email || null,
          admin: draft.admin,
          active: draft.active,
        });
      } else {
        await this.api.createUser({
          username: draft.username,
          password: draft.password,
          fullName: draft.fullName || null,
          email: draft.email || null,
          admin: draft.admin,
        });
      }
      this.editing.set(null);
      this.toasts.success(this.t('common.saved'));
      await this.load();
    } catch (error) {
      this.errors.set(this.toasts.problem(error).fieldErrors);
    } finally {
      this.saving.set(false);
    }
  }

  protected async applyReset(): Promise<void> {
    const target = this.resetting();
    if (!target || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.errors.set({});
    try {
      await this.api.resetUserPassword(target.id, this.newPassword);
      this.resetting.set(null);
      this.newPassword = '';
      this.toasts.success(this.t('common.saved'));
      await this.load();
    } catch (error) {
      this.errors.set(this.toasts.problem(error).fieldErrors);
    } finally {
      this.saving.set(false);
    }
  }

  protected async remove(user: User): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: this.t('common.confirmTitle'),
      question: this.t('common.deleteQuestion', { name: user.username }),
      confirmLabel: this.t('action.delete'),
      destructive: true,
    });
    if (!confirmed) {
      return;
    }
    try {
      await this.api.deleteUser(user.id);
      this.toasts.success(this.t('common.deleted'));
      await this.load();
    } catch (error) {
      this.toasts.problem(error);
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.users.set(await this.api.listUsers());
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.loading.set(false);
    }
  }
}
