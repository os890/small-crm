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
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { renderPage } from '../../testing/page-harness';
import { ChangePasswordPage } from './change-password.page';

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

describe('ChangePasswordPage', () => {
  it('requires a current password and eight characters for the new one', async () => {
    const harness = renderPage(ChangePasswordPage);
    await harness.settle();

    expect(harness.query<HTMLButtonElement>('change-password-submit')?.disabled).toBe(true);

    await harness.type('current-password', 'changeit');
    await harness.type('new-password', 'short');
    expect(harness.query<HTMLButtonElement>('change-password-submit')?.disabled).toBe(true);

    await harness.type('new-password', 'long-enough');
    expect(harness.query<HTMLButtonElement>('change-password-submit')?.disabled).toBe(false);
  });

  it('points out a typo in the repetition before contacting the server', async () => {
    const harness = renderPage(ChangePasswordPage);
    await harness.settle();

    await harness.type('current-password', 'changeit');
    await harness.type('new-password', 'a-better-secret');
    await harness.type('repeat-password', 'a-better-secrat');

    expect(harness.query('password-mismatch')).not.toBeNull();

    await harness.click('change-password-submit');

    harness.http.verify();
    expect(harness.text('password-error')).toBe('The two passwords are not the same.');
  });

  it('changes the password and continues to the start page', async () => {
    const harness = renderPage(ChangePasswordPage);
    await harness.settle();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    await harness.type('current-password', 'changeit');
    await harness.type('new-password', 'a-better-secret');
    await harness.type('repeat-password', 'a-better-secret');
    await harness.click('change-password-submit');

    const request = harness.http.expectOne('/api/auth/password');
    expect(request.request.body).toEqual({
      currentPassword: 'changeit',
      newPassword: 'a-better-secret',
    });
    request.flush(PROFILE);
    await harness.settle();

    expect(TestBed.inject(AuthService).mustChangePassword()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it('reports a wrong current password in words, not as a code', async () => {
    const harness = renderPage(ChangePasswordPage);
    await harness.settle();

    await harness.type('current-password', 'wrong');
    await harness.type('new-password', 'a-better-secret');
    await harness.type('repeat-password', 'a-better-secret');
    await harness.click('change-password-submit');

    harness.http
      .expectOne('/api/auth/password')
      .flush(
        { code: 'CURRENT_PASSWORD_WRONG', message: 'nope', details: null },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.settle();

    expect(harness.text('password-error')).toBe('The current password is not correct.');
  });

  it('marks the field the server rejected', async () => {
    const harness = renderPage(ChangePasswordPage);
    await harness.settle();

    await harness.type('current-password', 'changeit');
    await harness.type('new-password', 'a-better-secret');
    await harness.type('repeat-password', 'a-better-secret');
    await harness.click('change-password-submit');

    harness.http.expectOne('/api/auth/password').flush(
      {
        code: 'VALIDATION_FAILED',
        message: 'invalid',
        details: { newPassword: 'size must be between 8 and 100' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await harness.settle();

    expect(harness.fixture.nativeElement.textContent).toContain('size must be between 8 and 100');
  });
});
