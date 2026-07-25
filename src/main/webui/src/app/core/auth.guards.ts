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

import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Decides whether a signed-in, password-current user may proceed.
 *
 * <p>Extracted so {@link adminGuard} can build on the same answer without depending on the
 * wider return type a `CanActivateFn` is allowed to have.
 */
async function requireSignedIn(auth: AuthService, router: Router): Promise<true | UrlTree> {
  await auth.ensureLoaded();
  if (!auth.isSignedIn()) {
    return router.createUrlTree(['/login']);
  }
  if (auth.mustChangePassword()) {
    return router.createUrlTree(['/change-password']);
  }
  return true;
}

/** Requires a signed-in account that has finished its forced password change. */
export const authGuard: CanActivateFn = () => requireSignedIn(inject(AuthService), inject(Router));

/** Additionally requires the administrator role. */
export const adminGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const allowed = await requireSignedIn(auth, router);
  if (allowed !== true) {
    return allowed;
  }
  return auth.isAdmin() ? true : router.createUrlTree(['/']);
};

/** Keeps an already signed-in user away from the login screen. */
export const anonymousGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.ensureLoaded();

  if (!auth.isSignedIn()) {
    return true;
  }
  return router.createUrlTree([auth.mustChangePassword() ? '/change-password' : '/']);
};

/** Only reachable while a password change is actually pending. */
export const passwordChangeGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.ensureLoaded();

  if (!auth.isSignedIn()) {
    return router.createUrlTree(['/login']);
  }
  return auth.mustChangePassword() ? true : router.createUrlTree(['/']);
};
