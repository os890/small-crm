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

import { Appointment } from '../../core/models';

/**
 * The calendar form asks for a date and two clock times rather than two full timestamps,
 * because that is how people describe an appointment. These helpers translate between that and
 * the ISO instants the API speaks.
 */

/** Turns a local `yyyy-MM-dd` plus `HH:mm` into a UTC ISO instant. */
export function partsToIso(date: string, time: string): string {
  return new Date(`${date}T${time}:00`).toISOString();
}

/** The local calendar date of an instant, as `yyyy-MM-dd`. */
export function isoToDatePart(iso: string): string {
  const value = new Date(iso);
  const year = value.getFullYear();
  const month = `${value.getMonth() + 1}`.padStart(2, '0');
  const day = `${value.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** The local clock time of an instant, as `HH:mm`. */
export function isoToTimePart(iso: string): string {
  const value = new Date(iso);
  return `${`${value.getHours()}`.padStart(2, '0')}:${`${value.getMinutes()}`.padStart(2, '0')}`;
}

/** Adds whole minutes to an `HH:mm` string, clamping at the end of the day. */
export function addMinutes(time: string, minutes: number): string {
  const [hours, mins] = time.split(':').map(Number);
  const total = Math.min(hours * 60 + mins + minutes, 23 * 60 + 59);
  return `${`${Math.floor(total / 60)}`.padStart(2, '0')}:${`${total % 60}`.padStart(2, '0')}`;
}

/** Length of an appointment in whole minutes. */
export function durationMinutes(appointment: Appointment): number {
  const from = new Date(appointment.startsAt).getTime();
  const to = new Date(appointment.endsAt).getTime();
  return Math.max(0, Math.round((to - from) / 60000));
}

export interface DayGroup {
  /** Local `yyyy-MM-dd`, used as the group key and for the heading. */
  date: string;
  appointments: Appointment[];
}

/**
 * Groups appointments into the local days they start on, keeping the incoming chronological
 * order both between and inside the groups.
 */
export function groupByDay(appointments: Appointment[]): DayGroup[] {
  const groups: DayGroup[] = [];
  let current: DayGroup | undefined;
  for (const appointment of appointments) {
    const date = isoToDatePart(appointment.startsAt);
    if (!current || current.date !== date) {
      current = { date, appointments: [] };
      groups.push(current);
    }
    current.appointments.push(appointment);
  }
  return groups;
}

/** Local midnight of today, the lower bound of the agenda. */
export function startOfToday(now = new Date()): Date {
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}
