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
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { Language } from '../../core/i18n/translations';
import { toProblem } from '../../core/problem';

/** The sign-in screen; also where the language can be chosen before signing in. */
@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="centre">
      <form class="card card-pad stack" (ngSubmit)="submit()" data-testid="login-form">
        <div class="row-between">
          <div>
            <h1>{{ t('app.title') }}</h1>
            <p class="faint">{{ t('app.tagline') }}</p>
          </div>
          <label>
            <span class="visually-hidden">{{ t('nav.language') }}</span>
            <select data-testid="language-switcher" (change)="switchLanguage($event)">
              @for (option of i18n.available; track option) {
                <option [value]="option" [selected]="option === i18n.language()">
                  {{ option === 'de' ? 'Deutsch' : 'English' }}
                </option>
              }
            </select>
          </label>
        </div>

        <h2>{{ t('login.heading') }}</h2>

        @if (failed()) {
          <p class="notice notice-danger" role="alert" data-testid="login-error">
            {{ errorText() }}
          </p>
        }

        <div class="field">
          <label for="username">{{ t('login.username') }}</label>
          <input
            id="username"
            name="username"
            data-testid="username"
            autocomplete="username"
            required
            [(ngModel)]="username"
            [disabled]="busy()"
          />
        </div>

        <div class="field">
          <label for="password">{{ t('login.password') }}</label>
          <input
            id="password"
            name="password"
            type="password"
            data-testid="password"
            autocomplete="current-password"
            required
            [(ngModel)]="password"
            [disabled]="busy()"
          />
        </div>

        <button
          type="submit"
          class="btn btn-primary"
          data-testid="login-submit"
          [disabled]="busy() || !username.trim() || !password"
        >
          {{ busy() ? t('common.loading') : t('login.submit') }}
        </button>

        @if (googleAvailable()) {
          <p class="faint centre-text">{{ t('login.orPassword') }}</p>
          <button
            type="button"
            class="btn"
            data-testid="login-google"
            [disabled]="busy()"
            (click)="signInWithGoogle()"
          >
            {{ t('login.withGoogle') }}
          </button>
        }

        <p class="faint">{{ t('login.firstStart') }}</p>
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
      width: min(420px, 100%);
    }

    select {
      font: inherit;
      min-height: 34px;
      border: 1px solid var(--line-strong);
      border-radius: var(--radius-sm);
      background: var(--surface);
      color: var(--ink);
    }
  `,
})
export class LoginPage {
  protected readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);

  /** Whether this installation offers Google at all; asked once, before anybody signs in. */
  protected readonly googleAvailable = signal(false);

  protected username = '';
  protected password = '';
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);
  protected readonly errorText = signal('');

  constructor() {
    // Whether the button belongs here at all. Answered without a session, and it says only
    // that the installation is configured — never who has an account.
    void this.api
      .googleAvailable()
      .then((status) => this.googleAvailable.set(status.available))
      .catch(() => this.googleAvailable.set(false));
  }

  protected async signInWithGoogle(): Promise<void> {
    this.busy.set(true);
    try {
      const { url } = await this.api.googleSignIn();
      // A full navigation: the next page is Google's, not one of ours.
      window.location.href = url;
    } catch (error) {
      this.busy.set(false);
      this.failed.set(true);
      this.errorText.set(this.i18n.errorMessage(toProblem(error).code, this.t('login.failed')));
    }
  }

  protected switchLanguage(event: Event): void {
    this.i18n.use((event.target as HTMLSelectElement).value as Language);
    if (this.failed()) {
      this.errorText.set(this.t('login.failed'));
    }
  }

  protected async submit(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.failed.set(false);
    try {
      const user = await this.auth.signIn(this.username.trim(), this.password);
      this.password = '';
      if (user?.mustChangePassword) {
        await this.router.navigate(['/change-password']);
      } else {
        // Back to whatever the session expiry interrupted, rather than the dashboard.
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        await this.router.navigateByUrl(returnUrl && returnUrl.startsWith('/') ? returnUrl : '/');
      }
    } catch (error) {
      const problem = toProblem(error);
      this.failed.set(true);
      // A rejected sign-in is by far the likeliest cause, but an unreachable server has to be
      // distinguishable from a typo in the password.
      this.errorText.set(problem.status === 0 ? this.t('error.network') : this.t('login.failed'));
    } finally {
      this.busy.set(false);
    }
  }
}
