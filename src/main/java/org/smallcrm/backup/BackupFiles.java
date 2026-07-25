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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Naming rules for the files in the backup folder.
 *
 * <p>The timestamp is UTC and uses hyphens instead of colons, because a colon is not a legal
 * character in a Windows file name and these folders get copied between machines.
 */
public final class BackupFiles {

  /** Written automatically after a change, and by the "create backup" button. */
  public static final String AUTOMATIC_PREFIX = "smallcrm-backup-";

  /** Written immediately before a restore, so an unwanted restore can itself be undone. */
  public static final String BEFORE_RESTORE_PREFIX = "before-restore-";

  public static final String EXTENSION = ".xml";

  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneOffset.UTC);

  /** Matches the files this application writes, so nothing else in the folder is ever touched. */
  private static final Pattern OWN_FILE =
      Pattern.compile("^(" + AUTOMATIC_PREFIX + "|" + BEFORE_RESTORE_PREFIX + ")"
          + "\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}(-\\d+)?\\.xml$");

  private BackupFiles() {
  }

  public static String automaticName(Instant at) {
    return AUTOMATIC_PREFIX + STAMP.format(at) + EXTENSION;
  }

  public static String beforeRestoreName(Instant at) {
    return BEFORE_RESTORE_PREFIX + STAMP.format(at) + EXTENSION;
  }

  /**
   * Whether the rolling clean-up and the file list may consider this name.
   *
   * <p>Anything a user dropped into the folder by hand is left alone, in both directions: it is
   * never listed as a restore candidate and never deleted.
   */
  public static boolean isOwnBackup(String fileName) {
    return OWN_FILE.matcher(fileName).matches();
  }

  /**
   * Rejects a name that could escape the backup folder.
   *
   * <p>The name arrives from the browser as the chosen entry of a drop-down, but nothing stops a
   * caller from sending {@code ../../etc/passwd} instead.
   */
  public static boolean isSafeName(String fileName) {
    return fileName != null
        && !fileName.isBlank()
        && fileName.indexOf('/') < 0
        && fileName.indexOf('\\') < 0
        && !fileName.contains("..")
        && isOwnBackup(fileName);
  }
}
