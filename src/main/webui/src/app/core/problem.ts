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
import { Appointment, ApiError } from './models';

/** A failed request reduced to what the interface actually needs to react to. */
export interface Problem {
  status: number;
  code: string;
  message: string;
  /** Field name to message, present when the server rejected individual inputs. */
  fieldErrors: Record<string, string>;
  /** Appointments blocking the requested slot, present only for a booking conflict. */
  conflicts: Appointment[];
}

/**
 * Normalises anything a failed call can throw into a {@link Problem}.
 *
 * <p>Status 0 means the request never reached the server, which deserves a different message
 * from a server that answered with an error.
 */
export function toProblem(error: unknown): Problem {
  if (!(error instanceof HttpErrorResponse)) {
    return blank(0, 'UNEXPECTED');
  }
  if (error.status === 0) {
    return blank(0, 'NETWORK');
  }
  const body = error.error as ApiError | string | null;
  if (!body || typeof body === 'string') {
    return blank(error.status, defaultCodeFor(error.status));
  }
  const details = body.details ?? {};
  return {
    status: error.status,
    code: body.code || defaultCodeFor(error.status),
    message: body.message || '',
    fieldErrors: extractFieldErrors(details),
    conflicts: Array.isArray(details['conflicts']) ? (details['conflicts'] as Appointment[]) : [],
  };
}

function extractFieldErrors(details: Record<string, unknown>): Record<string, string> {
  const fields: Record<string, string> = {};
  for (const [key, value] of Object.entries(details)) {
    if (typeof value === 'string') {
      fields[key] = value;
    }
  }
  return fields;
}

function defaultCodeFor(status: number): string {
  if (status === 401) {
    return 'UNAUTHORIZED';
  }
  if (status === 403) {
    return 'forbidden';
  }
  if (status === 404) {
    return 'notFound';
  }
  return 'unexpected';
}

function blank(status: number, code: string): Problem {
  return {
    status,
    code: code === 'NETWORK' ? 'network' : code === 'UNEXPECTED' ? 'unexpected' : code,
    message: '',
    fieldErrors: {},
    conflicts: [],
  };
}
