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

package org.smallcrm.api.dto;

import java.time.Instant;
import org.smallcrm.backup.BackupService.BackupFile;

/**
 * One file in the backup folder.
 *
 * @param beforeRestore whether this is a safety copy taken just before a restore, which the
 *     interface highlights because it is the file to pick when undoing one
 */
public record BackupFileDto(
    String name, long sizeBytes, Instant createdAt, boolean beforeRestore) {

  public static BackupFileDto from(BackupFile file) {
    return new BackupFileDto(
        file.name(), file.sizeBytes(), file.createdAt(), file.beforeRestore());
  }
}
