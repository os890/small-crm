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
import { PageHarness, renderPage } from '../testing/page-harness';
import { PagerComponent } from './pager.component';

interface State {
  page: number;
  size: number;
  total: number;
  shown: number;
}

async function render(state: State): Promise<PageHarness<PagerComponent>> {
  const harness = renderPage(PagerComponent);
  for (const [name, value] of Object.entries(state)) {
    harness.fixture.componentRef.setInput(name, value);
  }
  await harness.settle();
  return harness;
}

describe('PagerComponent', () => {
  it('stays out of the way while everything fits on one page', async () => {
    const harness = await render({ page: 0, size: 50, total: 12, shown: 12 });

    expect(harness.query('pager')).toBeNull();
  });

  it('says which records are on screen and how many there are', async () => {
    const harness = await render({ page: 0, size: 50, total: 812, shown: 50 });

    expect(harness.text('pager-range')).toBe('Showing 1–50 of 812');
  });

  it('counts from the right place on a later page', async () => {
    const harness = await render({ page: 3, size: 50, total: 812, shown: 50 });

    expect(harness.text('pager-range')).toBe('Showing 151–200 of 812');
  });

  it('handles a short last page without claiming records that are not there', async () => {
    const harness = await render({ page: 16, size: 50, total: 812, shown: 12 });

    expect(harness.text('pager-range')).toBe('Showing 801–812 of 812');
    expect(harness.query<HTMLButtonElement>('pager-next')?.disabled).toBe(true);
  });

  it('cannot go back from the first page or forward from the last', async () => {
    const first = await render({ page: 0, size: 50, total: 120, shown: 50 });
    expect(first.query<HTMLButtonElement>('pager-previous')?.disabled).toBe(true);
    expect(first.query<HTMLButtonElement>('pager-next')?.disabled).toBe(false);

    const last = await render({ page: 2, size: 50, total: 120, shown: 20 });
    expect(last.query<HTMLButtonElement>('pager-previous')?.disabled).toBe(false);
    expect(last.query<HTMLButtonElement>('pager-next')?.disabled).toBe(true);
  });

  it('asks for the neighbouring page when a button is used', async () => {
    const harness = await render({ page: 2, size: 50, total: 812, shown: 50 });
    const asked: number[] = [];
    harness.component.goTo.subscribe((page) => asked.push(page));

    await harness.click('pager-next');
    await harness.click('pager-previous');

    expect(asked).toEqual([3, 1]);
  });
});
