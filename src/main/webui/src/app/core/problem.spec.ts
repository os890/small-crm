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

import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { toProblem } from './problem';

function response(status: number, body: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body, url: '/api/test' });
}

describe('toProblem', () => {
  it('keeps the code, message and field errors the server sent', () => {
    const problem = toProblem(
      response(400, {
        code: 'VALIDATION_FAILED',
        message: 'One or more fields are invalid',
        details: { name: 'must not be blank', email: 'must be a well-formed email address' },
      }),
    );

    expect(problem.status).toBe(400);
    expect(problem.code).toBe('VALIDATION_FAILED');
    expect(problem.fieldErrors).toEqual({
      name: 'must not be blank',
      email: 'must be a well-formed email address',
    });
    expect(problem.conflicts).toEqual([]);
  });

  it('extracts the colliding appointments of a booking conflict', () => {
    const conflict = {
      id: 7,
      title: 'Client meeting',
      startsAt: '2026-07-25T08:00:00Z',
      endsAt: '2026-07-25T09:00:00Z',
    };

    const problem = toProblem(
      response(409, {
        code: 'APPOINTMENT_CONFLICT',
        message: 'overlaps',
        details: { conflicts: [conflict] },
      }),
    );

    expect(problem.code).toBe('APPOINTMENT_CONFLICT');
    expect(problem.conflicts).toEqual([conflict]);
    // A list-valued detail must not leak into the per-field messages.
    expect(problem.fieldErrors).toEqual({});
  });

  it('reports an unreachable server separately from a rejected request', () => {
    expect(toProblem(response(0, null)).code).toBe('network');
  });

  it('derives a code when the body is not the standard error shape', () => {
    expect(toProblem(response(403, 'Forbidden')).code).toBe('forbidden');
    expect(toProblem(response(404, null)).code).toBe('notFound');
    expect(toProblem(response(401, null)).code).toBe('UNAUTHORIZED');
    expect(toProblem(response(500, null)).code).toBe('unexpected');
  });

  it('survives something that is not an HTTP error at all', () => {
    const problem = toProblem(new Error('boom'));

    expect(problem.status).toBe(0);
    expect(problem.code).toBe('unexpected');
    expect(problem.conflicts).toEqual([]);
  });

  it('falls back to the derived code when the body has none', () => {
    expect(toProblem(response(404, { code: '', message: '', details: null })).code).toBe(
      'notFound',
    );
  });
});
