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
import { ConfirmService } from './confirm.service';

/** Renders the confirmation prompt requested through {@link ConfirmService}. */
@Component({
  selector: 'app-confirm-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (confirm.request(); as request) {
      <div class="backdrop" (keydown.escape)="confirm.answer(false)" tabindex="-1">
        <div
          class="dialog dialog-narrow"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="confirm-title"
        >
          <div class="dialog-head">
            <h2 id="confirm-title">{{ request.title }}</h2>
          </div>
          <div class="dialog-body">
            <p>{{ request.question }}</p>
            @if (request.hint) {
              <p class="faint">{{ request.hint }}</p>
            }
          </div>
          <div class="dialog-foot">
            <button type="button" class="btn" (click)="confirm.answer(false)">
              {{ t('action.cancel') }}
            </button>
            <button
              type="button"
              class="btn"
              data-testid="confirm-accept"
              [class.btn-danger]="request.destructive"
              [class.btn-primary]="!request.destructive"
              (click)="confirm.answer(true)"
            >
              {{ request.confirmLabel }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ConfirmHostComponent {
  protected readonly confirm = inject(ConfirmService);
  protected readonly t = inject(I18nService).t;
}
