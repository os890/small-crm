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

  ask(request: ConfirmRequest): Promise<boolean> {
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
