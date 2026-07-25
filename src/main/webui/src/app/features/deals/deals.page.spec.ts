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
import { Deal } from '../../core/models';
import { PageHarness, renderPage } from '../../testing/page-harness';
import { DealsPage } from './deals.page';

const LEAD: Deal = {
  id: 1,
  title: 'Website relaunch',
  stage: 'LEAD',
  amount: 1000,
  currency: 'EUR',
};
const PROPOSAL: Deal = {
  id: 2,
  title: 'Support contract',
  stage: 'PROPOSAL',
  amount: 500,
  currency: 'EUR',
};
const WON: Deal = { id: 3, title: 'Logo design', stage: 'WON', amount: 250, currency: 'EUR' };

async function open(deals: Deal[] = [LEAD, PROPOSAL]): Promise<PageHarness<DealsPage>> {
  const harness = renderPage(DealsPage);
  await harness.settle();
  harness.flushGet('/api/deals', deals);
  harness.flushGet('/api/contacts', []);
  harness.flushGet('/api/companies', []);
  await harness.settle();
  return harness;
}

describe('DealsPage', () => {
  it('shows only the open stages while the filter is on', async () => {
    const harness = await open();

    expect(harness.query('column-LEAD')).not.toBeNull();
    expect(harness.query('column-PROPOSAL')).not.toBeNull();
    expect(harness.query('column-WON')).toBeNull();
  });

  it('puts each deal in the column of its stage', async () => {
    const harness = await open();

    expect(harness.query('column-LEAD')?.textContent).toContain('Website relaunch');
    expect(harness.query('column-PROPOSAL')?.textContent).toContain('Support contract');
  });

  it('shows each deal at its own stage in the dropdown', async () => {
    const harness = await open();

    expect(harness.query<HTMLSelectElement>('deal-move-1')?.value).toBe('LEAD');
    expect(harness.query<HTMLSelectElement>('deal-move-2')?.value).toBe('PROPOSAL');
  });

  it('sums the value per column', async () => {
    const harness = await open([LEAD, { ...PROPOSAL, stage: 'LEAD', id: 4 }]);

    expect(harness.query('column-LEAD')?.textContent).toContain('1,500.00');
  });

  it('adds the closed stages when the filter is turned off', async () => {
    const harness = await open();

    await harness.click('deals-open-only');
    const request = harness.http.expectOne((c) => c.url === '/api/deals');
    expect(request.request.params.get('openOnly')).toBe('false');
    request.flush([LEAD, WON]);
    await harness.settle();

    expect(harness.query('column-WON')?.textContent).toContain('Logo design');
  });

  it('says so when there is nothing in the pipeline', async () => {
    const harness = await open([]);

    expect(harness.query('deals-empty')).not.toBeNull();
  });

  it('moves a deal to the stage picked in its dropdown', async () => {
    const harness = await open();

    const select = harness.query<HTMLSelectElement>('deal-move-1');
    select!.value = 'PROPOSAL';
    select!.dispatchEvent(new Event('change'));
    await harness.settle();

    const move = harness.http.expectOne((c) => c.url === '/api/deals/1/stage');
    expect(move.request.params.get('value')).toBe('PROPOSAL');
    move.flush({});
    await harness.settle();
    harness.flushGet('/api/deals', [{ ...LEAD, stage: 'PROPOSAL' }]);
    await harness.settle();

    expect(harness.query('column-PROPOSAL')?.textContent).toContain('Website relaunch');
  });

  it('does not call the server when the dropdown is set to the current stage', async () => {
    const harness = await open();

    const select = harness.query<HTMLSelectElement>('deal-move-1');
    select!.value = 'LEAD';
    select!.dispatchEvent(new Event('change'));
    await harness.settle();

    harness.http.verify();
  });

  it('creates a deal that starts as a lead in euro', async () => {
    const harness = await open([]);

    await harness.click('new-deal');
    await harness.type('deal-title', 'New engagement');
    await harness.click('deal-save');

    const created = harness.http.expectOne('/api/deals');
    expect(created.request.body).toMatchObject({
      title: 'New engagement',
      stage: 'LEAD',
      currency: 'EUR',
    });
    created.flush({ id: 9, title: 'New engagement', stage: 'LEAD' });
    await harness.settle();
    harness.flushGet('/api/deals', []);
    await harness.settle();

    expect(harness.query('deal-dialog')).toBeNull();
  });

  it('marks a rejected amount on the field itself', async () => {
    const harness = await open([]);

    await harness.click('new-deal');
    await harness.click('deal-save');
    harness.http.expectOne('/api/deals').flush(
      {
        code: 'VALIDATION_FAILED',
        message: 'invalid',
        details: { amount: 'must be greater than or equal to 0' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await harness.settle();

    expect(harness.query('deal-dialog')?.textContent).toContain(
      'must be greater than or equal to 0',
    );
  });
});
