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
import { beforeEach, describe, expect, it } from 'vitest';
import { ConfirmService } from './confirm.service';

describe('ConfirmService', () => {
  let confirm: ConfirmService;

  beforeEach(() => {
    TestBed.resetTestingModule();
    confirm = TestBed.inject(ConfirmService);
  });

  const request = {
    title: 'Are you sure?',
    question: 'Delete “Muster GmbH”?',
    confirmLabel: 'Delete',
    destructive: true,
  };

  it('has nothing pending until something is asked', () => {
    expect(confirm.request()).toBeNull();
  });

  it('exposes the pending question and resolves true when accepted', async () => {
    const answer = confirm.ask(request);

    expect(confirm.request()?.question).toBe('Delete “Muster GmbH”?');
    confirm.answer(true);

    await expect(answer).resolves.toBe(true);
    expect(confirm.request()).toBeNull();
  });

  it('resolves false when dismissed', async () => {
    const answer = confirm.ask(request);

    confirm.answer(false);

    await expect(answer).resolves.toBe(false);
  });

  it('ignores an answer when nothing was asked', () => {
    expect(() => confirm.answer(true)).not.toThrow();
    expect(confirm.request()).toBeNull();
  });
});
