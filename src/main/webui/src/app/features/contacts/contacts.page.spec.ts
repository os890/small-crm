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
import { Company, Contact } from '../../core/models';
import { ConfirmService } from '../../shared/confirm.service';
import { PageHarness, renderPage } from '../../testing/page-harness';
import { ContactsPage } from './contacts.page';

const MARIA: Contact = {
  id: 1,
  firstName: 'Maria',
  lastName: 'Huber',
  displayName: 'Maria Huber',
  email: 'maria@example.org',
  companyId: 5,
  companyName: 'Muster GmbH',
  tags: ['vip'],
};

const MUSTER: Company = { id: 5, name: 'Muster GmbH' };

async function open(contacts: Contact[] = [MARIA]): Promise<PageHarness<ContactsPage>> {
  const harness = renderPage(ContactsPage);
  await harness.settle();
  harness.flushGet('/api/contacts', contacts);
  await harness.settle();
  return harness;
}

describe('ContactsPage', () => {
  it('lists contacts with their company and tags', async () => {
    const harness = await open();

    const rows = harness.query('contact-rows')?.textContent ?? '';
    expect(rows).toContain('Maria Huber');
    expect(rows).toContain('Muster GmbH');
    expect(rows).toContain('vip');
  });

  it('invites the user to add the first contact when there are none', async () => {
    const harness = await open([]);

    expect(harness.text('contacts-empty')).toBe(
      'No contacts yet. Add the first one to get started.',
    );
  });

  it('searches after the user stops typing', async () => {
    const harness = await open();

    await harness.type('contact-search', 'hub');
    await harness.wait(300);
    const request = harness.http.expectOne((c) => c.url === '/api/contacts');

    expect(request.request.params.get('search')).toBe('hub');
    request.flush([MARIA]);
    await harness.settle();
  });

  it('turns the comma separated tag field into a list when saving', async () => {
    const harness = await open([]);

    await harness.click('new-contact');
    await harness.type('contact-first-name', 'Bernd');
    await harness.type('contact-last-name', 'Aigner');
    await harness.type('contact-tags', 'vip, lead , vip');
    await harness.click('contact-save');

    const created = harness.http.expectOne('/api/contacts');
    expect(created.request.body.tags).toEqual(['vip', 'lead']);
    created.flush({ id: 2, firstName: 'Bernd', lastName: 'Aigner' });
    await harness.settle();
    harness.flushGet('/api/contacts', []);
    await harness.settle();
  });

  it('prefills the dialog from the row being edited', async () => {
    const harness = await open();

    (harness.fixture.nativeElement.querySelector('tbody .btn') as HTMLElement).click();
    await harness.settle();

    expect(harness.query<HTMLInputElement>('contact-first-name')?.value).toBe('Maria');
    expect(harness.query<HTMLInputElement>('contact-tags')?.value).toBe('vip');
  });

  it('looks companies up as they are typed instead of loading them all', async () => {
    const harness = await open();

    await harness.click('new-contact');
    // Nothing has been fetched merely by opening the dialog — that is the whole point of the
    // change: the previous version pulled every company in the installation to fill a dropdown.
    harness.http.expectNone((request) => request.url === '/api/companies');

    await harness.type('contact-company', 'must');
    await harness.wait(250);

    const lookup = harness.http.expectOne((request) => request.url === '/api/companies');
    expect(lookup.request.params.get('search')).toBe('must');
    expect(lookup.request.params.get('size')).toBe('10');
    lookup.flush([MUSTER]);
    await harness.settle();

    expect(harness.query('contact-company-option')?.textContent).toContain('Muster GmbH');
  });

  it('keeps the dialog usable when the company lookup fails', async () => {
    const harness = await open();

    await harness.click('new-contact');
    await harness.type('contact-company', 'must');
    await harness.wait(250);
    harness.http
      .expectOne((request) => request.url === '/api/companies')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await harness.settle();

    expect(harness.query('contact-dialog')).not.toBeNull();
    expect(harness.query('contact-company-empty')).not.toBeNull();
  });

  it('shows the company a contact already has without a lookup', async () => {
    const harness = await open();

    await harness.click('contact-edit-1');

    expect(harness.query<HTMLInputElement>('contact-company')?.value).toBe('Muster GmbH');
    harness.http.expectNone((request) => request.url === '/api/companies');
  });

  it('names the contact in the delete prompt', async () => {
    const harness = await open();
    const confirm = TestBed.inject(ConfirmService);

    (harness.fixture.nativeElement.querySelector('tbody .btn-danger') as HTMLElement).click();
    await harness.settle();

    expect(confirm.request()?.question).toContain('Maria Huber');

    confirm.answer(true);
    await harness.settle();
    harness.http.expectOne('/api/contacts/1').flush(null);
    await harness.settle();
    harness.flushGet('/api/contacts', []);
    await harness.settle();

    expect(harness.query('contacts-empty')).not.toBeNull();
  });

  it('shows rejected fields on the inputs they belong to', async () => {
    const harness = await open([]);

    await harness.click('new-contact');
    await harness.click('contact-save');
    harness.http.expectOne('/api/contacts').flush(
      {
        code: 'VALIDATION_FAILED',
        message: 'invalid',
        details: { firstName: 'must not be blank', lastName: 'must not be blank' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await harness.settle();

    const dialog = harness.query('contact-dialog')?.textContent ?? '';
    expect(dialog.match(/must not be blank/g)).toHaveLength(2);
  });
});
