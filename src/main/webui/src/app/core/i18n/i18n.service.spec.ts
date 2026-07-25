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

import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { I18nService } from './i18n.service';
import { CATALOGUES, EN, LANGUAGES, TranslationKey } from './translations';

describe('I18nService', () => {
  let i18n: I18nService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    i18n = TestBed.inject(I18nService);
    i18n.use('en');
  });

  it('translates a key in the selected language', () => {
    expect(i18n.t('nav.contacts')).toBe('Contacts');

    i18n.use('de');

    expect(i18n.t('nav.contacts')).toBe('Kontakte');
  });

  it('fills placeholders and leaves unknown ones alone', () => {
    expect(i18n.t('dashboard.greeting', { name: 'Maria' })).toBe('Hello Maria!');
    expect(i18n.t('common.deleteQuestion', {})).toContain('{name}');
  });

  it('switches the locale used for dates and money along with the language', () => {
    expect(i18n.locale()).toBe('en-GB');

    i18n.use('de');

    expect(i18n.locale()).toBe('de-AT');
  });

  it('remembers the choice for the next visit', () => {
    i18n.use('de');

    expect(localStorage.getItem('small-crm.language')).toBe('de');
  });

  it('ignores an unknown language instead of blanking the interface', () => {
    i18n.use('fr' as never);

    expect(i18n.language()).toBe('en');
  });

  it('builds labels for values only known at runtime', () => {
    expect(i18n.label('deals.stage', 'PROPOSAL')).toBe('Proposal sent');
    expect(i18n.label('deals.stage', null)).toBe('');
  });

  it('maps a known server error code to a sentence', () => {
    expect(i18n.errorMessage('LAST_ADMIN')).toBe(
      'At least one active administrator has to remain.',
    );
  });

  it('falls back for an error code it does not know', () => {
    expect(i18n.errorMessage('SOMETHING_NEW')).toBe(
      'An unexpected error occurred. Nothing was saved.',
    );
    expect(i18n.errorMessage(undefined, 'raw detail')).toBe('raw detail');
  });
});

describe('translation catalogues', () => {
  it('define exactly the same keys in every language', () => {
    const englishKeys = Object.keys(EN).sort();
    for (const language of LANGUAGES) {
      expect(Object.keys(CATALOGUES[language]).sort(), `keys of ${language}`).toEqual(englishKeys);
    }
  });

  it('leave no entry empty', () => {
    for (const language of LANGUAGES) {
      for (const key of Object.keys(CATALOGUES[language]) as TranslationKey[]) {
        expect(CATALOGUES[language][key].trim(), `${language}.${key}`).not.toBe('');
      }
    }
  });

  it('keep the same placeholders in the translation as in the English original', () => {
    const placeholders = (text: string) => (text.match(/\{\w+\}/g) ?? []).sort();
    for (const key of Object.keys(EN) as TranslationKey[]) {
      for (const language of LANGUAGES) {
        expect(placeholders(CATALOGUES[language][key]), `${language}.${key}`).toEqual(
          placeholders(EN[key]),
        );
      }
    }
  });
});
