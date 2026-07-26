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
import { Contact, CrmTask, Deal, Interaction } from '../../core/models';
import { ConfirmService } from '../../shared/confirm.service';
import { PageHarness, renderPage } from '../../testing/page-harness';
import { ContactDetailPage } from './contact-detail.page';

const MARIA: Contact = {
  id: 1,
  firstName: 'Maria',
  lastName: 'Huber',
  displayName: 'Maria Huber',
  email: 'maria@example.org',
  phone: '+43 1 234',
  position: 'Owner',
  companyName: 'Muster GmbH',
  tags: ['vip'],
  notes: 'Prefers e-mail.',
};

const CALL: Interaction = {
  id: 10,
  type: 'CALL',
  subject: 'Kickoff call',
  occurredAt: '2026-07-01T08:00:00Z',
  contactId: 1,
  notes: 'Agreed on the scope',
};

const DEAL: Deal = { id: 20, title: 'Website relaunch', stage: 'PROPOSAL', contactId: 1 };
const TASK: CrmTask = { id: 30, title: 'Send the offer', done: false, dueDate: '2026-08-01' };

interface Fixtures {
  contact?: Contact | null;
  interactions?: Interaction[];
  deals?: Deal[];
  tasks?: CrmTask[];
}

async function open(fixtures: Fixtures = {}): Promise<PageHarness<ContactDetailPage>> {
  const harness = renderPage(ContactDetailPage);
  harness.fixture.componentRef.setInput('id', '1');
  await harness.settle();

  const contact = fixtures.contact === undefined ? MARIA : fixtures.contact;
  if (contact) {
    harness.flushGet('/api/contacts/1', contact as unknown as Record<string, unknown>);
  } else {
    harness.http
      .match((request) => request.url === '/api/contacts/1')
      .forEach((request) => request.flush(null, { status: 404, statusText: 'Not Found' }));
  }
  harness.flushGet('/api/interactions', fixtures.interactions ?? []);
  harness.flushGet('/api/deals', fixtures.deals ?? []);
  harness.flushGet('/api/tasks', fixtures.tasks ?? []);
  await harness.settle();
  return harness;
}

describe('ContactDetailPage', () => {
  it('shows the contact with its details', async () => {
    const harness = await open();

    expect(harness.text('contact-name')).toBe('Maria Huber');
    const page = harness.fixture.nativeElement.textContent as string;
    expect(page).toContain('Muster GmbH');
    expect(page).toContain('maria@example.org');
    expect(page).toContain('vip');
    expect(page).toContain('Prefers e-mail.');
  });

  it('lists the logged activity, the deals and the open to-dos', async () => {
    const harness = await open({ interactions: [CALL], deals: [DEAL], tasks: [TASK] });

    expect(harness.all('interaction-row')).toHaveLength(1);
    const page = harness.fixture.nativeElement.textContent as string;
    expect(page).toContain('Kickoff call');
    expect(page).toContain('Website relaunch');
    expect(page).toContain('Proposal sent');
    expect(page).toContain('Send the offer');
  });

  it('asks the server for this contact\u2019s deals rather than sifting through all of them', async () => {
    const harness = renderPage(ContactDetailPage);
    harness.fixture.componentRef.setInput('id', '1');
    await harness.settle();

    const deals = harness.http.expectOne((request) => request.url === '/api/deals');
    expect(deals.request.params.get('contactId')).toBe('1');

    deals.flush([DEAL]);
    harness.flushGet('/api/contacts/1', MARIA as unknown as Record<string, unknown>);
    harness.flushGet('/api/interactions', []);
    harness.flushGet('/api/tasks', []);
    await harness.settle();

    expect(harness.fixture.nativeElement.textContent).toContain('Website relaunch');
  });

  it('logs a new activity against this contact', async () => {
    const harness = await open();

    await harness.click('new-interaction');
    await harness.type('interaction-subject', 'Follow-up call');
    await harness.type('interaction-when', '2026-07-20T10:30');
    await harness.click('interaction-save');

    const created = harness.http.expectOne('/api/interactions');
    expect(created.request.method).toBe('POST');
    expect(created.request.body).toMatchObject({
      subject: 'Follow-up call',
      contactId: 1,
      type: 'CALL',
    });
    // Entered as local Vienna time, sent as the matching instant.
    expect(created.request.body.occurredAt).toBe('2026-07-20T08:30:00.000Z');
    created.flush({ ...CALL, id: 11, subject: 'Follow-up call' });
    await harness.settle();

    harness.flushGet('/api/interactions', [{ ...CALL, id: 11, subject: 'Follow-up call' }]);
    await harness.settle();

    expect(harness.query('interaction-row')?.textContent).toContain('Follow-up call');
  });

  it('asks before removing a logged activity', async () => {
    const harness = await open({ interactions: [CALL] });
    const confirm = TestBed.inject(ConfirmService);

    (harness.query('interaction-row')?.querySelector('.btn-quiet') as HTMLElement).click();
    await harness.settle();

    expect(confirm.request()?.question).toContain('Kickoff call');
    confirm.answer(true);
    await harness.settle();

    harness.http.expectOne('/api/interactions/10').flush(null);
    await harness.settle();
    harness.flushGet('/api/interactions', []);
    await harness.settle();

    expect(harness.all('interaction-row')).toHaveLength(0);
  });

  it('says plainly when the contact is gone', async () => {
    const harness = await open({ contact: null });

    expect(harness.fixture.nativeElement.textContent).toContain('This entry no longer exists');
  });
});
