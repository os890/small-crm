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
import { Appointment } from '../../core/models';
import { PageHarness, renderPage } from '../../testing/page-harness';
import { CalendarPage } from './calendar.page';

const MEETING: Appointment = {
  id: 1,
  title: 'Client meeting',
  startsAt: '2026-09-01T08:00:00Z',
  endsAt: '2026-09-01T09:00:00Z',
  location: 'Office',
};

async function open(appointments: Appointment[] = []): Promise<PageHarness<CalendarPage>> {
  const harness = renderPage(CalendarPage);
  await harness.settle();
  harness.flushGet('/api/appointments', appointments);
  harness.flushGet('/api/contacts', []);
  harness.flushGet('/api/deals', []);
  await harness.settle();
  return harness;
}

/** Answers the debounced availability lookup the dialog fires while the user types. */
async function answerConflictCheck(
  harness: PageHarness<CalendarPage>,
  conflicts: Appointment[],
): Promise<void> {
  await harness.wait(350);
  harness.http
    .match((request) => request.url === '/api/appointments/conflicts')
    .forEach((request) => request.flush(conflicts));
  await harness.settle();
}

describe('CalendarPage', () => {
  it('groups the agenda by day', async () => {
    const harness = await open([MEETING]);

    expect(harness.all('appointment-row')).toHaveLength(1);
    expect(harness.query('agenda')?.textContent).toContain('Client meeting');
    expect(harness.query('calendar-empty')).toBeNull();
  });

  it('says so when the period holds nothing', async () => {
    const harness = await open([]);

    expect(harness.text('calendar-empty')).toBe('No appointments in this period.');
  });

  it('reloads with a different window when another range is picked', async () => {
    const harness = await open([]);

    await harness.click('range-7');
    const request = harness.http.expectOne((c) => c.url === '/api/appointments');
    const from = new Date(request.request.params.get('from') as string);
    const to = new Date(request.request.params.get('to') as string);

    expect(Math.round((to.getTime() - from.getTime()) / 86_400_000)).toBe(7);
    request.flush([]);
    await harness.settle();
  });

  it('reports a free slot before the user saves', async () => {
    const harness = await open([]);

    await harness.click('new-appointment');
    await answerConflictCheck(harness, []);

    expect(harness.query('no-conflict')).not.toBeNull();
    expect(harness.query('conflict-warning')).toBeNull();
  });

  it('warns about an overlap while the user is still editing', async () => {
    const harness = await open([MEETING]);

    await harness.click('new-appointment');
    await answerConflictCheck(harness, [MEETING]);

    expect(harness.query('conflict-warning')?.textContent).toContain('Client meeting');
    expect(harness.query('no-conflict')).toBeNull();
  });

  it('refuses an end that is not after the start and skips the availability check', async () => {
    const harness = await open([]);

    await harness.click('new-appointment');
    await answerConflictCheck(harness, []);
    await harness.type('appointment-to', '00:00');

    expect(harness.query('end-before-start')).not.toBeNull();
    expect(harness.query('no-conflict')).toBeNull();
    expect(harness.query<HTMLButtonElement>('appointment-save')?.disabled).toBe(true);
    harness.http.verify();
  });

  it('keeps the length of the appointment when the start time moves', async () => {
    const harness = await open([]);

    await harness.click('new-appointment');
    await answerConflictCheck(harness, []);
    await harness.type('appointment-from', '14:00');
    await answerConflictCheck(harness, []);

    expect(harness.query<HTMLInputElement>('appointment-to')?.value).toBe('15:00');
  });

  it('sends the local date and times as instants', async () => {
    const harness = await open([]);

    await harness.click('new-appointment');
    await answerConflictCheck(harness, []);
    await harness.type('appointment-title', 'Client meeting');
    await harness.type('appointment-date', '2026-09-01');
    await harness.type('appointment-from', '10:00');
    await answerConflictCheck(harness, []);
    await harness.type('appointment-to', '11:00');
    await answerConflictCheck(harness, []);

    await harness.click('appointment-save');
    const request = harness.http.expectOne((c) => c.url === '/api/appointments');

    // Vienna is UTC+2 on that date.
    expect(request.request.body.startsAt).toBe('2026-09-01T08:00:00.000Z');
    expect(request.request.body.endsAt).toBe('2026-09-01T09:00:00.000Z');
    expect(request.request.params.get('allowConflict')).toBe('false');
    request.flush({});
    await harness.settle();
    harness.flushGet('/api/appointments', []);
    await harness.settle();
  });

  it('offers to book anyway after the server refuses a taken slot', async () => {
    const harness = await open([MEETING]);

    await harness.click('new-appointment');
    await answerConflictCheck(harness, []);
    await harness.type('appointment-title', 'Dentist');
    await harness.click('appointment-save');

    harness.http
      .expectOne((c) => c.url === '/api/appointments')
      .flush(
        {
          code: 'APPOINTMENT_CONFLICT',
          message: 'overlaps',
          details: { conflicts: [MEETING] },
        },
        { status: 409, statusText: 'Conflict' },
      );
    await harness.settle();

    expect(harness.query('conflict-blocked')).not.toBeNull();
    expect(harness.query('conflict-warning')?.textContent).toContain('Client meeting');
    expect(harness.query('appointment-dialog')).not.toBeNull();

    await harness.click('appointment-save-anyway');
    const retry = harness.http.expectOne((c) => c.url === '/api/appointments');

    expect(retry.request.params.get('allowConflict')).toBe('true');
    retry.flush({});
    await harness.settle();
    harness.flushGet('/api/appointments', [MEETING]);
    await harness.settle();

    expect(harness.query('appointment-dialog')).toBeNull();
  });

  it('opens an existing appointment with its slot already filled in', async () => {
    const harness = await open([MEETING]);

    (harness.fixture.nativeElement.querySelector('.slot .btn') as HTMLElement).click();
    await harness.settle();

    expect(harness.query<HTMLInputElement>('appointment-title')?.value).toBe('Client meeting');
    expect(harness.query<HTMLInputElement>('appointment-date')?.value).toBe('2026-09-01');
    // 08:00 UTC is 10:00 in Vienna.
    expect(harness.query<HTMLInputElement>('appointment-from')?.value).toBe('10:00');

    await answerConflictCheck(harness, []);
    const check = harness.http.match((c) => c.url === '/api/appointments/conflicts');
    expect(check).toHaveLength(0);
  });

  it('excludes the edited appointment from its own availability check', async () => {
    const harness = await open([MEETING]);

    (harness.fixture.nativeElement.querySelector('.slot .btn') as HTMLElement).click();
    await harness.settle();
    await harness.wait(350);

    const request = harness.http.expectOne((c) => c.url === '/api/appointments/conflicts');
    expect(request.request.params.get('excludeId')).toBe('1');
    request.flush([]);
    await harness.settle();
  });

  it('survives a failing availability check without blocking the form', async () => {
    const harness = await open([]);

    await harness.click('new-appointment');
    await harness.wait(350);
    harness.http
      .match((c) => c.url === '/api/appointments/conflicts')
      .forEach((request) => request.flush(null, { status: 500, statusText: 'Server Error' }));
    await harness.settle();

    expect(harness.query('no-conflict')).toBeNull();
    expect(harness.query('conflict-warning')).toBeNull();
    expect(harness.query('appointment-dialog')).not.toBeNull();
  });
});
