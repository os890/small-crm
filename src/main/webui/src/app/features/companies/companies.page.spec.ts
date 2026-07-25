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
import { ConfirmService } from '../../shared/confirm.service';
import { PageHarness, renderPage } from '../../testing/page-harness';
import { CompaniesPage } from './companies.page';

const MUSTER = { id: 1, name: 'Muster GmbH', city: 'Graz', email: 'office@muster.example' };

async function open(companies: unknown[] = [MUSTER]): Promise<PageHarness<CompaniesPage>> {
  const harness = renderPage(CompaniesPage);
  await harness.settle();
  harness.flushGet('/api/companies', companies);
  await harness.settle();
  return harness;
}

describe('CompaniesPage', () => {
  it('lists what the server returned', async () => {
    const harness = await open();

    expect(harness.query('company-rows')?.textContent).toContain('Muster GmbH');
    expect(harness.query('companies-empty')).toBeNull();
  });

  it('explains an empty list differently from an empty search result', async () => {
    const harness = await open([]);
    expect(harness.text('companies-empty')).toBe('No companies yet.');

    await harness.type('company-search', 'nothing matches');
    await harness.wait(300);
    harness.flushGet('/api/companies', []);
    await harness.settle();

    expect(harness.text('companies-empty')).toBe('No entries match your search.');
    harness.http.verify();
  });

  it('sends the search term to the server after the user stops typing', async () => {
    const harness = await open();

    await harness.type('company-search', 'gra');
    await harness.wait(300);
    const request = harness.http.expectOne((candidate) => candidate.url === '/api/companies');

    expect(request.request.params.get('search')).toBe('gra');
    request.flush([]);
    await harness.settle();
  });

  it('creates a company and reloads the list', async () => {
    const harness = await open([]);

    await harness.click('new-company');
    expect(harness.query('company-dialog')).not.toBeNull();

    await harness.type('company-name', 'New Ltd');
    await harness.click('company-save');

    const created = harness.http.expectOne('/api/companies');
    expect(created.request.method).toBe('POST');
    expect(created.request.body.name).toBe('New Ltd');
    created.flush({ id: 2, name: 'New Ltd' });
    await harness.settle();

    harness.flushGet('/api/companies', [{ id: 2, name: 'New Ltd' }]);
    await harness.settle();

    expect(harness.query('company-dialog')).toBeNull();
    expect(harness.query('company-rows')?.textContent).toContain('New Ltd');
  });

  it('shows a rejected field next to the input rather than only as a toast', async () => {
    const harness = await open([]);

    await harness.click('new-company');
    await harness.click('company-save');

    harness.http.expectOne('/api/companies').flush(
      {
        code: 'VALIDATION_FAILED',
        message: 'invalid',
        details: { name: 'must not be blank' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await harness.settle();

    expect(harness.query('company-dialog')?.textContent).toContain('must not be blank');
  });

  it('asks before deleting and does nothing when the prompt is dismissed', async () => {
    const harness = await open();
    const confirm = TestBed.inject(ConfirmService);

    const deleteButton = harness.fixture.nativeElement.querySelector(
      'tbody .btn-danger',
    ) as HTMLElement;
    deleteButton.click();
    await harness.settle();

    expect(confirm.request()?.question).toContain('Muster GmbH');
    confirm.answer(false);
    await harness.settle();

    harness.http.verify();
  });

  it('deletes once the prompt is accepted', async () => {
    const harness = await open();
    const confirm = TestBed.inject(ConfirmService);

    (harness.fixture.nativeElement.querySelector('tbody .btn-danger') as HTMLElement).click();
    await harness.settle();
    confirm.answer(true);
    await harness.settle();

    harness.http.expectOne('/api/companies/1').flush(null);
    await harness.settle();
    harness.flushGet('/api/companies', []);
    await harness.settle();

    expect(harness.query('companies-empty')).not.toBeNull();
  });
});
