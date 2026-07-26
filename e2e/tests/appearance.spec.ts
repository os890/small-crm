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

import { expect, gotoSection, test } from './fixtures';

/**
 * That the application is actually styled.
 *
 * <p>This exists because it once was not, in the packaged build, on every screen — and the whole
 * suite stayed green. Angular's production build deferred the stylesheet with
 * `media="print" onload="this.media='all'"`, the Content-Security-Policy refused to run the
 * inline handler, and the sheet stayed print-only: parsed, present in `document.styleSheets`,
 * applied to nothing. Every test asserted on text and behaviour, which were unaffected, so
 * nothing noticed until someone looked at a screenshot.
 *
 * <p>Checking a computed colour is a blunt instrument, but it is the one thing that fails
 * whatever the cause — a blocked stylesheet, a missing build step, a bad Content-Type.
 */
test('the stylesheet is applied, not merely served', async ({ browser }) => {
  // A context of its own: the shared one is already signed in, and /login would redirect away.
  const context = await browser.newContext({ storageState: undefined });
  const page = await context.newPage();
  await page.goto('/login');
  await page.getByTestId('login-form').waitFor();

  // No stylesheet may be left waiting for something to switch its media on. That switch is an
  // inline event handler, which the CSP blocks by design.
  const media = await page.evaluate(() =>
    [...document.querySelectorAll('link[rel="stylesheet"]')].map((link) =>
      link.getAttribute('media'),
    ),
  );
  expect(media.every((value) => value === null || value === 'all')).toBe(true);

  // The primary button is the loudest thing on the screen when the styles are there, and a
  // default grey browser button when they are not.
  const button = page.getByTestId('login-submit');
  await expect(button).toHaveCSS('background-color', 'rgb(31, 111, 235)');
  await expect(button).toHaveCSS('min-height', '42px');

  // The card the form sits in draws a surface and a border; unstyled, it is neither.
  const card = page.getByTestId('login-form');
  await expect(card).toHaveCSS('background-color', 'rgb(255, 255, 255)');
  await expect(card).not.toHaveCSS('border-radius', '0px');

  await context.close();
});

test('the styles survive into the signed-in application', async ({ page }) => {
  // The shell and the feature screens carry their own component styles, which arrive through a
  // different route than the global sheet.
  await gotoSection(page, '/contacts');

  await expect(page.getByTestId('new-contact')).toHaveCSS('background-color', 'rgb(31, 111, 235)');
  const nav = page.getByTestId('nav-nav.contacts');
  await expect(nav).toBeVisible();
  await expect(nav).not.toHaveCSS('display', 'inline');
});
