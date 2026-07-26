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

/**
 * Pins the environment the unit tests run in.
 *
 * <p>The time zone is fixed because date formatting and calendar grouping depend on it;
 * without this the same test would pass in Vienna and fail in a UTC container.
 */

// Declared locally rather than pulling in the whole Node type package for one property.
declare const process: { env: Record<string, string | undefined> };

process.env['TZ'] = 'Europe/Vienna';

/*
 * The test runner's jsdom instance has an opaque origin, and jsdom withholds localStorage in
 * that case. Real browsers always provide it, so the language switcher is tested against this
 * in-memory stand-in rather than being weakened to accommodate the test environment.
 */
if (typeof globalThis.localStorage === 'undefined') {
  const entries = new Map<string, string>();
  const memoryStorage: Storage = {
    get length() {
      return entries.size;
    },
    clear: () => entries.clear(),
    getItem: (key: string) => entries.get(key) ?? null,
    key: (index: number) => [...entries.keys()][index] ?? null,
    removeItem: (key: string) => void entries.delete(key),
    setItem: (key: string, value: string) => void entries.set(key, String(value)),
  };
  Object.defineProperty(globalThis, 'localStorage', {
    value: memoryStorage,
    configurable: true,
  });
}

/*
 * jsdom renders <dialog> but implements none of its behaviour. The confirmation prompt relies on
 * `showModal()` for the things that make it a real modal — focus trapping, Escape, inertness of
 * the page behind it — all of which are the browser's job and cannot be asserted here anyway.
 * What can be asserted is the wiring around it, so the two methods are stood in for with just
 * enough behaviour to drive that: the open flag and the `close` event.
 */
const dialogPrototype = globalThis.HTMLDialogElement?.prototype;
if (dialogPrototype && typeof dialogPrototype.showModal !== 'function') {
  dialogPrototype.showModal = function showModal(this: HTMLDialogElement) {
    this.open = true;
  };
  dialogPrototype.close = function close(this: HTMLDialogElement, returnValue?: string) {
    if (returnValue !== undefined) {
      this.returnValue = returnValue;
    }
    this.open = false;
    this.dispatchEvent(new Event('close'));
  };
}
