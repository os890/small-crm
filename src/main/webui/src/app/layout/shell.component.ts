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
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { Language } from '../core/i18n/translations';
import { TranslationKey } from '../core/i18n/translations';

interface NavItem {
  path: string;
  labelKey: TranslationKey;
  icon: string;
  adminOnly?: boolean;
}

const NAV: NavItem[] = [
  { path: '/', labelKey: 'nav.dashboard', icon: '◉' },
  { path: '/contacts', labelKey: 'nav.contacts', icon: '👤' },
  { path: '/companies', labelKey: 'nav.companies', icon: '🏢' },
  { path: '/deals', labelKey: 'nav.deals', icon: '💼' },
  { path: '/tasks', labelKey: 'nav.tasks', icon: '✓' },
  { path: '/calendar', labelKey: 'nav.calendar', icon: '📅' },
  { path: '/users', labelKey: 'nav.users', icon: '⚙', adminOnly: true },
];

/** Frame around every signed-in page: header, navigation and the routed content. */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <header class="topbar">
        <button
          type="button"
          class="btn btn-quiet nav-toggle"
          [attr.aria-expanded]="navOpen()"
          [attr.aria-label]="t('nav.menu')"
          (click)="navOpen.set(!navOpen())"
        >
          ☰
        </button>
        <a routerLink="/" class="brand">{{ t('app.title') }}</a>
        <span class="grow"></span>

        <label class="lang">
          <span class="visually-hidden">{{ t('nav.language') }}</span>
          <select data-testid="language-switcher" (change)="switchLanguage($event)">
            @for (option of i18n.available; track option) {
              <option [value]="option" [selected]="option === i18n.language()">
                {{ option === 'de' ? 'Deutsch' : 'English' }}
              </option>
            }
          </select>
        </label>

        <span class="who" data-testid="signed-in-user">{{ auth.displayName() }}</span>
        <button type="button" class="btn btn-sm" data-testid="sign-out" (click)="signOut()">
          {{ t('nav.signOut') }}
        </button>
      </header>

      <div class="body">
        <nav class="sidenav" [class.open]="navOpen()" [attr.aria-label]="t('nav.menu')">
          @for (item of visibleNav(); track item.path) {
            <a
              [routerLink]="item.path"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{ exact: item.path === '/' }"
              [attr.data-testid]="'nav-' + item.labelKey"
              (click)="navOpen.set(false)"
            >
              <span class="icon" aria-hidden="true">{{ item.icon }}</span>
              <span>{{ t(item.labelKey) }}</span>
            </a>
          }
        </nav>

        <main class="content">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: `
    .shell {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
    }

    .topbar {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-4);
      background: var(--surface);
      border-bottom: 1px solid var(--line);
      position: sticky;
      top: 0;
      z-index: 20;
    }

    .brand {
      font-weight: 700;
      font-size: 1.1rem;
      color: var(--ink);
      text-decoration: none;
    }

    .who {
      color: var(--ink-soft);
      font-size: 0.9375rem;
    }

    .lang select {
      font: inherit;
      min-height: 34px;
      padding: 0 var(--space-2);
      border: 1px solid var(--line-strong);
      border-radius: var(--radius-sm);
      background: var(--surface);
      color: var(--ink);
    }

    .nav-toggle {
      display: none;
      font-size: 1.25rem;
    }

    .body {
      display: flex;
      flex: 1 1 auto;
      align-items: flex-start;
    }

    .sidenav {
      width: 220px;
      flex: 0 0 220px;
      padding: var(--space-4) var(--space-3);
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
      position: sticky;
      top: 61px;
    }

    .sidenav a {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-3);
      border-radius: var(--radius-sm);
      color: var(--ink-soft);
      text-decoration: none;
      font-weight: 550;
    }

    .sidenav a:hover {
      background: var(--surface);
      color: var(--ink);
    }

    .sidenav a.active {
      background: var(--accent-soft);
      color: var(--accent-dark);
    }

    .icon {
      width: 1.25rem;
      text-align: center;
    }

    .content {
      flex: 1 1 auto;
      min-width: 0;
      padding: var(--space-5);
      padding-inline-start: 0;
    }

    @media (max-width: 800px) {
      .nav-toggle {
        display: inline-flex;
      }

      .who {
        display: none;
      }

      .body {
        flex-direction: column;
      }

      .sidenav {
        display: none;
        width: 100%;
        flex: 1 1 auto;
        position: static;
        background: var(--surface);
        border-bottom: 1px solid var(--line);
      }

      .sidenav.open {
        display: flex;
      }

      .content {
        width: 100%;
        padding: var(--space-4);
      }
    }
  `,
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  private readonly router = inject(Router);

  protected readonly navOpen = signal(false);
  protected readonly visibleNav = computed(() =>
    NAV.filter((item) => !item.adminOnly || this.auth.isAdmin()),
  );

  protected switchLanguage(event: Event): void {
    this.i18n.use((event.target as HTMLSelectElement).value as Language);
  }

  protected async signOut(): Promise<void> {
    await this.auth.signOut();
    await this.router.navigate(['/login']);
  }
}
