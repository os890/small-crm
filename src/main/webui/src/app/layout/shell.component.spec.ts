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
import { Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../core/auth.service';
import { User } from '../core/models';
import { PageHarness, renderPage } from '../testing/page-harness';
import { ShellComponent } from './shell.component';

function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 1,
    username: 'admin',
    fullName: 'Maria Huber',
    email: null,
    roles: ['ADMIN', 'USER'],
    admin: true,
    active: true,
    mustChangePassword: false,
    createdAt: '2026-07-01T00:00:00Z',
    ...overrides,
  };
}

async function open(user: User): Promise<PageHarness<ShellComponent>> {
  const harness = renderPage(ShellComponent);
  TestBed.inject(AuthService).setUser(user);
  await harness.settle();
  return harness;
}

describe('ShellComponent', () => {
  beforeEach(() => localStorage.clear());

  it('shows who is signed in', async () => {
    const harness = await open(makeUser());

    expect(harness.text('signed-in-user')).toBe('Maria Huber');
  });

  it('offers the administration entry only to administrators', async () => {
    const asAdmin = await open(makeUser());
    expect(asAdmin.query('nav-nav.users')).not.toBeNull();

    const asUser = await open(makeUser({ admin: false, roles: ['USER'] }));
    expect(asUser.query('nav-nav.users')).toBeNull();
    expect(asUser.query('nav-nav.contacts')).not.toBeNull();
  });

  it('translates the navigation when the language is switched', async () => {
    const harness = await open(makeUser());
    expect(harness.text('nav-nav.contacts')).toContain('Contacts');
    expect(harness.query<HTMLSelectElement>('language-switcher')?.value).toBe('en');

    const select = harness.query<HTMLSelectElement>('language-switcher');
    select!.value = 'de';
    select!.dispatchEvent(new Event('change'));
    await harness.settle();

    expect(harness.text('nav-nav.contacts')).toContain('Kontakte');
    expect(harness.text('nav-nav.calendar')).toContain('Kalender');
    // The dropdown itself has to reflect the choice, not fall back to the first option.
    expect(harness.query<HTMLSelectElement>('language-switcher')?.value).toBe('de');
  });

  it('signs out and returns to the login screen', async () => {
    const harness = await open(makeUser());
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    await harness.click('sign-out');
    harness.http.expectOne('/api/auth/logout').flush(null);
    await harness.settle();

    expect(navigate).toHaveBeenCalledWith(['/login']);
    expect(TestBed.inject(AuthService).isSignedIn()).toBe(false);
  });
});
