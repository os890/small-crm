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

import { defineConfig, devices } from '@playwright/test';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * The end-to-end suite drives the packaged application: one Quarkus process serving both the
 * REST API and the built Angular bundle, exactly as a user would run it.
 */

const PORT = Number(process.env['E2E_PORT'] ?? 8099);
const BASE_URL = process.env['E2E_BASE_URL'] ?? `http://localhost:${PORT}`;

/** Bootstrap password of the fresh installation the suite starts from. */
export const INITIAL_PASSWORD = 'changeit';

/** Password the setup step switches the administrator to. */
export const ADMIN_PASSWORD = 'e2e-admin-secret';

/** A throwaway database directory, so every run starts from an empty installation. */
const dataDir = mkdtempSync(join(tmpdir(), 'small-crm-e2e-'));

export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  fullyParallel: false,
  // The suite shares one database, so the specs run one after another.
  workers: 1,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  timeout: 30_000,
  expect: { timeout: 7_000 },

  reporter: [
    ['list'],
    [
      'monocart-reporter',
      {
        name: 'Small CRM end-to-end tests',
        outputFile: './playwright-report/index.html',
        coverage: {
          // Only the application's own bundles; the framework runtime is not under test here.
          entryFilter: (entry: { url: string }) => entry.url.includes(BASE_URL),
          sourceFilter: (sourcePath: string) => sourcePath.includes('src/app/'),
          reports: [['v8'], ['console-summary'], ['lcovonly']],
          outputDir: './coverage',
        },
      },
    ],
  ],

  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    locale: 'en-GB',
    timezoneId: 'Europe/Vienna',
  },

  projects: [
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'chromium',
      dependencies: ['setup'],
      use: {
        ...devices['Desktop Chrome'],
        storageState: '.auth/admin.json',
      },
    },
  ],

  webServer: process.env['E2E_BASE_URL']
    ? undefined
    : {
        command: 'java -jar ../target/quarkus-app/quarkus-run.jar',
        url: `${BASE_URL}/q/health/ready`,
        timeout: 120_000,
        reuseExistingServer: false,
        stdout: 'pipe',
        stderr: 'pipe',
        env: {
          QUARKUS_HTTP_PORT: String(PORT),
          SMALLCRM_DATA_DIR: dataDir,
          SMALLCRM_BOOTSTRAP_ADMIN_USERNAME: 'admin',
          SMALLCRM_BOOTSTRAP_ADMIN_PASSWORD: INITIAL_PASSWORD,
          SMALLCRM_SESSION_KEY: 'end-to-end-session-key-at-least-16-chars',
        },
      },
});
