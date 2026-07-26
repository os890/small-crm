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

package org.os890.smallcrm.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A setting an administrator can change from the interface.
 *
 * <p>Key/value rather than a column per setting, so adding one is a code change instead of a
 * schema change. Settings describe the installation, not the customer data, and are therefore
 * not part of a backup.
 */
@Entity
@Table(name = "app_setting")
public class AppSetting extends PanacheEntityBase {

  /** How many days automatic backups are kept before the rolling clean-up removes them. */
  public static final String BACKUP_RETENTION_DAYS = "backup.retention-days";

  @Id
  @Column(name = "setting_key", length = 100)
  public String key;

  @Column(name = "setting_value", nullable = false, length = 500)
  public String value;
}
