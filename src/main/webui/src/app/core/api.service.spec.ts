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

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the login as a form, which is what the server mechanism expects', async () => {
    const pending = api.login('admin', 's3cr3t & more');

    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(request.request.body).toBe('username=admin&password=s3cr3t+%26+more');
    request.flush(null);

    await pending;
  });

  it('leaves empty query parameters out instead of sending blanks', async () => {
    const pending = api.listContacts('', undefined);

    const request = http.expectOne('/api/contacts');
    expect(request.request.params.keys()).toEqual([]);
    request.flush([]);

    await pending;
  });

  it('sends the filters that are set', async () => {
    const pending = api.listContacts('huber', 4);

    const request = http.expectOne((candidate) => candidate.url === '/api/contacts');
    expect(request.request.params.get('search')).toBe('huber');
    expect(request.request.params.get('companyId')).toBe('4');
    request.flush([]);

    await pending;
  });

  it('reads the paging headers back off a list response', async () => {
    const pending = api.listContacts(undefined, undefined, { page: 2, size: 25 });

    const request = http.expectOne((candidate) => candidate.url === '/api/contacts');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('25');
    request.flush([{ id: 1, firstName: 'Maria', lastName: 'Huber' }], {
      headers: { 'X-Total-Count': '812', 'X-Page': '2', 'X-Page-Size': '25' },
    });

    const found = await pending;
    expect(found.items).toHaveLength(1);
    expect(found.total).toBe(812);
    expect(found.page).toBe(2);
    expect(found.size).toBe(25);
  });

  it('treats a response without paging headers as the whole answer', async () => {
    const pending = api.listCompanies();

    http.expectOne('/api/companies').flush([{ id: 1, name: 'Muster GmbH' }]);

    const found = await pending;
    expect(found.total).toBe(1);
    expect(found.page).toBe(0);
    expect(found.size).toBe(1);
  });

  it('creates with POST and updates with PUT, based on the presence of an id', async () => {
    const create = api.saveCompany({ name: 'New Ltd' });
    const created = http.expectOne('/api/companies');
    expect(created.request.method).toBe('POST');
    created.flush({ id: 1, name: 'New Ltd' });
    await create;

    const update = api.saveCompany({ id: 1, name: 'Renamed Ltd' });
    const updated = http.expectOne('/api/companies/1');
    expect(updated.request.method).toBe('PUT');
    updated.flush({ id: 1, name: 'Renamed Ltd' });
    await update;
  });

  it('passes the overlap override through as a query parameter', async () => {
    const pending = api.saveAppointment(
      { title: 'Client meeting', startsAt: 'a', endsAt: 'b' },
      true,
    );

    const request = http.expectOne((candidate) => candidate.url === '/api/appointments');
    expect(request.request.params.get('allowConflict')).toBe('true');
    request.flush({});

    await pending;
  });

  it('asks for conflicts without sending an empty exclusion', async () => {
    const pending = api.appointmentConflicts('2026-07-25T08:00:00Z', '2026-07-25T09:00:00Z');

    const request = http.expectOne((candidate) => candidate.url === '/api/appointments/conflicts');
    expect(request.request.params.get('startsAt')).toBe('2026-07-25T08:00:00Z');
    expect(request.request.params.has('excludeId')).toBe(false);
    request.flush([]);

    await pending;
  });

  it('sends the agenda window as ISO instants', async () => {
    const pending = api.listAppointments(
      new Date('2026-07-25T00:00:00Z'),
      new Date('2026-08-25T00:00:00Z'),
    );

    const request = http.expectOne((candidate) => candidate.url === '/api/appointments');
    expect(request.request.params.get('from')).toBe('2026-07-25T00:00:00.000Z');
    expect(request.request.params.get('to')).toBe('2026-08-25T00:00:00.000Z');
    request.flush([]);

    await pending;
  });

  it('moves a deal with an empty body and the stage as a parameter', async () => {
    const pending = api.moveDeal(3, 'WON');

    const request = http.expectOne((candidate) => candidate.url === '/api/deals/3/stage');
    expect(request.request.method).toBe('PUT');
    expect(request.request.params.get('value')).toBe('WON');
    request.flush({});

    await pending;
  });

  it('toggles a task through the dedicated endpoint', async () => {
    const pending = api.setTaskDone(9, false);

    const request = http.expectOne((candidate) => candidate.url === '/api/tasks/9/done');
    expect(request.request.params.get('value')).toBe('false');
    request.flush({});

    await pending;
  });

  it('deletes through the collection endpoint with the identifier appended', async () => {
    const pending = api.deleteContact(12);

    const request = http.expectOne('/api/contacts/12');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    await pending;
  });
});
