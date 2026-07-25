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
import { FormatService } from './format.service';
import { I18nService } from './i18n/i18n.service';

describe('FormatService', () => {
  let format: FormatService;
  let i18n: I18nService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    i18n = TestBed.inject(I18nService);
    format = TestBed.inject(FormatService);
    i18n.use('en');
  });

  it('formats a plain date in the day-first order both languages use', () => {
    expect(format.date('2026-07-25')).toBe('25/07/2026');

    i18n.use('de');

    expect(format.date('2026-07-25')).toBe('25.7.2026');
  });

  it('keeps a date-only value on its own day regardless of the time zone', () => {
    // Parsed as local midnight rather than UTC midnight, so the day cannot slip backwards.
    expect(format.date('2026-01-01')).toBe('01/01/2026');
  });

  it('formats an instant as a local time without seconds', () => {
    // 08:30 UTC is 10:30 in Vienna during summer time.
    expect(format.time('2026-07-25T08:30:00Z')).toBe('10:30');
  });

  it('combines date and time', () => {
    expect(format.dateTime('2026-07-25T08:30:00Z')).toBe('25/07/2026, 10:30');
  });

  it('writes a long day heading in the chosen language', () => {
    expect(format.dayHeading('2026-07-25')).toBe('Saturday, 25 July 2026');

    i18n.use('de');

    expect(format.dayHeading('2026-07-25')).toBe('Samstag, 25. Juli 2026');
  });

  it('formats money with the currency of the deal', () => {
    expect(format.money(1234.5, 'EUR')).toBe('€1,234.50');

    i18n.use('de');

    // Austrian formatting: dot as thousands separator, comma as decimal separator.
    expect(format.money(1234.5, 'EUR').replace(/\u00a0/g, ' ')).toBe('€ 1.234,50');
  });

  it('falls back to euro when no currency is given', () => {
    expect(format.money(10, null)).toBe('€10.00');
  });

  it('returns an empty string for missing or unusable values', () => {
    expect(format.date(null)).toBe('');
    expect(format.date(undefined)).toBe('');
    expect(format.time('')).toBe('');
    expect(format.date('not a date')).toBe('');
    expect(format.dateTime(null)).toBe('');
    expect(format.dayHeading('nonsense')).toBe('');
    expect(format.money(null, 'EUR')).toBe('');
    expect(format.money(undefined, 'EUR')).toBe('');
  });

  it('accepts a Date for the day heading', () => {
    expect(format.dayHeading(new Date(2026, 6, 25))).toBe('Saturday, 25 July 2026');
  });
});
