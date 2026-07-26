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

import { expect, gotoSection, test, unique } from './fixtures';

test('the interface switches to German and stays there after a reload', async ({ page }) => {
  await gotoSection(page, '/');

  await page.getByTestId('language-switcher').selectOption('de');
  await expect(page.getByTestId('nav-nav.contacts')).toContainText('Kontakte');
  await expect(page.getByTestId('nav-nav.calendar')).toContainText('Kalender');

  await page.reload();
  await expect(page.getByTestId('nav-nav.contacts')).toContainText('Kontakte');

  // Server side validation messages follow the chosen language too.
  await gotoSection(page, '/contacts');
  await page.getByTestId('new-contact').click();
  await page.getByTestId('contact-save').click();
  await expect(page.getByTestId('contact-dialog')).toContainText('darf nicht leer sein');

  await page.getByTestId('language-switcher').selectOption('en');
  await expect(page.getByTestId('nav-nav.contacts')).toContainText('Contacts');
});

test('an administrator adds a user who then has to choose their own password', async ({
  page,
  browser,
}) => {
  const username = unique('assistant')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');
  const initialPassword = 'initial-secret';
  const ownPassword = 'their-own-secret';

  await gotoSection(page, '/users');
  await page.getByTestId('new-user').click();
  await page.getByTestId('user-username').fill(username);
  await page.getByTestId('user-password').fill(initialPassword);
  await page.getByTestId('user-save').click();

  const row = page.getByTestId('user-rows').locator('tr', { hasText: username });
  await expect(row).toContainText('Must change password');

  // A second browser context takes the new account through its first sign-in. The stored
  // administrator session has to be dropped explicitly, otherwise the context inherits it
  // from the project configuration and never sees the login screen.
  const context = await browser.newContext({ storageState: undefined });
  const fresh = await context.newPage();
  await fresh.goto('/');
  await fresh.getByTestId('username').fill(username);
  await fresh.getByTestId('password').fill(initialPassword);
  await fresh.getByTestId('login-submit').click();

  await expect(fresh.getByTestId('change-password-form')).toBeVisible();
  await fresh.getByTestId('current-password').fill(initialPassword);
  await fresh.getByTestId('new-password').fill(ownPassword);
  await fresh.getByTestId('repeat-password').fill(ownPassword);
  await fresh.getByTestId('change-password-submit').click();

  await expect(fresh.getByTestId('signed-in-user')).toBeVisible();
  // A plain user gets the CRM but not the administration area.
  await expect(fresh.getByTestId('nav-nav.contacts')).toBeVisible();
  await expect(fresh.getByTestId('nav-nav.users')).toHaveCount(0);

  await fresh.goto('/users');
  await expect(fresh).toHaveURL(/\/$/);

  await context.close();
});

test('a bookmarked page opens the application instead of a blank 404', async ({ page }) => {
  // Every URL other than /api belongs to the Angular router, and the server has to hand back
  // the application for all of them. This broke once, invisibly: a catch-all exception mapper
  // answered the unmatched path itself, so the single-page fallback never ran and every
  // bookmark, reload and deep link came back empty.
  for (const path of ['/contacts', '/deals', '/calendar', '/backups']) {
    const response = await page.goto(path);
    expect(response?.status(), `${path} should be served by the application`).toBe(200);
    await expect(page.getByTestId('signed-in-user')).toBeVisible();
  }

  // A path the application does not know still loads it; the router shows its own not-found.
  const unknown = await page.goto('/no-such-page');
  expect(unknown?.status()).toBe(200);
});

test('a wrong password is refused and the session stays signed out', async ({ browser }) => {
  const context = await browser.newContext({ storageState: undefined });
  const page = await context.newPage();

  await page.goto('/');
  await page.getByTestId('username').fill('admin');
  await page.getByTestId('password').fill('definitely-not-the-password');
  await page.getByTestId('login-submit').click();

  await expect(page.getByTestId('login-error')).toBeVisible();
  await expect(page.getByTestId('login-form')).toBeVisible();

  await context.close();
});

test('signing out returns to the login screen and protects the pages again', async ({
  browser,
}) => {
  const context = await browser.newContext({ storageState: '.auth/admin.json' });
  const page = await context.newPage();

  await gotoSection(page, '/');
  await page.getByTestId('sign-out').click();
  await expect(page.getByTestId('login-form')).toBeVisible();

  await page.goto('/contacts');
  await expect(page.getByTestId('login-form')).toBeVisible();

  await context.close();
});
