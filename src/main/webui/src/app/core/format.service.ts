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

import { Injectable, inject } from '@angular/core';
import { I18nService } from './i18n/i18n.service';

/**
 * Date, time and money formatting that follows the language the user picked, so a German user
 * sees 25.07.2026 and 1.234,50 € while an English user sees 25/07/2026 and €1,234.50.
 */
@Injectable({ providedIn: 'root' })
export class FormatService {
  private readonly i18n = inject(I18nService);

  /** Formats an ISO date (yyyy-MM-dd) or instant as a plain date. */
  date(value: string | null | undefined): string {
    const parsed = this.parse(value);
    return parsed ? parsed.toLocaleDateString(this.i18n.locale()) : '';
  }

  /** Formats an ISO instant as a time of day without seconds. */
  time(value: string | null | undefined): string {
    const parsed = this.parse(value);
    return parsed
      ? parsed.toLocaleTimeString(this.i18n.locale(), { hour: '2-digit', minute: '2-digit' })
      : '';
  }

  dateTime(value: string | null | undefined): string {
    const parsed = this.parse(value);
    if (!parsed) {
      return '';
    }
    return `${this.date(value)}, ${this.time(value)}`;
  }

  /** A long, readable day heading such as "Saturday, 25 July 2026". */
  dayHeading(value: string | Date): string {
    const parsed = value instanceof Date ? value : this.parse(value);
    return parsed
      ? parsed.toLocaleDateString(this.i18n.locale(), {
          weekday: 'long',
          day: 'numeric',
          month: 'long',
          year: 'numeric',
        })
      : '';
  }

  money(amount: number | null | undefined, currency: string | null | undefined): string {
    if (amount === null || amount === undefined) {
      return '';
    }
    return new Intl.NumberFormat(this.i18n.locale(), {
      style: 'currency',
      currency: currency || 'EUR',
    }).format(amount);
  }

  private parse(value: string | null | undefined): Date | null {
    if (!value) {
      return null;
    }
    // A bare yyyy-MM-dd is parsed as UTC midnight by Date; adding the time keeps it local so
    // the displayed day cannot shift backwards in negative offsets.
    const text = /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value;
    const parsed = new Date(text);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }
}
