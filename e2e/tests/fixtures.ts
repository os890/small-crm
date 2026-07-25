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

import { Page, test as base } from '@playwright/test';
import { addCoverageReport } from 'monocart-reporter';

/**
 * The shared test base.
 *
 * <p>Every test records V8 coverage of the browser bundles and hands it to the reporter, which
 * maps it back onto the TypeScript sources through the emitted source maps.
 */
export const test = base.extend<{ collectCoverage: void }>({
  collectCoverage: [
    async ({ page }, use) => {
      await page.coverage.startJSCoverage({ resetOnNavigation: false });
      await use();
      const coverage = await page.coverage.stopJSCoverage();
      // A few tests drive their own browser context and never touch the default page; there is
      // nothing to report for those, and handing over an empty result upsets the reporter.
      if (coverage.length > 0) {
        await addCoverageReport(coverage, test.info());
      }
    },
    { scope: 'test', auto: true },
  ],
});

export { expect } from '@playwright/test';

/** Suffix that keeps records created by one test from colliding with another. */
export function unique(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
}

/** Waits for the page to be signed in and showing the shell. */
export async function gotoSection(page: Page, path: string): Promise<void> {
  await page.goto(path);
  await page.getByTestId('signed-in-user').waitFor();
}

/** A date a fixed number of days from today, as the `yyyy-MM-dd` a date input expects. */
export function isoDate(offsetDays = 0): string {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
