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

test('a company and a contact can be created and linked', async ({ page }) => {
  const companyName = unique('Muster GmbH');
  const lastName = unique('Huber');

  await gotoSection(page, '/companies');
  await page.getByTestId('new-company').click();
  await page.getByTestId('company-name').fill(companyName);
  await page.getByTestId('company-save').click();
  await expect(page.getByTestId('company-rows')).toContainText(companyName);

  await gotoSection(page, '/contacts');
  await page.getByTestId('new-contact').click();
  await page.getByTestId('contact-first-name').fill('Maria');
  await page.getByTestId('contact-last-name').fill(lastName);
  await page.getByTestId('contact-email').fill('maria@example.org');
  await page.getByTestId('contact-tags').fill('vip, key account');
  await page.getByTestId('contact-company').selectOption({ label: companyName });
  await page.getByTestId('contact-save').click();

  const row = page.getByTestId('contact-rows').locator('tr', { hasText: lastName });
  await expect(row).toContainText(companyName);
  await expect(row).toContainText('vip');
  await expect(row).toContainText('key account');
});

test('a contact with no name is refused with the message on the field', async ({ page }) => {
  await gotoSection(page, '/contacts');
  await page.getByTestId('new-contact').click();
  await page.getByTestId('contact-first-name').fill('Only first');
  await page.getByTestId('contact-save').click();

  await expect(page.getByTestId('contact-dialog')).toContainText('must not be blank');
  // The dialog stays open so the entry is not lost.
  await expect(page.getByTestId('contact-dialog')).toBeVisible();
});

test('search narrows the list down to the matching contact', async ({ page }) => {
  const wanted = unique('Findme');
  const other = unique('Hidden');

  await gotoSection(page, '/contacts');
  for (const lastName of [wanted, other]) {
    await page.getByTestId('new-contact').click();
    await page.getByTestId('contact-first-name').fill('Test');
    await page.getByTestId('contact-last-name').fill(lastName);
    await page.getByTestId('contact-save').click();
    await expect(page.getByTestId('contact-dialog')).toBeHidden();
  }

  await page.getByTestId('contact-search').fill(wanted);

  await expect(page.getByTestId('contact-rows').locator('tr')).toHaveCount(1);
  await expect(page.getByTestId('contact-rows')).toContainText(wanted);
});

test('an activity logged on a contact appears in its history', async ({ page }) => {
  const lastName = unique('Aigner');

  await gotoSection(page, '/contacts');
  await page.getByTestId('new-contact').click();
  await page.getByTestId('contact-first-name').fill('Bernd');
  await page.getByTestId('contact-last-name').fill(lastName);
  await page.getByTestId('contact-save').click();

  await page
    .getByTestId('contact-rows')
    .getByRole('link', { name: `Bernd ${lastName}` })
    .click();
  await expect(page.getByTestId('contact-name')).toHaveText(`Bernd ${lastName}`);

  await page.getByTestId('new-interaction').click();
  await page.getByTestId('interaction-subject').fill('Kickoff call');
  await page.getByTestId('interaction-save').click();

  await expect(page.getByTestId('interaction-row')).toContainText('Kickoff call');
});

test('deleting a contact asks first and then removes it', async ({ page }) => {
  const lastName = unique('Doomed');

  await gotoSection(page, '/contacts');
  await page.getByTestId('new-contact').click();
  await page.getByTestId('contact-first-name').fill('Temp');
  await page.getByTestId('contact-last-name').fill(lastName);
  await page.getByTestId('contact-save').click();

  const row = page.getByTestId('contact-rows').locator('tr', { hasText: lastName });
  await row.getByRole('button', { name: 'Delete' }).click();

  await expect(page.getByRole('alertdialog')).toContainText(lastName);
  await page.getByTestId('confirm-accept').click();

  await expect(page.getByTestId('contact-rows').locator('tr', { hasText: lastName })).toHaveCount(
    0,
  );
});
