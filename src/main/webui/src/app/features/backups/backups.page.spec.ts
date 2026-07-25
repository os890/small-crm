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
import { describe, expect, it } from 'vitest';
import { BackupFile, BackupSettings } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../shared/confirm.service';
import { PageHarness, renderPage } from '../../testing/page-harness';
import { BackupsPage } from './backups.page';

const SETTINGS: BackupSettings = {
  retentionDays: 14,
  minRetentionDays: 1,
  maxRetentionDays: 3650,
  directory: '/srv/small-crm/backup',
};

const AUTOMATIC: BackupFile = {
  name: 'smallcrm-backup-2026-07-26T08-00-00.xml',
  sizeBytes: 4096,
  createdAt: '2026-07-26T08:00:00Z',
  beforeRestore: false,
};

const SAFETY: BackupFile = {
  name: 'before-restore-2026-07-25T20-00-00.xml',
  sizeBytes: 2048,
  createdAt: '2026-07-25T20:00:00Z',
  beforeRestore: true,
};

async function open(
  files: BackupFile[] = [AUTOMATIC, SAFETY],
  settings: BackupSettings = SETTINGS,
): Promise<PageHarness<BackupsPage>> {
  const harness = renderPage(BackupsPage);
  await harness.settle();
  harness.flushGet('/api/backups', files);
  harness.flushGet('/api/backups/settings', settings as unknown as Record<string, unknown>);
  await harness.settle();
  return harness;
}

