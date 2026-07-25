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

import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';
import { ToastService } from '../core/toast.service';

/** Renders the short confirmations and error messages queued by {@link ToastService}. */
@Component({
  selector: 'app-toast-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toast-stack" role="status" aria-live="polite">
      @for (toast of toasts.toasts(); track toast.id) {
        <div
          class="toast"
          [class.toast-success]="toast.kind === 'success'"
          [class.toast-error]="toast.kind === 'error'"
        >
          <span class="grow">{{ toast.text }}</span>
          <button
            type="button"
            (click)="toasts.dismiss(toast.id)"
            [attr.aria-label]="t('action.close')"
          >
            &times;
          </button>
        </div>
      }
    </div>
  `,
})
export class ToastHostComponent {
  protected readonly toasts = inject(ToastService);
  protected readonly t = inject(I18nService).t;
}
