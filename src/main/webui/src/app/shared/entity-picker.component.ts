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
  OnDestroy,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';

/** One choice offered by an {@link EntityPickerComponent}. */
export interface PickerOption {
  id: number;
  label: string;
  /** Secondary line, for instance the company a contact belongs to. */
  hint?: string | null;
}

/** What a lookup found: the choices to offer, and how many match in total. */
export interface PickerResult {
  options: PickerOption[];
  /** Total number of matches, so the field can say how many it is not showing. */
  total: number;
}

/** Looks up the choices matching what the user has typed so far. */
export type PickerSearch = (term: string) => Promise<PickerResult>;

/**
 * Picks one record by typing part of its name.
 *
 * <p>This replaces the `<select>` elements the dialogs used to have. Those loaded every contact,
 * company or deal in the installation to build their options, which is both the slowest thing on
 * the screen and unusable once there are more than a screenful of them. Here only what matches
 * is fetched, a page at a time.
 *
 * <p>The field stays a plain text input that can be typed into and cleared, and the list is only
 * ever an aid: nothing is selected until the user picks a row.
 */
@Component({
  selector: 'app-entity-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="field picker" (focusout)="onFocusOut($event)">
      <label [attr.for]="testId()">{{ label() }}</label>
      <div class="picker-input">
        <input
          [id]="testId()"
          type="text"
          role="combobox"
          autocomplete="off"
          [attr.data-testid]="testId()"
          [attr.aria-expanded]="open()"
          [attr.aria-controls]="testId() + '-list'"
          aria-autocomplete="list"
          [placeholder]="placeholder() ?? t('picker.placeholder')"
          [value]="text()"
          (input)="onType($any($event.target).value)"
          (focus)="onFocus()"
          (keydown)="onKeydown($event)"
        />
        @if (text()) {
          <button
            type="button"
            class="btn btn-quiet btn-sm picker-clear"
            [attr.data-testid]="testId() + '-clear'"
            [attr.aria-label]="t('picker.clear')"
            (click)="clear()"
          >
            &times;
          </button>
        }
      </div>

      @if (open()) {
        <ul class="picker-list" role="listbox" [id]="testId() + '-list'">
          @if (searching()) {
            <li class="picker-note">{{ t('common.loading') }}</li>
          } @else if (options().length === 0) {
            <li class="picker-note" [attr.data-testid]="testId() + '-empty'">
              {{ t('picker.noMatches') }}
            </li>
          } @else {
            @for (option of options(); track option.id; let index = $index) {
              <li
                role="option"
                class="picker-option"
                [class.active]="index === active()"
                [attr.aria-selected]="option.id === value()"
                [attr.data-testid]="testId() + '-option'"
                (mousedown)="$event.preventDefault()"
                (click)="choose(option)"
              >
                <span>{{ option.label }}</span>
                @if (option.hint) {
                  <span class="faint">{{ option.hint }}</span>
                }
              </li>
            }
            @if (more() > 0) {
              <li class="picker-note" [attr.data-testid]="testId() + '-more'">
                {{ t('picker.more', { count: more() }) }}
              </li>
            }
          }
        </ul>
      }
    </div>
  `,
  styles: `
    .picker {
      position: relative;
    }

    .picker-input {
      display: flex;
      align-items: center;
      gap: var(--space-1);
    }

    .picker-input input {
      flex: 1;
    }

    .picker-clear {
      flex: none;
    }

    .picker-list {
      position: absolute;
      z-index: 20;
      top: 100%;
      left: 0;
      right: 0;
      margin: 2px 0 0;
      padding: 0;
      list-style: none;
      max-height: 240px;
      overflow-y: auto;
      background: var(--surface);
      border: 1px solid var(--line-strong);
      border-radius: var(--radius-sm);
      box-shadow: var(--shadow-lg);
    }

    .picker-option {
      display: flex;
      justify-content: space-between;
      gap: var(--space-2);
      padding: var(--space-2) var(--space-3);
      cursor: pointer;
    }

    .picker-option.active,
    .picker-option:hover {
      background: var(--canvas);
    }

    .picker-note {
      padding: var(--space-2) var(--space-3);
      color: var(--ink-faint);
    }
  `,
})
export class EntityPickerComponent implements OnDestroy {
  readonly label = input.required<string>();
  /** Used for the input's id and as the prefix of every `data-testid` inside. */
  readonly testId = input.required<string>();
  readonly placeholder = input<string | null>(null);
  /** The record currently chosen, or `null`. */
  readonly value = input<number | null>(null);
  /** What to show for {@link value}; the caller usually already has the name. */
  readonly valueLabel = input<string | null>(null);
  readonly search = input.required<PickerSearch>();

  /** Emits the chosen record, or `null` when the field is cleared. */
  readonly selected = output<PickerOption | null>();

  protected readonly t = inject(I18nService).t;

  protected readonly text = signal('');
  protected readonly options = signal<PickerOption[]>([]);
  protected readonly open = signal(false);
  protected readonly searching = signal(false);
  protected readonly active = signal(-1);
  /** How many further matches exist beyond the ones listed. */
  protected readonly more = signal(0);

  /**
   * The record the field is currently showing.
   *
   * <p>Lets a change coming from the parent — the dialog being opened on another record — be
   * told apart from one this component just made itself. Without it, the effect below undid
   * every selection the moment it was made, because the parent's input had not been re-read
   * yet.
   */
  private shownId: number | null = null;

  private timer: ReturnType<typeof setTimeout> | undefined;
  /**
   * Counts the lookups issued so a slower earlier one cannot overwrite a newer one — the same
   * out-of-order problem the list searches have, and just as visible here.
   */
  private sequence = 0;

  constructor() {
    // Follows the record being edited: opening the dialog on another deal has to re-label the
    // field. Only when the record actually differs from the one on screen, so this never
    // overwrites what the user is doing.
    effect(() => {
      const label = this.valueLabel();
      const id = this.value() ?? null;
      if (id !== this.shownId) {
        this.shownId = id;
        this.text.set(id === null ? '' : (label ?? ''));
      }
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.timer);
  }

  protected onFocus(): void {
    this.open.set(true);
    void this.lookup(this.text());
  }

  protected onType(value: string): void {
    this.text.set(value);
    this.open.set(true);
    this.active.set(-1);
    if (this.shownId !== null) {
      // Typing over the name abandons the record it belonged to. What the field shows and what
      // is stored must never disagree: a field reading "mar" while contact 12 is still attached
      // is the kind of thing nobody notices until the wrong record is saved.
      this.shownId = null;
      this.selected.emit(null);
    }
    clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.lookup(value), 200);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.close();
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.open.set(true);
      const count = this.options().length;
      if (count > 0) {
        const step = event.key === 'ArrowDown' ? 1 : -1;
        this.active.set((this.active() + step + count) % count);
      }
      return;
    }
    if (event.key === 'Enter') {
      const option = this.options()[this.active()];
      if (this.open() && option) {
        // Only swallowed when it actually picks something, so Enter still submits the form.
        event.preventDefault();
        this.choose(option);
      }
    }
  }

  protected choose(option: PickerOption): void {
    this.shownId = option.id;
    this.text.set(option.label);
    this.close();
    this.selected.emit(option);
  }

  protected clear(): void {
    this.shownId = null;
    this.text.set('');
    this.options.set([]);
    this.close();
    this.selected.emit(null);
  }

  /** Closes the list when focus leaves the field altogether, but not while moving inside it. */
  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (!next || !(event.currentTarget as HTMLElement).contains(next)) {
      this.close();
    }
  }

  private close(): void {
    this.open.set(false);
    this.active.set(-1);
  }

  private async lookup(term: string): Promise<void> {
    const sequence = ++this.sequence;
    this.searching.set(true);
    try {
      const found = await this.search()(term.trim());
      if (sequence !== this.sequence) {
        return;
      }
      this.options.set(found.options);
      this.active.set(-1);
      this.more.set(Math.max(0, found.total - found.options.length));
    } catch {
      // A failed lookup leaves the field usable and simply offers nothing; the record being
      // edited must not be lost because a suggestion could not be fetched.
      if (sequence === this.sequence) {
        this.options.set([]);
        this.more.set(0);
      }
    } finally {
      if (sequence === this.sequence) {
        this.searching.set(false);
      }
    }
  }
}
