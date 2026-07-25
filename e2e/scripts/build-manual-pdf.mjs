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

/*
 * Renders the HTML manuals to PDF.
 *
 * The HTML pages are the source; this only prints them, through the print stylesheet that lives
 * in each page. Run it after changing the manual text or regenerating the screenshots:
 *
 *   node e2e/scripts/build-manual-pdf.mjs
 *
 * It lives under e2e so it can borrow the Playwright and Chromium that the end-to-end suite
 * already installs, rather than carrying a dependency of its own.
 */

import { chromium } from '@playwright/test';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const MANUAL_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', 'docs', 'manual');

const editions = [
  { html: 'index.html', pdf: 'small-crm-manual-en.pdf', footer: 'Small CRM — user manual' },
  { html: 'index.de.html', pdf: 'small-crm-manual-de.pdf', footer: 'Small CRM — Benutzerhandbuch' },
];

const browser = await chromium.launch();

for (const edition of editions) {
  const page = await browser.newPage();
  await page.goto(`file://${MANUAL_DIR}/${edition.html}`, { waitUntil: 'load' });

  // Switch to the print stylesheet, then wait for every screenshot to finish decoding: an image
  // still loading when the page is measured lands on the wrong page or comes out blank.
  await page.emulateMedia({ media: 'print' });
  await page.evaluate(() =>
    Promise.all(Array.from(document.images).map((image) => image.decode().catch(() => {}))),
  );
  await page.waitForTimeout(600);

  await page.pdf({
    path: `${MANUAL_DIR}/${edition.pdf}`,
    format: 'A4',
    printBackground: true,
    margin: { top: '18mm', bottom: '18mm', left: '18mm', right: '18mm' },
    displayHeaderFooter: true,
    headerTemplate: '<div></div>',
    footerTemplate: `
      <div style="width:100%;font-size:8pt;color:#6f7c88;padding:0 18mm;
                  font-family:system-ui,-apple-system,'Segoe UI',Roboto,Arial,sans-serif;
                  display:flex;justify-content:space-between;">
        <span>${edition.footer}</span>
        <span><span class="pageNumber"></span> / <span class="totalPages"></span></span>
      </div>`,
  });

  await page.close();
  console.log(`wrote docs/manual/${edition.pdf}`);
}

await browser.close();
