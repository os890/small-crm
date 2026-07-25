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
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { User } from './models';

function user(overrides: Partial<User> = {}): User {
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

/** Stands in for the HTTP layer so the service can be exercised without a server. */
class ApiStub {
  me = vi.fn<() => Promise<User>>();
  login = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
  logout = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
}

describe('AuthService', () => {
  let auth: AuthService;
  let api: ApiStub;

  beforeEach(() => {
    api = new ApiStub();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    auth = TestBed.inject(AuthService);
  });

  it('starts out signed out', () => {
    expect(auth.isSignedIn()).toBe(false);
    expect(auth.isAdmin()).toBe(false);
    expect(auth.displayName()).toBe('');
  });

  it('adopts the profile the server returns', async () => {
    api.me.mockResolvedValue(user());

    await auth.refresh();

    expect(auth.isSignedIn()).toBe(true);
    expect(auth.isAdmin()).toBe(true);
    expect(auth.displayName()).toBe('Administrator');
  });

  it('falls back to the user name when no full name is set', async () => {
    api.me.mockResolvedValue(user({ fullName: null }));

    await auth.refresh();

    expect(auth.displayName()).toBe('admin');
  });

  it('treats an unauthenticated answer as signed out rather than an error', async () => {
    api.me.mockRejectedValue(new Error('401'));

    await expect(auth.refresh()).resolves.toBeNull();
    expect(auth.isSignedIn()).toBe(false);
  });

  it('reports a pending password change so the router can redirect', async () => {
    api.me.mockResolvedValue(user({ mustChangePassword: true }));

    await auth.refresh();

    expect(auth.isSignedIn()).toBe(true);
    expect(auth.mustChangePassword()).toBe(true);
  });

  it('loads the profile only once even when several guards ask', async () => {
    api.me.mockResolvedValue(user());

    await Promise.all([auth.ensureLoaded(), auth.ensureLoaded()]);
    await auth.ensureLoaded();

    expect(api.me).toHaveBeenCalledTimes(1);
  });

  it('signs in and then reads the profile back', async () => {
    api.me.mockResolvedValue(user());

    const signedIn = await auth.signIn('admin', 'secret');

    expect(api.login).toHaveBeenCalledWith('admin', 'secret');
    expect(signedIn?.username).toBe('admin');
    expect(auth.isSignedIn()).toBe(true);
  });

  it('clears the profile on sign out even if the server call fails', async () => {
    api.me.mockResolvedValue(user());
    await auth.refresh();
    api.logout.mockRejectedValue(new Error('offline'));

    await expect(auth.signOut()).rejects.toThrow('offline');
    expect(auth.isSignedIn()).toBe(false);
  });

  it('accepts the profile handed over after a forced password change', () => {
    auth.setUser(user({ mustChangePassword: false }));

    expect(auth.isSignedIn()).toBe(true);
    expect(auth.mustChangePassword()).toBe(false);
  });

  it('drops the profile when the session is reported gone', async () => {
    api.me.mockResolvedValue(user());
    await auth.refresh();

    auth.clear();

    expect(auth.isSignedIn()).toBe(false);
  });
});
