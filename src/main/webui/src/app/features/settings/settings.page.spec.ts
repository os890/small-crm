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

import { describe, expect, it } from 'vitest';
import { GoogleStatus } from '../../core/models';
import { PageHarness, renderPage } from '../../testing/page-harness';
import { SettingsPage } from './settings.page';

const CONNECTED: GoogleStatus = {
  available: true,
  unavailableReason: '',
  connected: true,
  email: 'maria@example.org',
  connectedAt: '2026-07-01T08:00:00Z',
  resources: [
    { resource: 'CONTACTS', permitted: true, lastOkAt: '2026-07-26T08:00:00Z', failures: 0 },
    {
      resource: 'CALENDAR',
      permitted: true,
      lastOkAt: null,
      lastRunAt: '2026-07-26T08:00:00Z',
      lastError: 'Google answered 503',
      failures: 3,
    },
    { resource: 'TASKS', permitted: false, lastOkAt: null, failures: 0 },
  ],
};

async function open(status: GoogleStatus): Promise<PageHarness<SettingsPage>> {
  const harness = renderPage(SettingsPage);
  await harness.settle();
  harness.flushGet('/api/google/status', status as unknown as Record<string, unknown>);
  await harness.settle();
  return harness;
}

describe('SettingsPage', () => {
  it('offers to connect when the installation supports Google and the user has not', async () => {
    const harness = await open({
      available: true,
      unavailableReason: '',
      connected: false,
      resources: [],
    });

    expect(harness.query('google-connect')).not.toBeNull();
    expect(harness.query('google-disconnect')).toBeNull();
  });

  it('says so plainly when the installation is not set up for Google', async () => {
    const harness = await open({
      available: false,
      unavailableReason: 'SMALLCRM_TOKEN_KEY is not set.',
      connected: false,
      resources: [],
    });

    expect(harness.text('google-unavailable')).toContain('not set up for Google');
    // The reason is for whoever configures the installation, so it is shown rather than hidden.
    expect(harness.fixture.nativeElement.textContent).toContain('SMALLCRM_TOKEN_KEY');
    expect(harness.query('google-connect')).toBeNull();
  });

  it('shows which account is connected and how each sync is getting on', async () => {
    const harness = await open(CONNECTED);

    expect(harness.text('google-email')).toBe('maria@example.org');
    const rows = harness.query('google-resources')?.textContent ?? '';
    expect(rows).toContain('Working');
    // A failing resource is named as failing, with the count, rather than looking idle.
    expect(rows).toContain('Failing (3×)');
    // A scope the user declined is not a fault and must not read like one.
    expect(rows).toContain('Not granted');
    expect(rows).toContain('never');
  });

  it('reports what a sync actually did rather than only that it ran', async () => {
    const harness = await open(CONNECTED);

    await harness.click('google-sync');
    harness.http.expectOne('/api/google/sync').flush([
      {
        resource: 'CONTACTS',
        pulledIn: 2,
        pulledUpdated: 1,
        pulledDeleted: 0,
        pushedNew: 1,
        pushedUpdated: 0,
        readOnly: 4,
        skipped: 0,
        error: null,
      },
    ]);
    await harness.settle();
    harness.flushGet('/api/google/status', CONNECTED as unknown as Record<string, unknown>);
    await harness.settle();

    const report = harness.query('google-report')?.textContent ?? '';
    expect(report).toContain('3 in');
    expect(report).toContain('1 out');
    // The records Google owns more of are counted, so "nothing happened to those four" is
    // visible rather than silent.
    expect(report).toContain('4 managed in Google');
  });

  it('asks before disconnecting, and says what that does and does not delete', async () => {
    const harness = await open(CONNECTED);
    const { ConfirmService } = await import('../../shared/confirm.service');
    const { TestBed } = await import('@angular/core/testing');
    const confirm = TestBed.inject(ConfirmService);

    void harness.click('google-disconnect');
    await harness.settle();

    expect(confirm.request()?.question).toContain('Disconnect');
    expect(confirm.request()?.hint).toContain('withdrawn at Google');
  });
});
