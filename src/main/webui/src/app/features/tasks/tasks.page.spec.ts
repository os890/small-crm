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
import { CrmTask } from '../../core/models';
import { PageHarness, renderPage } from '../../testing/page-harness';
import { TasksPage } from './tasks.page';

const OPEN: CrmTask = {
  id: 1,
  title: 'Send the offer',
  done: false,
  priority: 'HIGH',
  dueDate: '2026-07-20',
  overdue: true,
};

async function open(tasks: CrmTask[] = [OPEN]): Promise<PageHarness<TasksPage>> {
  const harness = renderPage(TasksPage);
  await harness.settle();
  harness.flushGet('/api/tasks', tasks);
  harness.flushGet('/api/contacts', []);
  harness.flushGet('/api/deals', []);
  await harness.settle();
  return harness;
}

describe('TasksPage', () => {
  it('marks an overdue task so it stands out', async () => {
    const harness = await open();

    expect(harness.query('task-rows')?.textContent).toContain('Overdue');
    expect(harness.query('task-rows')?.textContent).toContain('High');
  });

  it('says so when nothing is open', async () => {
    const harness = await open([]);

    expect(harness.text('tasks-empty')).toBe('No to-dos. Enjoy the quiet.');
  });

  it('shows tasks without a due date as such', async () => {
    const harness = await open([{ id: 2, title: 'Someday', done: false }]);

    expect(harness.query('task-rows')?.textContent).toContain('No due date');
  });

  it('starts with only the open tasks and can include the finished ones', async () => {
    const harness = await open();

    const first = harness.http.match((c) => c.url === '/api/tasks');
    expect(first).toHaveLength(0);

    await harness.click('tasks-open-only');
    const request = harness.http.expectOne((c) => c.url === '/api/tasks');
    expect(request.request.params.get('openOnly')).toBe('false');
    request.flush([OPEN, { id: 2, title: 'Already done', done: true }]);
    await harness.settle();

    expect(harness.query('task-rows')?.textContent).toContain('Already done');
  });

  it('ticks a task off through the checkbox', async () => {
    const harness = await open();

    await harness.click('task-done-1');

    const request = harness.http.expectOne((c) => c.url === '/api/tasks/1/done');
    expect(request.request.params.get('value')).toBe('true');
    request.flush({ ...OPEN, done: true });
    await harness.settle();
    harness.flushGet('/api/tasks', []);
    await harness.settle();

    expect(harness.query('tasks-empty')).not.toBeNull();
  });

  it('reopens a finished task', async () => {
    const harness = await open([{ ...OPEN, done: true, overdue: false }]);

    await harness.click('task-done-1');

    expect(
      harness.http.expectOne((c) => c.url === '/api/tasks/1/done').request.params.get('value'),
    ).toBe('false');
  });

  it('creates a task with the normal priority by default', async () => {
    const harness = await open([]);

    await harness.click('new-task');
    await harness.type('task-title', 'Call the accountant');
    await harness.click('task-save');

    const created = harness.http.expectOne('/api/tasks');
    expect(created.request.body).toMatchObject({
      title: 'Call the accountant',
      done: false,
      priority: 'NORMAL',
    });
    created.flush({ id: 5, title: 'Call the accountant', done: false });
    await harness.settle();
    harness.flushGet('/api/tasks', []);
    await harness.settle();

    expect(harness.query('task-dialog')).toBeNull();
  });

  it('shows a rejected title on the field', async () => {
    const harness = await open([]);

    await harness.click('new-task');
    await harness.click('task-save');
    harness.http
      .expectOne('/api/tasks')
      .flush(
        { code: 'VALIDATION_FAILED', message: 'invalid', details: { title: 'must not be blank' } },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.settle();

    expect(harness.query('task-dialog')?.textContent).toContain('must not be blank');
  });
});
