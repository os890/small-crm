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

test('a backup can be created, downloaded and restored from the folder', async ({ page }) => {
  const beforeBackup = unique('Kept by the backup');

  await gotoSection(page, '/companies');
  await page.getByTestId('new-company').click();
  await page.getByTestId('company-name').fill(beforeBackup);
  await page.getByTestId('company-save').click();
  await expect(page.getByTestId('company-dialog')).toBeHidden();

  await gotoSection(page, '/backups');
  await page.getByTestId('create-backup').click();
  await expect(page.getByTestId('backup-rows')).toContainText('smallcrm-backup-');

  const fileName = (
    await page.getByTestId('backup-rows').locator('code').first().innerText()
  ).trim();

  // The downloaded file really is the backup, with the company in it.
  const download = await page.request.get(`/api/backups/${encodeURIComponent(fileName)}/content`);
  expect(download.ok()).toBe(true);
  expect(await download.text()).toContain(beforeBackup);

  // Change the data after the backup was taken.
  const afterBackup = unique('Added after the backup');
  await gotoSection(page, '/companies');
  await page.getByTestId('new-company').click();
  await page.getByTestId('company-name').fill(afterBackup);
  await page.getByTestId('company-save').click();
  await expect(page.getByTestId('company-rows')).toContainText(afterBackup);

  // Restoring names the file and warns before replacing anything.
  await gotoSection(page, '/backups');
  await page.getByTestId(`restore-${fileName}`).click();
  await expect(page.getByRole('alertdialog')).toContainText(fileName);
  await expect(page.getByRole('alertdialog')).toContainText('deletes all contacts');
  await page.getByTestId('confirm-accept').click();

  // A before-restore file now holds the state that was replaced.
  await expect(page.getByTestId('backup-rows')).toContainText('before-restore-');

  await gotoSection(page, '/companies');
  await expect(page.getByTestId('company-rows')).toContainText(beforeBackup);
  await expect(page.getByTestId('company-rows')).not.toContainText(afterBackup);
});

test('an unwanted restore can be undone with the before-restore file', async ({ page }) => {
  const original = unique('Only in the newer state');

  await gotoSection(page, '/companies');
  await page.getByTestId('new-company').click();
  await page.getByTestId('company-name').fill(original);
  await page.getByTestId('company-save').click();
  await expect(page.getByTestId('company-rows')).toContainText(original);

  await gotoSection(page, '/backups');
  // Restore the oldest backup, which predates the company just created.
  const oldest = (
    await page.getByTestId('backup-rows').locator('tr').last().locator('code').innerText()
  ).trim();
  await page.getByTestId(`restore-${oldest}`).click();
  await page.getByTestId('confirm-accept').click();
  await expect(page.getByTestId('backup-rows')).toContainText('before-restore-');

  await gotoSection(page, '/companies');
  await expect(page.getByTestId('company-rows')).not.toContainText(original);

  // The safety copy brings the newer state back.
  await gotoSection(page, '/backups');
  const safetyCopy = (
    await page
      .getByTestId('backup-rows')
      .locator('tr', { hasText: 'before-restore-' })
      .first()
      .locator('code')
      .innerText()
  ).trim();
  await page.getByTestId(`restore-${safetyCopy}`).click();
  await page.getByTestId('confirm-accept').click();

  await gotoSection(page, '/companies');
  await expect(page.getByTestId('company-rows')).toContainText(original);
});

test('the retention period can be changed from the screen', async ({ page }) => {
  await gotoSection(page, '/backups');

  await expect(page.getByTestId('retention-days')).toHaveValue('14');
  await expect(page.getByTestId('backup-folder')).toContainText('backup');

  await page.getByTestId('retention-days').fill('21');
  await page.getByTestId('save-retention').click();

  await page.reload();
  await expect(page.getByTestId('retention-days')).toHaveValue('21');

  // Put it back so the other specs see the default.
  await page.getByTestId('retention-days').fill('14');
  await page.getByTestId('save-retention').click();
  await expect(page.getByTestId('retention-days')).toHaveValue('14');
});

test('a file that is not a backup is refused without losing any data', async ({ page }) => {
  const kept = unique('Must survive');

  await gotoSection(page, '/companies');
  await page.getByTestId('new-company').click();
  await page.getByTestId('company-name').fill(kept);
  await page.getByTestId('company-save').click();
  await expect(page.getByTestId('company-rows')).toContainText(kept);

  await gotoSection(page, '/backups');
  await page.getByTestId('backup-upload').setInputFiles({
    name: 'not-a-backup.xml',
    mimeType: 'application/xml',
    buffer: Buffer.from('this is definitely not a backup'),
  });
  await page.getByTestId('restore-upload').click();
  await page.getByTestId('confirm-accept').click();

  await expect(page.locator('.toast-error')).toContainText('not a Small CRM backup');

  await gotoSection(page, '/companies');
  await expect(page.getByTestId('company-rows')).toContainText(kept);
});

test('a plain user cannot reach the backup screen', async ({ page, browser }) => {
  const username = unique('helper')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');

  await gotoSection(page, '/users');
  await page.getByTestId('new-user').click();
  await page.getByTestId('user-username').fill(username);
  await page.getByTestId('user-password').fill('initial-secret');
  await page.getByTestId('user-save').click();
  await expect(page.getByTestId('user-rows')).toContainText(username);

  const context = await browser.newContext({ storageState: undefined });
  const plain = await context.newPage();
  await plain.goto('/');
  await plain.getByTestId('username').fill(username);
  await plain.getByTestId('password').fill('initial-secret');
  await plain.getByTestId('login-submit').click();
  await plain.getByTestId('current-password').fill('initial-secret');
  await plain.getByTestId('new-password').fill('their-own-secret');
  await plain.getByTestId('repeat-password').fill('their-own-secret');
  await plain.getByTestId('change-password-submit').click();
  await expect(plain.getByTestId('signed-in-user')).toBeVisible();

  await expect(plain.getByTestId('nav-nav.backups')).toHaveCount(0);
  await plain.goto('/backups');
  await expect(plain).toHaveURL(/\/$/);

  await context.close();
});
