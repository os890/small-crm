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
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from './api.service';
import { adminGuard, anonymousGuard, authGuard, passwordChangeGuard } from './auth.guards';
import { AuthService } from './auth.service';
import { User } from './models';

function user(overrides: Partial<User> = {}): User {
  return {
    id: 1,
    username: 'admin',
    fullName: null,
    email: null,
    roles: ['ADMIN', 'USER'],
    admin: true,
    active: true,
    mustChangePassword: false,
    createdAt: '2026-07-01T00:00:00Z',
    ...overrides,
  };
}

describe('route guards', () => {
  let me: ReturnType<typeof vi.fn>;
  let router: Router;

  beforeEach(() => {
    me = vi.fn();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ApiService, useValue: { me } }],
    });
    router = TestBed.inject(Router);
  });

  /** Runs a guard inside an injection context, the way the router would. */
  function run(guard: CanActivateFn): Promise<boolean | UrlTree> {
    return TestBed.runInInjectionContext(
      () =>
        guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot) as Promise<
          boolean | UrlTree
        >,
    );
  }

  function path(result: boolean | UrlTree): string {
    return result instanceof UrlTree ? router.serializeUrl(result) : String(result);
  }

  it('sends an anonymous visitor to the login screen', async () => {
    me.mockRejectedValue(new Error('401'));

    expect(path(await run(authGuard))).toBe('/login');
  });

  it('lets a signed-in user through', async () => {
    me.mockResolvedValue(user());

    expect(await run(authGuard)).toBe(true);
  });

  it('diverts a user who still has to change the password', async () => {
    me.mockResolvedValue(user({ mustChangePassword: true }));

    expect(path(await run(authGuard))).toBe('/change-password');
  });

  it('keeps a plain user out of the administration area', async () => {
    me.mockResolvedValue(user({ admin: false, roles: ['USER'] }));

    expect(path(await run(adminGuard))).toBe('/');
  });

  it('lets an administrator into the administration area', async () => {
    me.mockResolvedValue(user());

    expect(await run(adminGuard)).toBe(true);
  });

  it('does not even ask about the role while a password change is pending', async () => {
    me.mockResolvedValue(user({ mustChangePassword: true }));

    expect(path(await run(adminGuard))).toBe('/change-password');
  });

  it('shows the login screen only to visitors who are not signed in', async () => {
    me.mockRejectedValue(new Error('401'));
    expect(await run(anonymousGuard)).toBe(true);

    TestBed.inject(AuthService).setUser(user());
    expect(path(await run(anonymousGuard))).toBe('/');
  });

  it('sends an already signed-in user with a pending change to the right screen', async () => {
    me.mockResolvedValue(user({ mustChangePassword: true }));
    await TestBed.inject(AuthService).ensureLoaded();

    expect(path(await run(anonymousGuard))).toBe('/change-password');
  });

  it('opens the password screen only while a change is pending', async () => {
    me.mockResolvedValue(user({ mustChangePassword: true }));
    expect(await run(passwordChangeGuard)).toBe(true);

    TestBed.inject(AuthService).setUser(user({ mustChangePassword: false }));
    expect(path(await run(passwordChangeGuard))).toBe('/');
  });

  it('sends an anonymous visitor from the password screen to the login screen', async () => {
    me.mockRejectedValue(new Error('401'));

    expect(path(await run(passwordChangeGuard))).toBe('/login');
  });
});
