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

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  Appointment,
  BackupFile,
  BackupSettings,
  Company,
  Contact,
  CreateUserRequest,
  CrmTask,
  Dashboard,
  Deal,
  DealStage,
  GoogleStatus,
  Interaction,
  Page,
  RestoreResult,
  SyncReport,
  UpdateUserRequest,
  User,
} from './models';

type QueryValue = string | number | boolean | null | undefined;

/** How much of a list to ask for; omitted parts fall back to the server's defaults. */
export interface PageQuery {
  /** Zero-based page index. */
  page?: number;
  size?: number;
}

function params(values: Record<string, QueryValue>): HttpParams {
  let result = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== null && value !== undefined && value !== '') {
      result = result.set(key, String(value));
    }
  }
  return result;
}

/** Reads a numeric response header, falling back when it is absent or unparseable. */
function header(value: string | null, fallback: number): number {
  const parsed = Number(value);
  return value !== null && Number.isFinite(parsed) ? parsed : fallback;
}

/**
 * One typed place for every backend call.
 *
 * <p>Promises rather than observables: each screen awaits a handful of independent calls, and
 * `await` keeps that far easier to follow than nested subscriptions.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  /**
   * Runs a paged GET and reads the paging headers back.
   *
   * <p>The body of a list endpoint is a plain array; how many there are in total travels in
   * `X-Total-Count`. When a header is missing — an older server, or a stubbed response in a test
   * — what arrived is treated as the whole answer.
   */
  private async paged<T>(url: string, query: Record<string, QueryValue>): Promise<Page<T>> {
    const response = await firstValueFrom(
      this.http.get<T[]>(url, { params: params(query), observe: 'response' }),
    );
    const items = response.body ?? [];
    return {
      items,
      total: header(response.headers.get('X-Total-Count'), items.length),
      page: header(response.headers.get('X-Page'), 0),
      size: header(response.headers.get('X-Page-Size'), items.length),
    };
  }

  // --- session -----------------------------------------------------------------

  login(username: string, password: string): Promise<void> {
    const body = new URLSearchParams({ username, password });
    return firstValueFrom(
      this.http.post<void>('/api/auth/login', body.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      }),
    );
  }

  logout(): Promise<void> {
    return firstValueFrom(this.http.post<void>('/api/auth/logout', null));
  }

  me(): Promise<User> {
    return firstValueFrom(this.http.get<User>('/api/auth/me'));
  }

  changePassword(currentPassword: string, newPassword: string): Promise<User> {
    return firstValueFrom(
      this.http.post<User>('/api/auth/password', { currentPassword, newPassword }),
    );
  }

  // --- google ------------------------------------------------------------------

  /** Whether the sign-in-with-Google button belongs on the login screen. */
  googleAvailable(): Promise<GoogleStatus> {
    return firstValueFrom(this.http.get<GoogleStatus>('/api/google/available'));
  }

  googleStatus(): Promise<GoogleStatus> {
    return firstValueFrom(this.http.get<GoogleStatus>('/api/google/status'));
  }

  /** Returns where to send the browser for Google's consent screen. */
  googleConnect(): Promise<{ url: string }> {
    return firstValueFrom(this.http.post<{ url: string }>('/api/google/connect', null));
  }

  googleSignIn(): Promise<{ url: string }> {
    return firstValueFrom(this.http.post<{ url: string }>('/api/google/signin', null));
  }

  googleDisconnect(): Promise<void> {
    return firstValueFrom(this.http.delete<void>('/api/google/connection'));
  }

  googleSyncNow(): Promise<SyncReport[]> {
    return firstValueFrom(this.http.post<SyncReport[]>('/api/google/sync', null));
  }

  // --- dashboard ---------------------------------------------------------------

  dashboard(): Promise<Dashboard> {
    return firstValueFrom(this.http.get<Dashboard>('/api/dashboard'));
  }

  // --- companies ---------------------------------------------------------------

  listCompanies(search?: string, paging: PageQuery = {}): Promise<Page<Company>> {
    return this.paged<Company>('/api/companies', { search, ...paging });
  }

  getCompany(id: number): Promise<Company> {
    return firstValueFrom(this.http.get<Company>(`/api/companies/${id}`));
  }

  saveCompany(company: Company): Promise<Company> {
    return company.id
      ? firstValueFrom(this.http.put<Company>(`/api/companies/${company.id}`, company))
      : firstValueFrom(this.http.post<Company>('/api/companies', company));
  }

  deleteCompany(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/companies/${id}`));
  }

  // --- contacts ----------------------------------------------------------------

  listContacts(
    search?: string,
    companyId?: number,
    paging: PageQuery = {},
  ): Promise<Page<Contact>> {
    return this.paged<Contact>('/api/contacts', { search, companyId, ...paging });
  }

  getContact(id: number): Promise<Contact> {
    return firstValueFrom(this.http.get<Contact>(`/api/contacts/${id}`));
  }

  saveContact(contact: Contact): Promise<Contact> {
    return contact.id
      ? firstValueFrom(this.http.put<Contact>(`/api/contacts/${contact.id}`, contact))
      : firstValueFrom(this.http.post<Contact>('/api/contacts', contact));
  }

  deleteContact(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/contacts/${id}`));
  }

  // --- deals -------------------------------------------------------------------

  listDeals(
    openOnly = false,
    stage?: DealStage,
    contactId?: number,
    paging: PageQuery = {},
  ): Promise<Page<Deal>> {
    return this.paged<Deal>('/api/deals', { openOnly, stage, contactId, ...paging });
  }

  saveDeal(deal: Deal): Promise<Deal> {
    return deal.id
      ? firstValueFrom(this.http.put<Deal>(`/api/deals/${deal.id}`, deal))
      : firstValueFrom(this.http.post<Deal>('/api/deals', deal));
  }

  moveDeal(id: number, stage: DealStage): Promise<Deal> {
    return firstValueFrom(
      this.http.put<Deal>(`/api/deals/${id}/stage`, null, { params: params({ value: stage }) }),
    );
  }

  deleteDeal(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/deals/${id}`));
  }

  // --- interactions ------------------------------------------------------------

  listInteractions(
    contactId?: number,
    dealId?: number,
    paging: PageQuery = {},
  ): Promise<Page<Interaction>> {
    return this.paged<Interaction>('/api/interactions', { contactId, dealId, ...paging });
  }

  saveInteraction(interaction: Interaction): Promise<Interaction> {
    return interaction.id
      ? firstValueFrom(
          this.http.put<Interaction>(`/api/interactions/${interaction.id}`, interaction),
        )
      : firstValueFrom(this.http.post<Interaction>('/api/interactions', interaction));
  }

  deleteInteraction(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/interactions/${id}`));
  }

  // --- tasks -------------------------------------------------------------------

  listTasks(
    openOnly = false,
    contactId?: number,
    dealId?: number,
    paging: PageQuery = {},
  ): Promise<Page<CrmTask>> {
    return this.paged<CrmTask>('/api/tasks', { openOnly, contactId, dealId, ...paging });
  }

  saveTask(task: CrmTask): Promise<CrmTask> {
    return task.id
      ? firstValueFrom(this.http.put<CrmTask>(`/api/tasks/${task.id}`, task))
      : firstValueFrom(this.http.post<CrmTask>('/api/tasks', task));
  }

  setTaskDone(id: number, done: boolean): Promise<CrmTask> {
    return firstValueFrom(
      this.http.put<CrmTask>(`/api/tasks/${id}/done`, null, { params: params({ value: done }) }),
    );
  }

  deleteTask(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/tasks/${id}`));
  }

  // --- appointments ------------------------------------------------------------

  listAppointments(from: Date, to: Date): Promise<Appointment[]> {
    return firstValueFrom(
      this.http.get<Appointment[]>('/api/appointments', {
        params: params({ from: from.toISOString(), to: to.toISOString() }),
      }),
    );
  }

  appointmentConflicts(
    startsAt: string,
    endsAt: string,
    excludeId?: number | null,
  ): Promise<Appointment[]> {
    return firstValueFrom(
      this.http.get<Appointment[]>('/api/appointments/conflicts', {
        params: params({ startsAt, endsAt, excludeId }),
      }),
    );
  }

  saveAppointment(appointment: Appointment, allowConflict = false): Promise<Appointment> {
    const options = { params: params({ allowConflict }) };
    return appointment.id
      ? firstValueFrom(
          this.http.put<Appointment>(`/api/appointments/${appointment.id}`, appointment, options),
        )
      : firstValueFrom(this.http.post<Appointment>('/api/appointments', appointment, options));
  }

  deleteAppointment(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/appointments/${id}`));
  }

  // --- users -------------------------------------------------------------------

  listUsers(): Promise<User[]> {
    return firstValueFrom(this.http.get<User[]>('/api/users'));
  }

  createUser(request: CreateUserRequest): Promise<User> {
    return firstValueFrom(this.http.post<User>('/api/users', request));
  }

  updateUser(id: number, request: UpdateUserRequest): Promise<User> {
    return firstValueFrom(this.http.put<User>(`/api/users/${id}`, request));
  }

  resetUserPassword(id: number, currentPassword: string, newPassword: string): Promise<User> {
    return firstValueFrom(
      this.http.post<User>(`/api/users/${id}/password`, { currentPassword, newPassword }),
    );
  }

  deleteUser(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/users/${id}`));
  }

  // --- backups -----------------------------------------------------------------

  listBackups(): Promise<BackupFile[]> {
    return firstValueFrom(this.http.get<BackupFile[]>('/api/backups'));
  }

  createBackup(): Promise<BackupFile> {
    return firstValueFrom(this.http.post<BackupFile>('/api/backups', null));
  }

  restoreBackup(name: string): Promise<RestoreResult> {
    return firstValueFrom(
      this.http.post<RestoreResult>(`/api/backups/${encodeURIComponent(name)}/restore`, null),
    );
  }

  restoreBackupUpload(file: File): Promise<RestoreResult> {
    const body = new FormData();
    body.append('file', file, file.name);
    return firstValueFrom(this.http.post<RestoreResult>('/api/backups/restore-upload', body));
  }

  backupSettings(): Promise<BackupSettings> {
    return firstValueFrom(this.http.get<BackupSettings>('/api/backups/settings'));
  }

  updateBackupSettings(retentionDays: number): Promise<BackupSettings> {
    return firstValueFrom(
      this.http.put<BackupSettings>('/api/backups/settings', { retentionDays }),
    );
  }
}
