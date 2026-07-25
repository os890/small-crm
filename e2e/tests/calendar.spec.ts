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

import { Page } from '@playwright/test';
import { expect, gotoSection, isoDate, test, unique } from './fixtures';

async function fillAppointment(
  page: Page,
  title: string,
  date: string,
  from: string,
  to: string,
): Promise<void> {
  await page.getByTestId('appointment-title').fill(title);
  await page.getByTestId('appointment-date').fill(date);
  await page.getByTestId('appointment-from').fill(from);
  await page.getByTestId('appointment-to').fill(to);
}

test('the double booking guard blocks a taken slot and can be overridden', async ({ page }) => {
  const day = isoDate(3);
  const first = unique('Client meeting');
  const second = unique('Dentist');
  const parallel = unique('Webinar');

  await gotoSection(page, '/calendar');

  // A first appointment in a free slot.
  await page.getByTestId('new-appointment').click();
  await fillAppointment(page, first, day, '10:00', '11:00');
  await expect(page.getByTestId('no-conflict')).toBeVisible();
  await page.getByTestId('appointment-save').click();
  await expect(page.getByTestId('appointment-dialog')).toBeHidden();
  await expect(page.getByTestId('agenda')).toContainText(first);

  // An overlapping slot is warned about before saving, and then refused.
  await page.getByTestId('new-appointment').click();
  await fillAppointment(page, second, day, '10:30', '11:30');
  await expect(page.getByTestId('conflict-warning')).toContainText(first);

  await page.getByTestId('appointment-save').click();
  await expect(page.getByTestId('conflict-blocked')).toBeVisible();
  await expect(page.getByTestId('appointment-dialog')).toBeVisible();

  // Moving it to a free slot resolves the clash.
  await page.getByTestId('appointment-from').fill('11:00');
  await page.getByTestId('appointment-to').fill('12:00');
  await expect(page.getByTestId('no-conflict')).toBeVisible();
  await page.getByTestId('appointment-save').click();
  await expect(page.getByTestId('appointment-dialog')).toBeHidden();
  await expect(page.getByTestId('agenda')).toContainText(second);

  // A deliberate parallel booking is still possible.
  await page.getByTestId('new-appointment').click();
  await fillAppointment(page, parallel, day, '10:15', '10:45');
  await page.getByTestId('appointment-save').click();
  await expect(page.getByTestId('conflict-blocked')).toBeVisible();
  await page.getByTestId('appointment-save-anyway').click();

  await expect(page.getByTestId('appointment-dialog')).toBeHidden();
  await expect(page.getByTestId('agenda')).toContainText(parallel);
});

test('back to back appointments are not treated as a clash', async ({ page }) => {
  const day = isoDate(5);
  const morning = unique('Morning');
  const noon = unique('Noon');

  await gotoSection(page, '/calendar');

  await page.getByTestId('new-appointment').click();
  await fillAppointment(page, morning, day, '09:00', '10:00');
  await page.getByTestId('appointment-save').click();
  await expect(page.getByTestId('appointment-dialog')).toBeHidden();

  await page.getByTestId('new-appointment').click();
  await fillAppointment(page, noon, day, '10:00', '11:00');
  await expect(page.getByTestId('no-conflict')).toBeVisible();
  await page.getByTestId('appointment-save').click();

  await expect(page.getByTestId('appointment-dialog')).toBeHidden();
  await expect(page.getByTestId('agenda')).toContainText(noon);
});

test('an end before the start is refused before the form can be sent', async ({ page }) => {
  await gotoSection(page, '/calendar');

  await page.getByTestId('new-appointment').click();
  await fillAppointment(page, unique('Backwards'), isoDate(7), '15:00', '14:00');

  await expect(page.getByTestId('end-before-start')).toBeVisible();
  await expect(page.getByTestId('appointment-save')).toBeDisabled();
});

test('an existing appointment can be edited without clashing with itself', async ({ page }) => {
  const day = isoDate(9);
  const title = unique('Review');

  await gotoSection(page, '/calendar');
  await page.getByTestId('new-appointment').click();
  await fillAppointment(page, title, day, '13:00', '14:00');
  await page.getByTestId('appointment-save').click();
  await expect(page.getByTestId('appointment-dialog')).toBeHidden();

  const row = page.getByTestId('appointment-row').filter({ hasText: title });
  await row.getByRole('button', { name: 'Edit' }).click();

  await expect(page.getByTestId('appointment-from')).toHaveValue('13:00');
  await expect(page.getByTestId('no-conflict')).toBeVisible();

  await page.getByTestId('appointment-to').fill('15:00');
  await page.getByTestId('appointment-save').click();

  await expect(page.getByTestId('appointment-dialog')).toBeHidden();
  await expect(page.getByTestId('appointment-row').filter({ hasText: title })).toContainText(
    '120 min',
  );
});
