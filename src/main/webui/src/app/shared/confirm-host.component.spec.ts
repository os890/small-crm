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
import { PageHarness, renderPage } from '../testing/page-harness';
import { ConfirmHostComponent } from './confirm-host.component';
import { ConfirmService } from './confirm.service';

const QUESTION = {
  title: 'Are you sure?',
  question: 'Do you really want to delete “Maria Huber”?',
  hint: 'Her activity history goes with her.',
  confirmLabel: 'Delete',
  destructive: true,
};

async function open(): Promise<{
  harness: PageHarness<ConfirmHostComponent>;
  service: ConfirmService;
  answer: Promise<boolean>;
}> {
  const harness = renderPage(ConfirmHostComponent);
  await harness.settle();
  const service = TestBed.inject(ConfirmService);
  const answer = service.ask(QUESTION);
  await harness.settle();
  return { harness, service, answer };
}

function dialogOf(harness: PageHarness<ConfirmHostComponent>): HTMLDialogElement {
  return harness.fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
}

describe('ConfirmHostComponent', () => {
  it('opens as a modal alert dialog naming what is about to happen', async () => {
    const { harness } = await open();

    const dialog = dialogOf(harness);
    expect(dialog.open).toBe(true);
    // alertdialog, not the element's implicit dialog: this always asks a question that has to
    // be answered before anything else can happen.
    expect(dialog.getAttribute('role')).toBe('alertdialog');
    expect(dialog.textContent).toContain('Maria Huber');
    expect(dialog.textContent).toContain('Her activity history goes with her.');
    expect(harness.text('confirm-accept')).toBe('Delete');
  });

  it('labels and describes itself for a screen reader', async () => {
    const { harness } = await open();

    const dialog = dialogOf(harness);
    const labelId = dialog.getAttribute('aria-labelledby');
    const describedId = dialog.getAttribute('aria-describedby');

    expect(dialog.querySelector(`#${labelId}`)?.textContent).toBe('Are you sure?');
    expect(dialog.querySelector(`#${describedId}`)?.textContent).toContain('Maria Huber');
  });

  it('resolves true when the destructive button is used, and closes', async () => {
    const { harness, answer } = await open();

    await harness.click('confirm-accept');

    await expect(answer).resolves.toBe(true);
    expect(dialogOf(harness).open).toBe(false);
  });

  it('resolves false when the prompt is declined', async () => {
    const { harness, answer } = await open();

    await harness.click('confirm-cancel');

    await expect(answer).resolves.toBe(false);
  });

  it('treats a dismissal by the browser, such as Escape, as declining', async () => {
    const { harness, answer } = await open();

    dialogOf(harness).dispatchEvent(new Event('cancel'));
    await harness.settle();

    await expect(answer).resolves.toBe(false);
    expect(dialogOf(harness).open).toBe(false);
  });

  it('does not answer the same question twice when it closes after being answered', async () => {
    const { harness, answer } = await open();
    let settled = 0;
    void answer.then(() => settled++);

    await harness.click('confirm-accept');
    // The close the component itself performed must not be read back as a dismissal.
    dialogOf(harness).dispatchEvent(new Event('close'));
    await harness.settle();

    await answer;
    expect(settled).toBe(1);
    expect(dialogOf(harness).open).toBe(false);
  });
});
