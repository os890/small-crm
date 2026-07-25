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

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { I18nService } from '../core/i18n/i18n.service';

/** A rendered page plus the handles its test needs. */
export interface PageHarness<T> {
  fixture: ComponentFixture<T>;
  component: T;
  http: HttpTestingController;
  /** Runs change detection and lets pending microtasks settle. */
  settle(): Promise<void>;
  /** Waits real time, for the debounced search and conflict lookups. */
  wait(ms: number): Promise<void>;
  /** Answers a pending GET, or does nothing if the page did not issue it. */
  flushGet(url: string, body: unknown[] | Record<string, unknown> | null): void;
  query<E extends Element = HTMLElement>(testId: string): E | null;
  text(testId: string): string;
  click(testId: string): Promise<void>;
  type(testId: string, value: string): Promise<void>;
  all(testId: string): HTMLElement[];
}

/**
 * Boots a standalone page component against a mocked HTTP backend.
 *
 * <p>Pages fetch in their constructor, so nothing is flushed automatically: each test decides
 * which of the initial requests to answer and with what.
 */
export function renderPage<T>(
  component: Type<T>,
  options: { providers?: unknown[]; language?: 'en' | 'de' } = {},
): PageHarness<T> {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      ...((options.providers ?? []) as never[]),
    ],
  });
  TestBed.inject(I18nService).use(options.language ?? 'en');

  const fixture = TestBed.createComponent(component);
  const http = TestBed.inject(HttpTestingController);

  const harness: PageHarness<T> = {
    fixture,
    component: fixture.componentInstance,
    http,
    async settle() {
      // Two rounds: the first lets the awaited promises resolve, the second renders what they
      // changed.
      await fixture.whenStable();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    },
    async wait(ms: number) {
      await new Promise((resolve) => setTimeout(resolve, ms));
      await harness.settle();
    },
    flushGet(url, body) {
      const matches = http.match((request) => request.url === url && request.method === 'GET');
      matches.forEach((request) => request.flush(body as never));
    },
    query<E extends Element = HTMLElement>(testId: string) {
      return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as E | null;
    },
    text(testId: string) {
      return harness.query(testId)?.textContent?.trim() ?? '';
    },
    async click(testId: string) {
      const element = harness.query<HTMLElement>(testId);
      if (!element) {
        throw new Error(`No element with data-testid="${testId}"`);
      }
      element.click();
      await harness.settle();
    },
    async type(testId: string, value: string) {
      const input = harness.query<HTMLInputElement>(testId);
      if (!input) {
        throw new Error(`No input with data-testid="${testId}"`);
      }
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await harness.settle();
    },
    all(testId: string) {
      return Array.from(
        fixture.nativeElement.querySelectorAll(`[data-testid="${testId}"]`),
      ) as HTMLElement[];
    },
  };
  return harness;
}
