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

package org.os890.smallcrm.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The backup settings an administrator can change.
 *
 * @param retentionDays how long backups are kept before the rolling clean-up removes them
 * @param minRetentionDays lower bound, so the interface can label the field without hard coding
 * @param maxRetentionDays upper bound
 * @param directory absolute path of the backup folder, shown so the user knows where to look
 */
public record BackupSettingsDto(
    @Min(1) @Max(3650) int retentionDays,
    int minRetentionDays,
    int maxRetentionDays,
    String directory) {}
