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

import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { I18nService } from './i18n/i18n.service';

const LOGIN_URL = '/api/auth/login';

/**
 * Tags every request with the chosen language, so server side validation messages come back
 * translated, and turns an expired session into a trip to the login screen instead of a
 * confusing error toast on whatever page the user was on.
 */
export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const i18n = inject(I18nService);
  const auth = inject(AuthService);
  const router = inject(Router);

  const localised = request.clone({
    setHeaders: { 'Accept-Language': i18n.language() },
  });

  return next(localised).pipe(
    catchError((error: unknown) => {
      const isSessionLoss =
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !request.url.endsWith(LOGIN_URL);
      if (isSessionLoss && auth.isSignedIn()) {
        auth.clear();
        // Carry the page along, so signing back in returns the user where they were rather
        // than dumping them on the dashboard.
        const returnUrl = router.url;
        void router.navigate(['/login'], {
          queryParams: returnUrl && returnUrl !== '/' ? { returnUrl } : {},
        });
      }
      return throwError(() => error);
    }),
  );
};
