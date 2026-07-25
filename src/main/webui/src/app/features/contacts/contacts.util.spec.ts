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

import { describe, expect, it } from 'vitest';
import { splitTags } from './contacts.util';

describe('splitTags', () => {
  it('splits on commas and trims the surrounding spaces', () => {
    expect(splitTags('vip, lead ,  key account')).toEqual(['vip', 'lead', 'key account']);
  });

  it('drops empty entries left by stray commas', () => {
    expect(splitTags('vip,,lead,')).toEqual(['vip', 'lead']);
    expect(splitTags('   ')).toEqual([]);
    expect(splitTags('')).toEqual([]);
  });

  it('keeps each tag once', () => {
    expect(splitTags('vip, vip , lead')).toEqual(['vip', 'lead']);
  });
});
