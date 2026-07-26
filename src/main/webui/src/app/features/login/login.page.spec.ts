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

import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderPage } from '../../testing/page-harness';
import { LoginPage } from './login.page';

const PROFILE = {
  id: 1,
  username: 'admin',
  fullName: 'Administrator',
  email: null,
  roles: ['ADMIN', 'USER'],
  admin: true,
  active: true,
  mustChangePassword: false,
  createdAt: '2026-07-01T00:00:00Z',
};

/** An {@link ActivatedRoute} that carries the given query parameters and nothing else. */
function routeWith(queryParams: Record<string, string>): unknown {
  return {
    provide: ActivatedRoute,
    useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } },
  };
}

describe('LoginPage', () => {
  beforeEach(() => localStorage.clear());

  it('keeps the button disabled until both fields are filled', async () => {
    const harness = renderPage(LoginPage);
    await harness.settle();

    expect(harness.query<HTMLButtonElement>('login-submit')?.disabled).toBe(true);

    await harness.type('username', 'admin');
    expect(harness.query<HTMLButtonElement>('login-submit')?.disabled).toBe(true);

    await harness.type('password', 'a-good-password');
    expect(harness.query<HTMLButtonElement>('login-submit')?.disabled).toBe(false);
  });

  it('signs in and lands on the start page', async () => {
    const harness = renderPage(LoginPage);
    await harness.settle();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    await harness.type('username', 'admin');
    await harness.type('password', 'a-good-password');
    await harness.click('login-submit');

    harness.http.expectOne('/api/auth/login').flush(null);
    await harness.settle();
    harness.http.expectOne('/api/auth/me').flush(PROFILE);
    await harness.settle();

    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('returns to the page the expired session interrupted', async () => {
    const harness = renderPage(LoginPage, {
      providers: [routeWith({ returnUrl: '/contacts/7' })],
    });
    await harness.settle();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    await harness.type('username', 'admin');
    await harness.type('password', 'a-good-password');
    await harness.click('login-submit');
    harness.http.expectOne('/api/auth/login').flush(null);
    await harness.settle();
    harness.http.expectOne('/api/auth/me').flush(PROFILE);
    await harness.settle();

    expect(navigate).toHaveBeenCalledWith('/contacts/7');
  });

  it('ignores a return address that points somewhere else entirely', async () => {
    // Without the leading-slash check this is an open redirect: a crafted login link would
    // bounce the user, freshly signed in, onto someone else's site.
    const harness = renderPage(LoginPage, {
      providers: [routeWith({ returnUrl: 'https://example.invalid/steal' })],
    });
    await harness.settle();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    await harness.type('username', 'admin');
    await harness.type('password', 'a-good-password');
    await harness.click('login-submit');
    harness.http.expectOne('/api/auth/login').flush(null);
    await harness.settle();
    harness.http.expectOne('/api/auth/me').flush(PROFILE);
    await harness.settle();

    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('sends a first-time user straight to the password screen', async () => {
    const harness = renderPage(LoginPage);
    await harness.settle();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    await harness.type('username', 'admin');
    await harness.type('password', 'a-good-password');
    await harness.click('login-submit');
    harness.http.expectOne('/api/auth/login').flush(null);
    await harness.settle();
    harness.http.expectOne('/api/auth/me').flush({ ...PROFILE, mustChangePassword: true });
    await harness.settle();

    expect(navigate).toHaveBeenCalledWith(['/change-password']);
  });

  it('explains a rejected sign-in without revealing which field was wrong', async () => {
    const harness = renderPage(LoginPage);
    await harness.settle();

    await harness.type('username', 'admin');
    await harness.type('password', 'wrong');
    await harness.click('login-submit');
    harness.http
      .expectOne('/api/auth/login')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await harness.settle();

    expect(harness.text('login-error')).toBe('User name or password is not correct.');
  });

  it('distinguishes an unreachable server from a wrong password', async () => {
    const harness = renderPage(LoginPage);
    await harness.settle();

    await harness.type('username', 'admin');
    await harness.type('password', 'a-good-password');
    await harness.click('login-submit');
    harness.http
      .expectOne('/api/auth/login')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    await harness.settle();

    expect(harness.text('login-error')).toContain('server cannot be reached');
  });

  it('re-renders the error in the language the visitor switches to', async () => {
    const harness = renderPage(LoginPage);
    await harness.settle();

    await harness.type('username', 'admin');
    await harness.type('password', 'wrong');
    await harness.click('login-submit');
    harness.http
      .expectOne('/api/auth/login')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await harness.settle();

    const select = harness.query<HTMLSelectElement>('language-switcher');
    select!.value = 'de';
    select!.dispatchEvent(new Event('change'));
    await harness.settle();

    expect(harness.text('login-error')).toBe('Benutzername oder Passwort ist nicht korrekt.');
    expect(harness.fixture.nativeElement.textContent).toContain('Anmelden');
  });
});
