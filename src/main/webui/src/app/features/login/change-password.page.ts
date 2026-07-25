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
import { Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { ToastService } from '../../core/toast.service';

/** Forced password change, shown before anything else can be reached. */
@Component({
  selector: 'app-change-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="centre">
      <form class="card card-pad stack" (ngSubmit)="submit()" data-testid="change-password-form">
        <h1>{{ t('password.heading') }}</h1>
        <p class="muted">{{ t('password.explain') }}</p>

        @if (errorText(); as message) {
          <p class="notice notice-danger" role="alert" data-testid="password-error">
            {{ message }}
          </p>
        }

        <div class="field" [class.invalid]="fieldErrors()['currentPassword']">
          <label for="current">{{ t('password.current') }}</label>
          <input
            id="current"
            name="current"
            type="password"
            data-testid="current-password"
            autocomplete="current-password"
            required
            [(ngModel)]="currentPassword"
          />
          @if (fieldErrors()['currentPassword']; as message) {
            <span class="field-error">{{ message }}</span>
          }
        </div>

        <div class="field" [class.invalid]="fieldErrors()['newPassword']">
          <label for="next">{{ t('password.new') }}</label>
          <input
            id="next"
            name="next"
            type="password"
            data-testid="new-password"
            autocomplete="new-password"
            minlength="8"
            required
            [(ngModel)]="newPassword"
          />
          @if (fieldErrors()['newPassword']; as message) {
            <span class="field-error">{{ message }}</span>
          }
        </div>

        <div class="field" [class.invalid]="mismatch()">
          <label for="repeat">{{ t('password.repeat') }}</label>
          <input
            id="repeat"
            name="repeat"
            type="password"
            data-testid="repeat-password"
            autocomplete="new-password"
            required
            [(ngModel)]="repeated"
          />
          @if (mismatch()) {
            <span class="field-error" data-testid="password-mismatch">
              {{ t('password.mismatch') }}
            </span>
          }
        </div>

        <button
          type="submit"
          class="btn btn-primary"
          data-testid="change-password-submit"
          [disabled]="busy() || !currentPassword || newPassword.length < 8"
        >
          {{ busy() ? t('common.loading') : t('password.submit') }}
        </button>
      </form>
    </div>
  `,
  styles: `
    .centre {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: var(--space-5);
    }

    form {
      width: min(460px, 100%);
    }
  `,
})
export class ChangePasswordPage {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  protected currentPassword = '';
  protected newPassword = '';
  protected repeated = '';
  protected readonly busy = signal(false);
  protected readonly errorText = signal('');
  protected readonly fieldErrors = signal<Record<string, string>>({});

  protected mismatch(): boolean {
    return this.repeated.length > 0 && this.repeated !== this.newPassword;
  }

  protected async submit(): Promise<void> {
    if (this.busy()) {
      return;
    }
    if (this.repeated !== this.newPassword) {
      this.errorText.set(this.t('password.mismatch'));
      return;
    }
    this.busy.set(true);
    this.errorText.set('');
    this.fieldErrors.set({});
    try {
      const user = await this.api.changePassword(this.currentPassword, this.newPassword);
      this.auth.setUser(user);
      this.toasts.success(this.t('password.changed'));
      await this.router.navigate(['/']);
    } catch (error) {
      const problem = this.toasts.problem(error);
      this.errorText.set(this.i18n.errorMessage(problem.code, problem.message));
      this.fieldErrors.set(problem.fieldErrors);
    } finally {
      this.busy.set(false);
    }
  }
}
