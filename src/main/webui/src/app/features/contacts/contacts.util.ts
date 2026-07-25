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
 * Turns the comma separated tag input back into the list the API expects, dropping blanks and
 * keeping each tag once.
 */
export function splitTags(text: string): string[] {
  const seen = new Set<string>();
  for (const part of text.split(',')) {
    const trimmed = part.trim();
    if (trimmed) {
      seen.add(trimmed);
    }
  }
  return [...seen];
}
