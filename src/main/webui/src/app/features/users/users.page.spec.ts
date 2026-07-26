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
import { describe, expect, it } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { User } from '../../core/models';
import { PageHarness, renderPage } from '../../testing/page-harness';
import { UsersPage } from './users.page';

function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 1,
    username: 'admin',
    fullName: 'Administrator',
    email: null,
    roles: ['ADMIN', 'USER'],
    admin: true,
    active: true,
    mustChangePassword: false,
    createdAt: '2026-07-01T00:00:00Z',
    ...overrides,
  };
}

const ASSISTANT = makeUser({
  id: 2,
  username: 'assistant',
  fullName: 'Assistant',
  admin: false,
  roles: ['USER'],
  mustChangePassword: true,
});

async function open(users: User[] = [makeUser(), ASSISTANT]): Promise<PageHarness<UsersPage>> {
  const harness = renderPage(UsersPage);
  await harness.settle();
  harness.flushGet('/api/users', users);
  await harness.settle();
  return harness;
}

describe('UsersPage', () => {
  it('lists accounts and flags a pending password change', async () => {
    const harness = await open();

    const rows = harness.query('user-rows')?.textContent ?? '';
    expect(rows).toContain('assistant');
    expect(rows).toContain('Must change password');
  });

  it('marks the signed-in account and refuses to delete it', async () => {
    const harness = await open();
    TestBed.inject(AuthService).setUser(makeUser());
    await harness.settle();

    expect(harness.query('user-rows')?.textContent).toContain('you');
    const deleteButtons = harness.fixture.nativeElement.querySelectorAll(
      'tbody .btn-danger',
    ) as NodeListOf<HTMLButtonElement>;
    expect(deleteButtons[0].disabled).toBe(true);
    expect(deleteButtons[1].disabled).toBe(false);
  });

  it('asks for a user name and password only when creating', async () => {
    const harness = await open();

    await harness.click('new-user');
    expect(harness.query('user-username')).not.toBeNull();
    expect(harness.query('user-password')).not.toBeNull();

    await harness.click('user-save');
    harness.http.expectOne('/api/users').flush(makeUser({ id: 3 }));
    await harness.settle();
    harness.flushGet('/api/users', []);
    await harness.settle();

    // Editing hides both, because a password is replaced through the reset dialog instead.
    const harness2 = await open();
    (harness2.fixture.nativeElement.querySelector('tbody .btn') as HTMLElement).click();
    await harness2.settle();

    expect(harness2.query('user-username')).toBeNull();
    expect(harness2.query('user-password')).toBeNull();
  });

  it('creates an account with the chosen role', async () => {
    const harness = await open();

    await harness.click('new-user');
    await harness.type('user-username', 'bookkeeper');
    await harness.type('user-password', 'initial-secret');
    await harness.click('user-admin');
    await harness.click('user-save');

    const created = harness.http.expectOne('/api/users');
    expect(created.request.method).toBe('POST');
    expect(created.request.body).toMatchObject({
      username: 'bookkeeper',
      password: 'initial-secret',
      admin: true,
    });
    created.flush(makeUser({ id: 3, username: 'bookkeeper' }));
    await harness.settle();
    harness.flushGet('/api/users', []);
    await harness.settle();
  });

  it('updates an account with PUT and without a password', async () => {
    const harness = await open();

    (harness.fixture.nativeElement.querySelector('tbody .btn') as HTMLElement).click();
    await harness.settle();
    await harness.click('user-save');

    const updated = harness.http.expectOne('/api/users/1');
    expect(updated.request.method).toBe('PUT');
    expect(updated.request.body).toMatchObject({ admin: true, active: true });
    expect(updated.request.body.password).toBeUndefined();
    updated.flush(makeUser());
    await harness.settle();
    harness.flushGet('/api/users', []);
    await harness.settle();
  });

  it('reports the server refusing to remove the last administrator', async () => {
    const harness = await open();

    (harness.fixture.nativeElement.querySelector('tbody .btn') as HTMLElement).click();
    await harness.settle();
    await harness.click('user-save');
    harness.http
      .expectOne('/api/users/1')
      .flush(
        { code: 'LAST_ADMIN', message: 'nope', details: null },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.settle();

    // The refusal is surfaced as a toast, which lives outside this component.
    expect(
      TestBed.inject(ToastService)
        .toasts()
        .map((toast) => toast.text),
    ).toContain('At least one active administrator has to remain.');
    expect(harness.query('user-dialog')).not.toBeNull();
  });

  it('sets a new password through the reset dialog', async () => {
    const harness = await open();

    const buttons = harness.fixture.nativeElement.querySelectorAll(
      'tbody tr:nth-child(2) .btn',
    ) as NodeListOf<HTMLElement>;
    buttons[1].click();
    await harness.settle();

    expect(harness.query('reset-dialog')?.textContent).toContain('assistant');

    await harness.type('reset-own-password', 'the-admins-own-password');
    await harness.type('reset-password', 'a-fresh-secret-x');
    await harness.click('reset-save');

    const request = harness.http.expectOne('/api/users/2/password');
    expect(request.request.body).toEqual({
      currentPassword: 'the-admins-own-password',
      newPassword: 'a-fresh-secret-x',
    });
    request.flush(ASSISTANT);
    await harness.settle();
    harness.flushGet('/api/users', []);
    await harness.settle();

    expect(harness.query('reset-dialog')).toBeNull();
  });

  it('will not reset anything until the administrator confirms their own password', async () => {
    const harness = await open();

    const buttons = harness.fixture.nativeElement.querySelectorAll(
      'tbody tr:nth-child(2) .btn',
    ) as NodeListOf<HTMLElement>;
    buttons[1].click();
    await harness.settle();

    await harness.type('reset-password', 'a-fresh-secret-x');
    expect(harness.query<HTMLButtonElement>('reset-save')?.disabled).toBe(true);

    await harness.type('reset-own-password', 'the-admins-own-password');
    expect(harness.query<HTMLButtonElement>('reset-save')?.disabled).toBe(false);
  });

  it('keeps the dialog open and points at the field when the own password is wrong', async () => {
    const harness = await open();

    const buttons = harness.fixture.nativeElement.querySelectorAll(
      'tbody tr:nth-child(2) .btn',
    ) as NodeListOf<HTMLElement>;
    buttons[1].click();
    await harness.settle();

    await harness.type('reset-own-password', 'not-my-password');
    await harness.type('reset-password', 'a-fresh-secret-x');
    await harness.click('reset-save');

    harness.http.expectOne('/api/users/2/password').flush(
      {
        code: 'VALIDATION_FAILED',
        message: 'One or more fields are invalid',
        details: { currentPassword: 'Your own password is not correct.' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await harness.settle();

    expect(harness.query('reset-dialog')).not.toBeNull();
    expect(harness.query('reset-dialog')?.textContent).toContain(
      'Your own password is not correct.',
    );
  });
});
