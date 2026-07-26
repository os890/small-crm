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

package org.os890.smallcrm.backup;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import org.os890.smallcrm.api.error.BusinessRuleException;
import org.os890.smallcrm.domain.AppSetting;

/** Reads and writes the backup settings an administrator controls from the interface. */
@ApplicationScoped
public class BackupSettingsService {

  /** Used when the setting is missing or unreadable, and offered as the default in the UI. */
  public static final int DEFAULT_RETENTION_DAYS = 14;

  /** One day is the shortest useful window; anything less would delete today's own backups. */
  public static final int MIN_RETENTION_DAYS = 1;

  /** Ten years, purely to stop a typo turning into "keep for ever". */
  public static final int MAX_RETENTION_DAYS = 3650;

  private static final Logger LOG = Logger.getLogger(BackupSettingsService.class);

  /**
   * How many days backups are kept.
   *
   * <p>Falls back to the default rather than failing: a corrupt setting must not stop the
   * application from taking backups.
   */
  public int retentionDays() {
    AppSetting setting = AppSetting.findById(AppSetting.BACKUP_RETENTION_DAYS);
    if (setting == null) {
      return DEFAULT_RETENTION_DAYS;
    }
    try {
      return clamp(Integer.parseInt(setting.value.trim()));
    } catch (NumberFormatException e) {
      LOG.warnf(
          "Setting '%s' holds '%s', which is not a number; falling back to %d days",
          AppSetting.BACKUP_RETENTION_DAYS, setting.value, DEFAULT_RETENTION_DAYS);
      return DEFAULT_RETENTION_DAYS;
    }
  }

  /**
   * Changes how long backups are kept.
   *
   * @throws BusinessRuleException if the period is outside the accepted range
   */
  @Transactional
  public int updateRetentionDays(int days) {
    if (days < MIN_RETENTION_DAYS || days > MAX_RETENTION_DAYS) {
      throw new BusinessRuleException(
          "RETENTION_OUT_OF_RANGE",
          "The retention period must be between "
              + MIN_RETENTION_DAYS
              + " and "
              + MAX_RETENTION_DAYS
              + " days");
    }
    AppSetting setting = AppSetting.findById(AppSetting.BACKUP_RETENTION_DAYS);
    if (setting == null) {
      setting = new AppSetting();
      setting.key = AppSetting.BACKUP_RETENTION_DAYS;
      setting.value = String.valueOf(days);
      setting.persist();
    } else {
      setting.value = String.valueOf(days);
    }
    return days;
  }

  private static int clamp(int days) {
    return Math.min(MAX_RETENTION_DAYS, Math.max(MIN_RETENTION_DAYS, days));
  }
}
