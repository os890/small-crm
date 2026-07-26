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

import { Injectable, computed, signal } from '@angular/core';
import { CATALOGUES, LANGUAGES, Language, TranslationKey } from './translations';

const STORAGE_KEY = 'small-crm.language';

/** Locale used for dates and currency per language; de means Austrian conventions. */
const LOCALES: Record<Language, string> = { en: 'en-GB', de: 'de-AT' };

/**
 * Runtime translation. The language lives in a signal, so every template that calls
 * {@link I18nService.t} re-renders the moment the user switches, without a page reload and
 * without shipping a separate bundle per language.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly current = signal<Language>(detectInitialLanguage());

  readonly language = this.current.asReadonly();
  readonly locale = computed(() => LOCALES[this.current()]);
  readonly available = LANGUAGES;

  /** Bound so it can be handed straight to a template as `t('some.key')`. */
  readonly t = (key: TranslationKey, params?: Record<string, string | number>): string => {
    const template = CATALOGUES[this.current()][key] ?? key;
    return params ? interpolate(template, params) : template;
  };

  use(language: Language): void {
    if (!LANGUAGES.includes(language) || language === this.current()) {
      return;
    }
    this.current.set(language);
    safeStore(language);
    document.documentElement.lang = language;
  }

  /**
   * Looks up a key that is only known at runtime, such as an enum value, and returns an empty
   * string for a missing value so templates do not have to guard.
   */
  readonly label = (prefix: string, value: string | null | undefined): string =>
    value ? this.t(`${prefix}.${value}` as TranslationKey) : '';

  /**
   * Turns a server error code into a human sentence, falling back to a generic message so an
   * unmapped code never surfaces as raw jargon.
   */
  errorMessage(code: string | undefined, fallback?: string): string {
    const key = `error.${code}` as TranslationKey;
    if (code && key in CATALOGUES[this.current()]) {
      return this.t(key);
    }
    // ?? would keep an empty string, which is exactly what a body-less 401 supplies, and the
    // user would get a blank red box.
    return fallback || this.t('error.unexpected');
  }
}

function interpolate(template: string, params: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  );
}

function detectInitialLanguage(): Language {
  const stored = safeRead();
  if (stored) {
    return stored;
  }
  const browser = typeof navigator === 'undefined' ? '' : navigator.language;
  return browser.toLowerCase().startsWith('de') ? 'de' : 'en';
}

function safeRead(): Language | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return LANGUAGES.includes(value as Language) ? (value as Language) : null;
  } catch {
    // Private browsing modes can deny storage access; the default language is fine then.
    return null;
  }
}

function safeStore(language: Language): void {
  try {
    localStorage.setItem(STORAGE_KEY, language);
  } catch {
    // Not being able to remember the choice is not worth interrupting the user over.
  }
}
