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

import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  viewChild,
} from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';
import { ConfirmService } from './confirm.service';

/**
 * Renders the confirmation prompt requested through {@link ConfirmService}.
 *
 * <p>Built on the native {@code <dialog>} element opened with {@code showModal()}. That is what
 * makes it a real modal: the browser moves focus into it, traps Tab inside it, closes it on
 * Escape and makes everything behind it inert. The previous hand-rolled backdrop had an Escape
 * handler that almost never fired, because focus stayed on the Delete button outside it.
 *
 * <p>The role is {@code alertdialog} rather than the element's implicit {@code dialog}: this
 * always asks a question that has to be answered before anything else can happen, which is
 * exactly the distinction the two roles draw.
 */
@Component({
  selector: 'app-confirm-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dialog
      class="confirm"
      role="alertdialog"
      aria-labelledby="confirm-title"
      aria-describedby="confirm-question"
      (close)="onNativeClose()"
      (cancel)="confirm.answer(false)"
    >
      @if (confirm.request(); as request) {
        <div class="dialog-head">
          <h2 id="confirm-title">{{ request.title }}</h2>
        </div>
        <div class="dialog-body">
          <p id="confirm-question">{{ request.question }}</p>
          @if (request.hint) {
            <p class="faint">{{ request.hint }}</p>
          }
        </div>
        <div class="dialog-foot">
          <button type="button" class="btn" data-testid="confirm-cancel" (click)="answer(false)">
            {{ t('action.cancel') }}
          </button>
          <button
            type="button"
            class="btn"
            data-testid="confirm-accept"
            [class.btn-danger]="request.destructive"
            [class.btn-primary]="!request.destructive"
            (click)="answer(true)"
          >
            {{ request.confirmLabel }}
          </button>
        </div>
      }
    </dialog>
  `,
  styles: `
    dialog.confirm {
      width: min(440px, calc(100vw - 2 * var(--space-5)));
      padding: 0;
      border: none;
      border-radius: var(--radius);
      box-shadow: var(--shadow-lg);
      color: var(--ink);
      background: var(--surface);
    }

    dialog.confirm::backdrop {
      background: rgb(16 24 40 / 45%);
    }
  `,
})
export class ConfirmHostComponent {
  protected readonly confirm = inject(ConfirmService);
  protected readonly t = inject(I18nService).t;

  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  /** Set while this component is closing the dialog itself, to ignore the resulting event. */
  private answering = false;

  constructor() {
    effect(() => {
      const request = this.confirm.request();
      const element = this.dialog().nativeElement;
      if (request && !element.open) {
        element.showModal();
      } else if (!request && element.open) {
        this.answering = true;
        element.close();
        this.answering = false;
      }
    });
  }

  protected answer(confirmed: boolean): void {
    this.confirm.answer(confirmed);
  }

  /**
   * Handles the dialog being dismissed by the browser, for instance with Escape.
   *
   * <p>Ignored when this component closed it after an answer was already given, otherwise the
   * close would resolve the same question a second time.
   */
  protected onNativeClose(): void {
    if (!this.answering && this.confirm.request()) {
      this.confirm.answer(false);
    }
  }
}
