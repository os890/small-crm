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

import { describe, expect, it, vi } from 'vitest';
import { PageHarness, renderPage } from '../testing/page-harness';
import { EntityPickerComponent, PickerOption, PickerResult } from './entity-picker.component';

const CONTACTS: PickerOption[] = [
  { id: 1, label: 'Maria Huber', hint: 'Muster GmbH' },
  { id: 2, label: 'Jonas Berger', hint: null },
];

function result(options: PickerOption[], total = options.length): PickerResult {
  return { options, total };
}

async function render(
  search: (term: string) => Promise<PickerResult>,
  state: { value?: number | null; valueLabel?: string | null } = {},
): Promise<PageHarness<EntityPickerComponent>> {
  const harness = renderPage(EntityPickerComponent);
  harness.fixture.componentRef.setInput('label', 'Contact');
  harness.fixture.componentRef.setInput('testId', 'pick');
  harness.fixture.componentRef.setInput('search', search);
  harness.fixture.componentRef.setInput('value', state.value ?? null);
  harness.fixture.componentRef.setInput('valueLabel', state.valueLabel ?? null);
  await harness.settle();
  return harness;
}

/** Types into the field and waits out the lookup debounce. */
async function type(harness: PageHarness<EntityPickerComponent>, term: string): Promise<void> {
  await harness.type('pick', term);
  await harness.wait(250);
}

describe('EntityPickerComponent', () => {
  it('shows what is already chosen without looking anything up', async () => {
    const search = vi.fn(async () => result([]));
    const harness = await render(search, { value: 1, valueLabel: 'Maria Huber' });

    expect(harness.query<HTMLInputElement>('pick')?.value).toBe('Maria Huber');
    expect(search).not.toHaveBeenCalled();
  });

  it('offers what matches once the user types', async () => {
    const harness = await render(async () => result(CONTACTS));

    await type(harness, 'ma');

    const options = harness.all('pick-option').map((option) => option.textContent?.trim());
    expect(options?.[0]).toContain('Maria Huber');
    expect(options?.[0]).toContain('Muster GmbH');
    expect(options).toHaveLength(2);
  });

  it('searches for what was typed, with the surrounding blanks removed', async () => {
    const search = vi.fn(async () => result(CONTACTS));
    const harness = await render(search);

    await type(harness, '  hub  ');

    expect(search).toHaveBeenCalledWith('hub');
  });

  it('waits for a pause in the typing instead of asking on every keystroke', async () => {
    const search = vi.fn(async () => result(CONTACTS));
    const harness = await render(search);

    await harness.type('pick', 'm');
    await harness.type('pick', 'ma');
    await harness.type('pick', 'mar');
    expect(search).not.toHaveBeenCalled();

    await harness.wait(250);
    expect(search).toHaveBeenCalledTimes(1);
    expect(search).toHaveBeenCalledWith('mar');
  });

  it('reports the record the user picks and puts its name in the field', async () => {
    const harness = await render(async () => result(CONTACTS));
    const picked: (PickerOption | null)[] = [];
    harness.component.selected.subscribe((option) => picked.push(option));

    await type(harness, 'ma');
    await harness.click('pick-option');

    expect(picked).toEqual([CONTACTS[0]]);
    expect(harness.query<HTMLInputElement>('pick')?.value).toBe('Maria Huber');
    expect(harness.query('pick-option')).toBeNull();
  });

  it('reports an empty field as no record at all', async () => {
    const harness = await render(async () => result(CONTACTS), {
      value: 1,
      valueLabel: 'Maria Huber',
    });
    const picked: (PickerOption | null)[] = [];
    harness.component.selected.subscribe((option) => picked.push(option));

    await harness.click('pick-clear');

    expect(picked).toEqual([null]);
    expect(harness.query<HTMLInputElement>('pick')?.value).toBe('');
  });

  it('abandons the chosen record as soon as the name is typed over', async () => {
    // Otherwise the field reads "Mar" while contact 1 is quietly still attached, and the wrong
    // record gets saved.
    const harness = await render(async () => result(CONTACTS), {
      value: 1,
      valueLabel: 'Maria Huber',
    });
    const picked: (PickerOption | null)[] = [];
    harness.component.selected.subscribe((option) => picked.push(option));

    await harness.type('pick', 'Mar');

    expect(picked).toEqual([null]);
  });

  it('says so when nothing matches, rather than showing an empty box', async () => {
    const harness = await render(async () => result([]));

    await type(harness, 'zzz');

    expect(harness.query('pick-empty')?.textContent).toContain('Nothing matches');
  });

  it('admits when it is only showing part of the matches', async () => {
    const harness = await render(async () => result(CONTACTS, 57));

    await type(harness, 'a');

    expect(harness.query('pick-more')?.textContent).toContain('55 more');
  });

  it('stays usable when the lookup fails', async () => {
    const harness = await render(async () => {
      throw new Error('offline');
    });

    await type(harness, 'ma');

    expect(harness.query('pick-empty')).not.toBeNull();
    expect(harness.query<HTMLInputElement>('pick')?.value).toBe('ma');
  });

  it('ignores a slow earlier lookup that lands after a newer one', async () => {
    // The same out-of-order hazard the list searches have: typing "mü" then "ller" must not
    // leave the suggestions for "mü" on screen.
    const search = vi
      .fn<(term: string) => Promise<PickerResult>>()
      .mockImplementationOnce(
        () => new Promise((resolve) => setTimeout(() => resolve(result([CONTACTS[0]])), 120)),
      )
      .mockImplementationOnce(async () => result([CONTACTS[1]]));
    const harness = await render(search);

    await type(harness, 'mü');
    await type(harness, 'müller');
    await harness.wait(200);

    const options = harness.all('pick-option').map((option) => option.textContent?.trim());
    expect(options).toHaveLength(1);
    expect(options[0]).toContain('Jonas Berger');
  });

  it('walks the list with the arrow keys and picks with Enter', async () => {
    const harness = await render(async () => result(CONTACTS));
    const picked: (PickerOption | null)[] = [];
    harness.component.selected.subscribe((option) => picked.push(option));

    await type(harness, 'a');
    const input = harness.query<HTMLInputElement>('pick');
    input?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    input?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    await harness.settle();
    input?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await harness.settle();

    expect(picked).toEqual([CONTACTS[1]]);
  });

  it('lets Enter submit the form when no suggestion is highlighted', async () => {
    const harness = await render(async () => result(CONTACTS));

    await type(harness, 'a');
    const event = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
    harness.query<HTMLInputElement>('pick')?.dispatchEvent(event);
    await harness.settle();

    expect(event.defaultPrevented).toBe(false);
  });

  it('closes the list on Escape without changing what is chosen', async () => {
    const harness = await render(async () => result(CONTACTS), {
      value: 1,
      valueLabel: 'Maria Huber',
    });
    const picked: (PickerOption | null)[] = [];
    harness.component.selected.subscribe((option) => picked.push(option));

    harness.query<HTMLInputElement>('pick')?.dispatchEvent(new Event('focus'));
    await harness.wait(250);
    expect(harness.query('pick-option')).not.toBeNull();

    harness
      .query<HTMLInputElement>('pick')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await harness.settle();

    expect(harness.query('pick-option')).toBeNull();
    expect(picked).toEqual([]);
    expect(harness.query<HTMLInputElement>('pick')?.value).toBe('Maria Huber');
  });
});
