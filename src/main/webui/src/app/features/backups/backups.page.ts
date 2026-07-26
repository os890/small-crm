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

import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { FormatService } from '../../core/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { BackupFile, BackupSettings, RestoreResult } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ConfirmService } from '../../shared/confirm.service';

/**
 * Backup and restore.
 *
 * <p>Restoring throws away everything currently in the CRM, so it is never one click: the prompt
 * names the file, spells out what disappears and points out that the current state is saved
 * first.
 */
@Component({
  selector: 'app-backups',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="stack">
      <div class="row-between">
        <h1>{{ t('backup.heading') }}</h1>
        <button
          type="button"
          class="btn btn-primary"
          data-testid="create-backup"
          [disabled]="busy()"
          (click)="createNow()"
        >
          {{ t('backup.createNow') }}
        </button>
      </div>

      <p class="muted">{{ t('backup.explain') }}</p>
      <p class="notice" data-testid="backup-folder">
        {{ t('backup.folder') }}: <code>{{ settings()?.directory }}</code>
      </p>
      <p class="faint">{{ t('backup.noAccountsHint') }}</p>

      @if (settings(); as current) {
        <section class="card card-pad stack">
          <h2>{{ t('backup.retention') }}</h2>
          <div class="row">
            <div class="field retention" [class.invalid]="retentionError()">
              <label for="retention-days" class="visually-hidden">{{
                t('backup.retention')
              }}</label>
              <input
                id="retention-days"
                type="number"
                data-testid="retention-days"
                [min]="current.minRetentionDays"
                [max]="current.maxRetentionDays"
                [(ngModel)]="retentionDays"
              />
            </div>
            <span>{{ t('backup.retentionUnit') }}</span>
            <button
              type="button"
              class="btn"
              data-testid="save-retention"
              [disabled]="busy()"
              (click)="saveRetention()"
            >
              {{ t('action.save') }}
            </button>
          </div>
          <span class="field-hint">
            {{
              t('backup.retentionHint', {
                min: current.minRetentionDays,
                max: current.maxRetentionDays,
              })
            }}
          </span>
          @if (retentionError(); as message) {
            <span class="field-error" data-testid="retention-error">{{ message }}</span>
          }
        </section>
      }

      <section class="card card-pad stack">
        <h2>{{ t('backup.restoreFromUpload') }}</h2>
        <div class="row">
          <input
            type="file"
            accept=".xml,application/xml,text/xml"
            data-testid="backup-upload"
            (change)="onFileChosen($event)"
          />
          <button
            type="button"
            class="btn btn-danger"
            data-testid="restore-upload"
            [disabled]="busy() || !chosenFile()"
            (click)="restoreUpload()"
          >
            {{ t('backup.upload') }}
          </button>
        </div>
        <span class="field-hint">{{ t('backup.uploadHint') }}</span>
      </section>

      <section class="card">
        <div class="card-pad">
          <h2>{{ t('backup.files') }}</h2>
        </div>
        @if (loading()) {
          <p class="empty-state">{{ t('common.loading') }}</p>
        } @else if (files().length === 0) {
          <p class="empty-state" data-testid="backups-empty">{{ t('backup.empty') }}</p>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>{{ t('backup.fileName') }}</th>
                  <th>{{ t('backup.fileDate') }}</th>
                  <th>{{ t('backup.fileSize') }}</th>
                  <th class="actions">{{ t('common.actions') }}</th>
                </tr>
              </thead>
              <tbody data-testid="backup-rows">
                @for (file of files(); track file.name) {
                  <tr>
                    <td>
                      <code>{{ file.name }}</code>
                      @if (file.beforeRestore) {
                        <span class="badge badge-warning">{{ t('backup.beforeRestore') }}</span>
                      }
                    </td>
                    <td class="muted">{{ format.dateTime(file.createdAt) }}</td>
                    <td class="muted">{{ kilobytes(file.sizeBytes) }}</td>
                    <td class="actions">
                      <a class="btn btn-sm" [href]="downloadUrl(file)" [download]="file.name">
                        {{ t('backup.download') }}
                      </a>
                      <button
                        type="button"
                        class="btn btn-sm btn-danger"
                        [attr.data-testid]="'restore-' + file.name"
                        [disabled]="busy()"
                        (click)="restoreFromFolder(file)"
                      >
                        {{ t('backup.restore') }}
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    </div>
  `,
  styles: `
    .retention {
      width: 8rem;
    }

    code {
      font-size: 0.875rem;
      word-break: break-all;
    }

    .badge {
      margin-inline-start: var(--space-2);
    }
  `,
})
export class BackupsPage {
  private readonly api = inject(ApiService);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly i18n = inject(I18nService);
  protected readonly format = inject(FormatService);
  protected readonly t = this.i18n.t;

  protected readonly files = signal<BackupFile[]>([]);
  protected readonly settings = signal<BackupSettings | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly retentionError = signal('');
  protected readonly chosenFile = signal<File | null>(null);
  protected retentionDays = 14;

  constructor() {
    void this.load();
  }

  protected kilobytes(bytes: number): string {
    return `${Math.max(1, Math.round(bytes / 1024))} kB`;
  }

  protected downloadUrl(file: BackupFile): string {
    return `/api/backups/${encodeURIComponent(file.name)}/content`;
  }

  protected onFileChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.chosenFile.set(input.files?.[0] ?? null);
  }

  /**
   * Empties the file input.
   *
   * <p>Without this the field still shows the name after a restore, and choosing the same file
   * again fires no change event, so the button can never be re-armed.
   */
  private clearFileInput(): void {
    const input = this.host.nativeElement.querySelector(
      '[data-testid="backup-upload"]',
    ) as HTMLInputElement | null;
    if (input) {
      input.value = '';
    }
    this.chosenFile.set(null);
  }

  protected async createNow(): Promise<void> {
    await this.run(async () => {
      await this.api.createBackup();
      this.toasts.success(this.t('backup.created'));
      await this.load();
    });
  }

  protected async saveRetention(): Promise<void> {
    this.retentionError.set('');
    await this.run(async () => {
      try {
        const updated = await this.api.updateBackupSettings(this.retentionDays);
        this.settings.set(updated);
        this.retentionDays = updated.retentionDays;
        this.toasts.success(this.t('backup.retentionSaved'));
        // Tightening the period may have removed files straight away.
        await this.load();
      } catch (error) {
        const problem = this.toasts.problem(error);
        this.retentionError.set(this.i18n.errorMessage(problem.code, problem.message));
      }
    });
  }

  protected async restoreFromFolder(file: BackupFile): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: this.t('backup.restoreWarningTitle'),
      question: this.t('backup.restoreWarning', { name: file.name }),
      hint: this.t('backup.restoreSafetyHint'),
      confirmLabel: this.t('backup.restoreConfirm'),
      destructive: true,
    });
    if (!confirmed) {
      return;
    }
    await this.run(async () => {
      const result = await this.api.restoreBackup(file.name);
      this.reportRestore(result);
      await this.load();
    });
  }

  protected async restoreUpload(): Promise<void> {
    const file = this.chosenFile();
    if (!file) {
      return;
    }
    const confirmed = await this.confirm.ask({
      title: this.t('backup.restoreWarningTitle'),
      question: this.t('backup.restoreWarningUpload'),
      hint: this.t('backup.restoreSafetyHint'),
      confirmLabel: this.t('backup.restoreConfirm'),
      destructive: true,
    });
    if (!confirmed) {
      return;
    }
    await this.run(async () => {
      const result = await this.api.restoreBackupUpload(file);
      this.reportRestore(result);
      this.clearFileInput();
      await this.load();
    });
  }

  private reportRestore(result: RestoreResult): void {
    this.toasts.success(
      this.t('backup.restored', { count: result.recordCount, file: result.safetyCopy }),
    );
    // Said out loud rather than only logged: restoring onto a fresh installation leaves every
    // record without an owner, and a plain record count made that look like a clean restore.
    if (result.unresolvedOwners > 0) {
      this.toasts.error(this.t('backup.unresolvedOwners', { count: result.unresolvedOwners }));
    }
    if (result.skipped > 0) {
      this.toasts.error(this.t('backup.skippedRecords', { count: result.skipped }));
    }
  }

  /** Runs an action with the buttons disabled, reporting anything that goes wrong once. */
  private async run(action: () => Promise<void>): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    try {
      await action();
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.busy.set(false);
    }
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      const [files, settings] = await Promise.all([
        this.api.listBackups(),
        this.api.backupSettings(),
      ]);
      this.files.set(files);
      this.settings.set(settings);
      this.retentionDays = settings.retentionDays;
    } catch (error) {
      this.toasts.problem(error);
    } finally {
      this.loading.set(false);
    }
  }
}