describe('BackupsPage', () => {
  it('lists the backups and marks the safety copies', async () => {
    const harness = await open();

    const rows = harness.query('backup-rows')?.textContent ?? '';
    expect(rows).toContain('smallcrm-backup-2026-07-26T08-00-00.xml');
    expect(rows).toContain('Before a restore');
    expect(harness.query('backups-empty')).toBeNull();
  });

  it('shows where the files are kept', async () => {
    const harness = await open();

    expect(harness.text('backup-folder')).toContain('/srv/small-crm/backup');
  });

  it('says so when nothing has been backed up yet', async () => {
    const harness = await open([]);

    expect(harness.text('backups-empty')).toBe('No backups yet.');
  });

  it('offers each file for download from the API', async () => {
    const harness = await open();
    const link = harness.fixture.nativeElement.querySelector('tbody a') as HTMLAnchorElement;

    expect(link.getAttribute('href')).toBe(
      '/api/backups/smallcrm-backup-2026-07-26T08-00-00.xml/content',
    );
    expect(link.getAttribute('download')).toBe(AUTOMATIC.name);
  });

  it('creates a backup and refreshes the list', async () => {
    const harness = await open([]);

    await harness.click('create-backup');

    const request = harness.http.expectOne('/api/backups');
    expect(request.request.method).toBe('POST');
    request.flush(AUTOMATIC);
    await harness.settle();

    harness.flushGet('/api/backups', [AUTOMATIC]);
    harness.flushGet('/api/backups/settings', SETTINGS as unknown as Record<string, unknown>);
    await harness.settle();

    expect(harness.query('backup-rows')?.textContent).toContain(AUTOMATIC.name);
  });

  it('prefills the retention period and sends a change', async () => {
    const harness = await open();

    expect(harness.query<HTMLInputElement>('retention-days')?.value).toBe('14');

    await harness.type('retention-days', '30');
    await harness.click('save-retention');

    const request = harness.http.expectOne('/api/backups/settings');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ retentionDays: 30 });
    request.flush({ ...SETTINGS, retentionDays: 30 });
    await harness.settle();

    harness.flushGet('/api/backups', []);
    harness.flushGet('/api/backups/settings', {
      ...SETTINGS,
      retentionDays: 30,
    } as unknown as Record<string, unknown>);
    await harness.settle();

    expect(harness.query<HTMLInputElement>('retention-days')?.value).toBe('30');
  });

  it('reports a rejected retention period on the field', async () => {
    const harness = await open();

    await harness.type('retention-days', '0');
    await harness.click('save-retention');
    harness.http
      .expectOne('/api/backups/settings')
      .flush(
        { code: 'RETENTION_OUT_OF_RANGE', message: 'nope', details: null },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.settle();

    expect(harness.text('retention-error')).toBe(
      'Please enter a retention period within the allowed range.',
    );
  });

  it('spells out what a restore destroys before doing it', async () => {
    const harness = await open();
    const confirm = TestBed.inject(ConfirmService);

    await harness.click(`restore-${AUTOMATIC.name}`);

    const request = confirm.request();
    expect(request?.question).toContain(AUTOMATIC.name);
    expect(request?.question).toContain('deletes all contacts');
    expect(request?.hint).toContain('before-restore');
    expect(request?.destructive).toBe(true);

    // Nothing is sent while the prompt is open.
    harness.http.verify();
  });

  it('does nothing when the restore prompt is dismissed', async () => {
    const harness = await open();
    const confirm = TestBed.inject(ConfirmService);

    await harness.click(`restore-${AUTOMATIC.name}`);
    confirm.answer(false);
    await harness.settle();

    harness.http.verify();
  });

  it('restores from the folder and reports the safety copy', async () => {
    const harness = await open();
    const confirm = TestBed.inject(ConfirmService);

    await harness.click(`restore-${AUTOMATIC.name}`);
    confirm.answer(true);
    await harness.settle();

    const request = harness.http.expectOne(
      `/api/backups/${encodeURIComponent(AUTOMATIC.name)}/restore`,
    );
    expect(request.request.method).toBe('POST');
    request.flush({ recordCount: 42, safetyCopy: SAFETY.name });
    await harness.settle();

    const messages = TestBed.inject(ToastService)
      .toasts()
      .map((toast) => toast.text);
    expect(messages.some((text) => text.includes('42 records restored'))).toBe(true);
    expect(messages.some((text) => text.includes(SAFETY.name))).toBe(true);

    harness.flushGet('/api/backups', [AUTOMATIC]);
    harness.flushGet('/api/backups/settings', SETTINGS as unknown as Record<string, unknown>);
    await harness.settle();
  });

  it('keeps the upload button disabled until a file is chosen', async () => {
    const harness = await open();

    expect(harness.query<HTMLButtonElement>('restore-upload')?.disabled).toBe(true);
  });

  it('uploads the chosen file as multipart form data', async () => {
    const harness = await open();
    const confirm = TestBed.inject(ConfirmService);

    const input = harness.query<HTMLInputElement>('backup-upload')!;
    const file = new File(['<smallCrmBackup formatVersion="1"/>'], 'mine.xml', {
      type: 'application/xml',
    });
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
    await harness.settle();

    expect(harness.query<HTMLButtonElement>('restore-upload')?.disabled).toBe(false);

    await harness.click('restore-upload');
    expect(confirm.request()?.question).toContain('deletes all contacts');
    confirm.answer(true);
    await harness.settle();

    const request = harness.http.expectOne('/api/backups/restore-upload');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeInstanceOf(FormData);
    // jsdom hands back an equivalent File rather than the same reference, so compare the name.
    expect(((request.request.body as FormData).get('file') as File).name).toBe('mine.xml');
    request.flush({ recordCount: 7, safetyCopy: SAFETY.name });
    await harness.settle();

    harness.flushGet('/api/backups', []);
    harness.flushGet('/api/backups/settings', SETTINGS as unknown as Record<string, unknown>);
    await harness.settle();
  });

  it('reports an unreadable upload in plain words', async () => {
    const harness = await open();
    const confirm = TestBed.inject(ConfirmService);

    const input = harness.query<HTMLInputElement>('backup-upload')!;
    Object.defineProperty(input, 'files', {
      value: [new File(['nonsense'], 'mine.xml')],
      configurable: true,
    });
    input.dispatchEvent(new Event('change'));
    await harness.settle();

    await harness.click('restore-upload');
    confirm.answer(true);
    await harness.settle();

    harness.http
      .expectOne('/api/backups/restore-upload')
      .flush(
        { code: 'BACKUP_UNREADABLE', message: 'nope', details: null },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.settle();

    expect(
      TestBed.inject(ToastService)
        .toasts()
        .map((toast) => toast.text),
    ).toContain('This file is not a Small CRM backup.');
  });

  it('translates the screen into German', async () => {
    const harness = renderPage(BackupsPage, { language: 'de' });
    await harness.settle();
    harness.flushGet('/api/backups', []);
    harness.flushGet('/api/backups/settings', SETTINGS as unknown as Record<string, unknown>);
    await harness.settle();

    expect(harness.fixture.nativeElement.textContent).toContain('Sicherungen');
    expect(harness.fixture.nativeElement.textContent).toContain('Jetzt sichern');
    expect(harness.text('backups-empty')).toBe('Noch keine Sicherungen.');
  });
});
