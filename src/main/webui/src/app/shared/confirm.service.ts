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

import { Injectable, signal } from '@angular/core';

export interface ConfirmRequest {
  title: string;
  question: string;
  hint?: string;
  confirmLabel: string;
  destructive: boolean;
}

interface PendingConfirm extends ConfirmRequest {
  resolve: (confirmed: boolean) => void;
}

/**
 * A promise-based confirmation prompt.
 *
 * <p>Deleting is the one irreversible thing this application does, so it is never a single
 * click; the prompt always names what is about to disappear.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly pending = signal<PendingConfirm | null>(null);

  readonly request = this.pending.asReadonly();

  /**
   * Asks a question and resolves with the answer.
   *
   * <p>A second question raised while one is still open resolves immediately as declined rather
   * than replacing it. Overwriting left the first caller's promise pending for ever, which in
   * practice meant a delete flow that silently stopped halfway.
   */
  ask(request: ConfirmRequest): Promise<boolean> {
    if (this.pending() !== null) {
      return Promise.resolve(false);
    }
    return new Promise<boolean>((resolve) => {
      this.pending.set({ ...request, resolve });
    });
  }

  answer(confirmed: boolean): void {
    const current = this.pending();
    if (current) {
      this.pending.set(null);
      current.resolve(confirmed);
    }
  }
}
