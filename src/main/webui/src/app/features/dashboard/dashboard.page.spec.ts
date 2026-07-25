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

import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { Dashboard } from '../../core/models';
import { PageHarness, renderPage } from '../../testing/page-harness';
import { DashboardPage } from './dashboard.page';

const EMPTY: Dashboard = {
  contactCount: 0,
  companyCount: 0,
  openDealCount: 0,
  openDealValue: 0,
  overdueTasks: [],
  tasksDueToday: [],
  upcomingAppointments: [],
  recentInteractions: [],
};

async function open(summary: Dashboard = EMPTY): Promise<PageHarness<DashboardPage>> {
  const harness = renderPage(DashboardPage);
  await harness.settle();
  harness.flushGet('/api/dashboard', summary as unknown as Record<string, unknown>);
  await harness.settle();
  return harness;
}

describe('DashboardPage', () => {
  it('greets the signed-in user by name', async () => {
    const harness = renderPage(DashboardPage);
    TestBed.inject(AuthService).setUser({
      id: 1,
      username: 'admin',
      fullName: 'Maria Huber',
      email: null,
      roles: ['USER'],
      admin: false,
      active: true,
      mustChangePassword: false,
      createdAt: '2026-07-01T00:00:00Z',
    });
    await harness.settle();
    harness.flushGet('/api/dashboard', EMPTY as unknown as Record<string, unknown>);
    await harness.settle();

    expect(harness.fixture.nativeElement.textContent).toContain('Hello Maria Huber!');
  });

  it('reassures the user when nothing needs attention', async () => {
    const harness = await open();

    expect(harness.query('all-clear')).not.toBeNull();
    expect(harness.query('panel-overdue')).toBeNull();
    expect(harness.query('panel-today')).toBeNull();
  });

  it('shows the counts as tiles', async () => {
    const harness = await open({ ...EMPTY, contactCount: 12, openDealValue: 4500 });

    expect(harness.text('tile-contacts')).toBe('12');
    expect(harness.fixture.nativeElement.textContent).toContain('€4,500.00');
  });

  it('lists what is overdue and what is due today, and drops the all-clear', async () => {
    const harness = await open({
      ...EMPTY,
      overdueTasks: [{ id: 1, title: 'Chase the invoice', done: false, dueDate: '2026-07-20' }],
      tasksDueToday: [{ id: 2, title: 'Call the accountant', done: false, priority: 'HIGH' }],
    });

    expect(harness.query('all-clear')).toBeNull();
    expect(harness.query('panel-overdue')?.textContent).toContain('Chase the invoice');
    expect(harness.query('panel-today')?.textContent).toContain('Call the accountant');
    expect(harness.query('panel-today')?.textContent).toContain('High');
  });

  it('shows the next appointments and the recent activity', async () => {
    const harness = await open({
      ...EMPTY,
      upcomingAppointments: [
        {
          id: 1,
          title: 'Client meeting',
          startsAt: '2026-09-01T08:00:00Z',
          endsAt: '2026-09-01T09:00:00Z',
        },
      ],
      recentInteractions: [
        {
          id: 1,
          type: 'CALL',
          subject: 'Kickoff call',
          occurredAt: '2026-08-30T08:00:00Z',
          contactId: 1,
          contactName: 'Maria Huber',
        },
      ],
    });

    expect(harness.query('panel-upcoming')?.textContent).toContain('Client meeting');
    expect(harness.query('panel-recent')?.textContent).toContain('Maria Huber');
  });

  it('says the panels are empty rather than showing blank boxes', async () => {
    const harness = await open();

    expect(harness.query('panel-upcoming')?.textContent).toContain(
      'No appointments in this period.',
    );
    expect(harness.query('panel-recent')?.textContent).toContain('Nothing here yet.');
  });
});
