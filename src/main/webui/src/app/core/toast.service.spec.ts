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

import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nService } from './i18n/i18n.service';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let toasts: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.inject(I18nService).use('en');
    toasts = TestBed.inject(ToastService);
  });

  afterEach(() => vi.useRealTimers());

  it('queues messages in the order they arrive', () => {
    toasts.success('first');
    toasts.error('second');

    expect(toasts.toasts().map((toast) => [toast.kind, toast.text])).toEqual([
      ['success', 'first'],
      ['error', 'second'],
    ]);
  });

  it('removes a message once its time is up', () => {
    toasts.success('gone soon');

    vi.advanceTimersByTime(5000);

    expect(toasts.toasts()).toHaveLength(0);
  });

  it('can be dismissed early without touching the others', () => {
    toasts.success('one');
    toasts.success('two');
    const [first] = toasts.toasts();

    toasts.dismiss(first.id);

    expect(toasts.toasts().map((toast) => toast.text)).toEqual(['two']);
  });

  it('turns a failed request into a translated message and hands back the details', () => {
    const problem = toasts.problem(
      new HttpErrorResponse({
        status: 400,
        error: { code: 'LAST_ADMIN', message: 'raw', details: { name: 'must not be blank' } },
      }),
    );

    expect(toasts.toasts()[0].text).toBe('At least one active administrator has to remain.');
    expect(problem.fieldErrors).toEqual({ name: 'must not be blank' });
  });

  it('reports an unreachable server in words the user can act on', () => {
    toasts.problem(new HttpErrorResponse({ status: 0 }));

    expect(toasts.toasts()[0].text).toContain('server cannot be reached');
  });
});
