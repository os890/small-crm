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

package org.smallcrm.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The naming rules that keep the backup folder tidy and the file endpoints safe. */
class BackupFilesTest {

  private static final Instant AT = Instant.parse("2026-07-26T08:05:09Z");

  @Test
  void names_carry_a_utc_timestamp_that_is_legal_on_every_file_system() {
    assertThat(BackupFiles.automaticName(AT)).isEqualTo("smallcrm-backup-2026-07-26T08-05-09.xml");
    assertThat(BackupFiles.beforeRestoreName(AT))
        .isEqualTo("before-restore-2026-07-26T08-05-09.xml");
    // A colon is not allowed in a Windows file name and these folders get copied around.
    assertThat(BackupFiles.automaticName(AT)).doesNotContain(":");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "smallcrm-backup-2026-07-26T08-05-09.xml",
        "smallcrm-backup-2026-07-26T08-05-09-1.xml",
        "before-restore-2026-07-26T08-05-09.xml"
      })
  void files_this_application_wrote_are_recognised(String name) {
    assertThat(BackupFiles.isOwnBackup(name)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "notes.xml",
        "smallcrm-backup.xml",
        "smallcrm-backup-2026-07-26.xml",
        "smallcrm-backup-2026-07-26T08-05-09.txt",
        "my-own-export.xml",
        ""
      })
  void anything_else_in_the_folder_is_left_alone(String name) {
    // Neither offered for restore nor deleted by the rolling clean-up.
    assertThat(BackupFiles.isOwnBackup(name)).isFalse();
    assertThat(BackupFiles.isSafeName(name)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "../smallcrm-backup-2026-07-26T08-05-09.xml",
        "../../etc/passwd",
        "sub/smallcrm-backup-2026-07-26T08-05-09.xml",
        "sub\\smallcrm-backup-2026-07-26T08-05-09.xml"
      })
  void a_name_that_tries_to_leave_the_folder_is_rejected(String name) {
    assertThat(BackupFiles.isSafeName(name)).isFalse();
  }

  @Test
  void a_missing_name_is_rejected_rather_than_throwing() {
    assertThat(BackupFiles.isSafeName(null)).isFalse();
    assertThat(BackupFiles.isSafeName("   ")).isFalse();
  }
}
