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

/** Mirrors of the JSON shapes the Quarkus API sends and accepts. */

export interface User {
  id: number;
  /** Optimistic locking token. Send back what the GET returned. */
  version?: number;
  username: string;
  fullName: string | null;
  email: string | null;
  roles: string[];
  admin: boolean;
  active: boolean;
  mustChangePassword: boolean;
  createdAt: string;
}

export interface Company {
  id?: number;
  /** Optimistic locking token. Send back what the GET returned. */
  version?: number;
  name: string;
  vatId?: string | null;
  website?: string | null;
  email?: string | null;
  phone?: string | null;
  street?: string | null;
  postalCode?: string | null;
  city?: string | null;
  country?: string | null;
  notes?: string | null;
  ownerName?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface Contact {
  id?: number;
  /** Optimistic locking token. Send back what the GET returned. */
  version?: number;
  firstName: string;
  lastName: string;
  email?: string | null;
  phone?: string | null;
  mobile?: string | null;
  position?: string | null;
  companyId?: number | null;
  companyName?: string | null;
  tags?: string[];
  notes?: string | null;
  displayName?: string;
  ownerName?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export const DEAL_STAGES = ['LEAD', 'QUALIFIED', 'PROPOSAL', 'WON', 'LOST'] as const;
export type DealStage = (typeof DEAL_STAGES)[number];

/** Stages a deal is still actively worked on, in the order the pipeline shows them. */
export const OPEN_DEAL_STAGES: DealStage[] = ['LEAD', 'QUALIFIED', 'PROPOSAL'];

export interface Deal {
  id?: number;
  /** Optimistic locking token. Send back what the GET returned. */
  version?: number;
  title: string;
  contactId?: number | null;
  contactName?: string | null;
  companyId?: number | null;
  companyName?: string | null;
  amount?: number | null;
  currency?: string | null;
  stage?: DealStage;
  expectedCloseDate?: string | null;
  notes?: string | null;
  ownerName?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export const INTERACTION_TYPES = ['CALL', 'EMAIL', 'MEETING', 'NOTE'] as const;
export type InteractionType = (typeof INTERACTION_TYPES)[number];

export interface Interaction {
  id?: number;
  /** Optimistic locking token. Send back what the GET returned. */
  version?: number;
  type: InteractionType;
  occurredAt: string;
  subject: string;
  notes?: string | null;
  contactId: number;
  contactName?: string | null;
  dealId?: number | null;
  dealTitle?: string | null;
  ownerName?: string | null;
}

export const TASK_PRIORITIES = ['LOW', 'NORMAL', 'HIGH'] as const;
export type TaskPriority = (typeof TASK_PRIORITIES)[number];

export interface CrmTask {
  id?: number;
  /** Optimistic locking token. Send back what the GET returned. */
  version?: number;
  title: string;
  description?: string | null;
  dueDate?: string | null;
  done: boolean;
  completedAt?: string | null;
  priority?: TaskPriority;
  contactId?: number | null;
  contactName?: string | null;
  dealId?: number | null;
  dealTitle?: string | null;
  overdue?: boolean;
  ownerName?: string | null;
}

export interface Appointment {
  id?: number;
  /** Optimistic locking token. Send back what the GET returned. */
  version?: number;
  title: string;
  startsAt: string;
  endsAt: string;
  timeZone?: string | null;
  location?: string | null;
  notes?: string | null;
  contactId?: number | null;
  contactName?: string | null;
  dealId?: number | null;
  dealTitle?: string | null;
  ownerName?: string | null;
}

/**
 * One page of a list endpoint.
 *
 * <p>Every list the API serves is paged, so a screen has to know both what it received and how
 * much there is in total — otherwise it cannot tell the user they are looking at the first fifty
 * of eight hundred.
 */
export interface Page<T> {
  items: T[];
  /** How many records match in total, across all pages. */
  total: number;
  /** Zero-based index of the page in {@link items}. */
  page: number;
  size: number;
}

export interface Dashboard {
  contactCount: number;
  companyCount: number;
  openDealCount: number;
  openDealValue: number;
  /** The same total split per currency; a mixed pipeline has no single meaningful sum. */
  openDealValueByCurrency: Record<string, number>;
  overdueTasks: CrmTask[];
  tasksDueToday: CrmTask[];
  upcomingAppointments: Appointment[];
  recentInteractions: Interaction[];
}

export interface CreateUserRequest {
  username: string;
  password: string;
  fullName?: string | null;
  email?: string | null;
  admin: boolean;
}

export interface UpdateUserRequest {
  fullName?: string | null;
  email?: string | null;
  admin: boolean;
  active: boolean;
  version?: number;
}

export interface BackupFile {
  name: string;
  sizeBytes: number;
  createdAt: string;
  /** A safety copy taken just before a restore; the file to pick when undoing one. */
  beforeRestore: boolean;
}

export interface BackupSettings {
  retentionDays: number;
  minRetentionDays: number;
  maxRetentionDays: number;
  /** Absolute path of the backup folder, shown so the user knows where the files are. */
  directory: string;
}

export interface RestoreResult {
  recordCount: number;
  /** Name of the before-restore file holding the state that was replaced. */
  safetyCopy: string;
  /** Records the file held but that could not be loaded, for instance an orphaned activity. */
  skipped: number;
  /** Records whose owner name matches no account here; expected on a fresh installation. */
  unresolvedOwners: number;
}

/** The single error shape every failing endpoint returns. */
export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown> | null;
}
