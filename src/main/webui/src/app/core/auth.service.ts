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

import { Injectable, computed, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { User } from './models';

/** Holds who is signed in and keeps that answer available to guards and templates. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly currentUser = signal<User | null>(null);
  private readonly loaded = signal(false);
  private inFlight: Promise<User | null> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly isSignedIn = computed(() => this.currentUser() !== null);
  readonly isAdmin = computed(() => this.currentUser()?.admin === true);
  readonly mustChangePassword = computed(() => this.currentUser()?.mustChangePassword === true);
  readonly displayName = computed(() => {
    const user = this.currentUser();
    return user ? user.fullName || user.username : '';
  });

  /**
   * Asks the server who we are, using the session cookie the browser already holds.
   *
   * <p>An unauthenticated answer is normal here, not a failure, so it resolves to `null` rather
   * than throwing. Note that a user who still has to change their password is returned as a
   * signed-in user: the profile endpoint stays reachable in that state on purpose, and the
   * router then sends them to the change-password screen.
   */
  async refresh(): Promise<User | null> {
    try {
      const user = await this.api.me();
      this.currentUser.set(user);
      return user;
    } catch {
      this.currentUser.set(null);
      return null;
    } finally {
      this.loaded.set(true);
    }
  }

  /**
   * Loads the profile once per application start, so guards can await a settled answer.
   *
   * <p>The in-flight request is shared: a route can pass through several guards at once, and
   * they must not each ask the server who is signed in.
   */
  async ensureLoaded(): Promise<void> {
    if (this.loaded()) {
      return;
    }
    this.inFlight ??= this.refresh().finally(() => {
      this.inFlight = null;
    });
    await this.inFlight;
  }

  async signIn(username: string, password: string): Promise<User | null> {
    await this.api.login(username, password);
    return this.refresh();
  }

  async signOut(): Promise<void> {
    try {
      await this.api.logout();
    } finally {
      this.currentUser.set(null);
      this.loaded.set(true);
    }
  }

  /** Applied after the forced password change so the guards let the user through. */
  setUser(user: User): void {
    this.currentUser.set(user);
    this.loaded.set(true);
  }

  /** Drops the cached profile, used when the server reports the session has expired. */
  clear(): void {
    this.currentUser.set(null);
    this.loaded.set(true);
  }
}
