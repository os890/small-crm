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

import { Injectable, inject, signal } from '@angular/core';
import { I18nService } from './i18n/i18n.service';
import { Problem, toProblem } from './problem';

export interface Toast {
  id: number;
  kind: 'success' | 'error';
  text: string;
}

const VISIBLE_MS = 5000;

/** Short confirmations and error messages shown in a corner of the screen. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly i18n = inject(I18nService);
  private readonly items = signal<Toast[]>([]);
  private nextId = 1;

  readonly toasts = this.items.asReadonly();

  success(text: string): void {
    this.push('success', text);
  }

  error(text: string): void {
    this.push('error', text);
  }

  /**
   * Shows a failed request in words the user can act on.
   *
   * <p>Field level validation errors are reported by the form next to the offending input, so
   * the toast only carries the summary line for those.
   */
  problem(error: unknown): Problem {
    const problem = toProblem(error);
    this.error(this.i18n.errorMessage(problem.code, problem.message));
    return problem;
  }

  dismiss(id: number): void {
    this.items.update((current) => current.filter((toast) => toast.id !== id));
  }

  private push(kind: Toast['kind'], text: string): void {
    const toast: Toast = { id: this.nextId++, kind, text };
    this.items.update((current) => [...current, toast]);
    setTimeout(() => this.dismiss(toast.id), VISIBLE_MS);
  }
}
