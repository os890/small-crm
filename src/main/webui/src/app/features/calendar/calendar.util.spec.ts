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
import { Appointment } from '../../core/models';
import {
  addMinutes,
  durationMinutes,
  groupByDay,
  isoToDatePart,
  isoToTimePart,
  partsToIso,
  startOfToday,
} from './calendar.util';

function appointment(startsAt: string, endsAt: string, title = 'x'): Appointment {
  return { title, startsAt, endsAt };
}

describe('calendar time conversion', () => {
  it('turns a local date and time into a UTC instant', () => {
    // Vienna is UTC+2 in July, so 10:00 local is 08:00 UTC.
    expect(partsToIso('2026-07-25', '10:00')).toBe('2026-07-25T08:00:00.000Z');
  });

  it('turns a UTC instant back into local parts', () => {
    expect(isoToDatePart('2026-07-25T08:00:00Z')).toBe('2026-07-25');
    expect(isoToTimePart('2026-07-25T08:00:00Z')).toBe('10:00');
  });

  it('round trips a slot without drift', () => {
    const iso = partsToIso('2026-12-31', '23:30');

    expect(partsToIso(isoToDatePart(iso), isoToTimePart(iso))).toBe(iso);
  });

  it('shows the local day even when UTC has already moved on', () => {
    // 23:30 UTC on 24 July is already 01:30 on 25 July in Vienna.
    expect(isoToDatePart('2026-07-24T23:30:00Z')).toBe('2026-07-25');
  });
});

describe('addMinutes', () => {
  it('advances the clock time', () => {
    expect(addMinutes('09:00', 60)).toBe('10:00');
    expect(addMinutes('09:45', 30)).toBe('10:15');
    expect(addMinutes('09:00', 0)).toBe('09:00');
  });

  it('clamps at the end of the day rather than rolling over', () => {
    expect(addMinutes('23:30', 60)).toBe('23:59');
  });
});

describe('durationMinutes', () => {
  it('reports the length of an appointment', () => {
    expect(durationMinutes(appointment('2026-07-25T08:00:00Z', '2026-07-25T09:30:00Z'))).toBe(90);
  });

  it('never reports a negative length', () => {
    expect(durationMinutes(appointment('2026-07-25T09:00:00Z', '2026-07-25T08:00:00Z'))).toBe(0);
  });
});

describe('groupByDay', () => {
  it('splits a chronological list into local days, keeping the order', () => {
    const groups = groupByDay([
      appointment('2026-07-25T07:00:00Z', '2026-07-25T08:00:00Z', 'morning'),
      appointment('2026-07-25T12:00:00Z', '2026-07-25T13:00:00Z', 'noon'),
      appointment('2026-07-26T07:00:00Z', '2026-07-26T08:00:00Z', 'next day'),
    ]);

    expect(groups.map((group) => group.date)).toEqual(['2026-07-25', '2026-07-26']);
    expect(groups[0].appointments.map((item) => item.title)).toEqual(['morning', 'noon']);
    expect(groups[1].appointments).toHaveLength(1);
  });

  it('returns nothing for an empty agenda', () => {
    expect(groupByDay([])).toEqual([]);
  });

  it('groups by the local day, so a late UTC evening lands on the next date', () => {
    const groups = groupByDay([
      appointment('2026-07-24T21:00:00Z', '2026-07-24T22:00:00Z'),
      appointment('2026-07-24T23:00:00Z', '2026-07-25T00:00:00Z'),
    ]);

    expect(groups.map((group) => group.date)).toEqual(['2026-07-24', '2026-07-25']);
  });
});

describe('startOfToday', () => {
  it('is local midnight of the given moment', () => {
    const midnight = startOfToday(new Date(2026, 6, 25, 17, 42, 13));

    expect(midnight.getFullYear()).toBe(2026);
    expect(midnight.getMonth()).toBe(6);
    expect(midnight.getDate()).toBe(25);
    expect(midnight.getHours()).toBe(0);
    expect(midnight.getMinutes()).toBe(0);
  });
});
