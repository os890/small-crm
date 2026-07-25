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

import { expect, gotoSection, isoDate, test, unique } from './fixtures';

test('a deal moves through the pipeline and disappears once it is won', async ({ page }) => {
  const title = unique('Website relaunch');

  await gotoSection(page, '/deals');
  await page.getByTestId('new-deal').click();
  await page.getByTestId('deal-title').fill(title);
  await page.getByTestId('deal-amount').fill('4500');
  await page.getByTestId('deal-save').click();

  await expect(page.getByTestId('column-LEAD')).toContainText(title);

  const card = page.getByTestId('deal-card').filter({ hasText: title });
  await card.locator('select').selectOption('PROPOSAL');
  await expect(page.getByTestId('column-PROPOSAL')).toContainText(title);
  await expect(page.getByTestId('column-LEAD')).not.toContainText(title);

  // Won deals leave the open pipeline but come back when the filter is turned off.
  await page
    .getByTestId('deal-card')
    .filter({ hasText: title })
    .locator('select')
    .selectOption('WON');
  await expect(page.getByTestId('deal-card').filter({ hasText: title })).toHaveCount(0);

  await page.getByTestId('deals-open-only').uncheck();
  await expect(page.getByTestId('column-WON')).toContainText(title);
});

test('a negative amount is rejected with the message on the field', async ({ page }) => {
  await gotoSection(page, '/deals');
  await page.getByTestId('new-deal').click();
  await page.getByTestId('deal-title').fill(unique('Bad amount'));
  await page.getByTestId('deal-amount').fill('-5');
  await page.getByTestId('deal-save').click();

  await expect(page.getByTestId('deal-dialog')).toContainText('must be greater than or equal to 0');
});

test('an overdue to-do is flagged and can be ticked off', async ({ page }) => {
  const title = unique('Chase the invoice');

  await gotoSection(page, '/tasks');
  await page.getByTestId('new-task').click();
  await page.getByTestId('task-title').fill(title);
  await page.getByTestId('task-due').fill(isoDate(-2));
  await page.getByTestId('task-save').click();

  const row = page.getByTestId('task-rows').locator('li', { hasText: title });
  await expect(row).toContainText('Overdue');

  await row.getByRole('checkbox').check();

  // With the open-only filter on, a finished to-do leaves the list.
  await expect(page.getByTestId('task-rows').locator('li', { hasText: title })).toHaveCount(0);

  await page.getByTestId('tasks-open-only').uncheck();
  await expect(page.getByTestId('task-rows')).toContainText(title);
});

test('the overview counts what was entered and lists what is overdue', async ({ page }) => {
  const title = unique('Overdue on the overview');

  await gotoSection(page, '/tasks');
  await page.getByTestId('new-task').click();
  await page.getByTestId('task-title').fill(title);
  await page.getByTestId('task-due').fill(isoDate(-1));
  await page.getByTestId('task-save').click();
  await expect(page.getByTestId('task-dialog')).toBeHidden();

  await gotoSection(page, '/');

  await expect(page.getByTestId('panel-overdue')).toContainText(title);
  await expect(page.getByTestId('all-clear')).toBeHidden();
  await expect(page.getByTestId('tile-contacts')).not.toHaveText('0');
});
