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

import { ADMIN_PASSWORD, INITIAL_PASSWORD } from '../playwright.config';
import { expect, test as setup } from './fixtures';

const STORAGE_STATE = '.auth/admin.json';

/**
 * Walks the very first start of a fresh installation, which is also the journey a new owner
 * takes: sign in with the bootstrap password, be forced to replace it, and land on the
 * overview. The resulting session is reused by every other spec.
 */
setup('first start forces the administrator to pick a new password', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByTestId('login-form')).toBeVisible();
  await page.getByTestId('username').fill('admin');
  await page.getByTestId('password').fill(INITIAL_PASSWORD);
  await page.getByTestId('login-submit').click();

  // The bootstrap password may not survive the first sign-in.
  await expect(page.getByTestId('change-password-form')).toBeVisible();

  await page.getByTestId('current-password').fill(INITIAL_PASSWORD);
  await page.getByTestId('new-password').fill(ADMIN_PASSWORD);
  await page.getByTestId('repeat-password').fill('something-else-entirely');
  await page.getByTestId('change-password-submit').click();
  await expect(page.getByTestId('password-mismatch')).toBeVisible();

  await page.getByTestId('repeat-password').fill(ADMIN_PASSWORD);
  await page.getByTestId('change-password-submit').click();

  await expect(page.getByTestId('signed-in-user')).toBeVisible();
  await expect(page.getByTestId('nav-nav.contacts')).toBeVisible();

  await page.context().storageState({ path: STORAGE_STATE });
});
