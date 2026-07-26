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

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Takes a complete copy of the database file, alongside the XML export.
 *
 * <p>The XML deliberately leaves out accounts, so it cannot rebuild an installation on its own:
 * restoring it onto a fresh machine gives the records but no users, every record ownerless, and
 * the retention setting back at its default. That is fine for the everyday case the XML is for —
 * undoing a mistake on a running installation — and useless for the case that actually loses
 * data, which is the disk dying.
 *
 * <p>H2's {@code BACKUP TO} is safe to run while the application is serving requests and
 * produces a zip of the whole database, users and settings included. Copying the {@code .mv.db}
 * by hand while the process holds it open is not safe and can capture a torn file.
 */
@ApplicationScoped
public class DatabaseSnapshot {

  private static final Logger LOG = Logger.getLogger(DatabaseSnapshot.class);

  static final String PREFIX = "smallcrm-snapshot-";
  static final String EXTENSION = ".zip";

  /**
   * Snapshots kept. They are far larger than the XML files, so they are capped by count rather
   * than by the XML retention period.
   */
  static final int KEEP = 5;

  @Inject AgroalDataSource dataSource;

  /**
   * Writes a snapshot named after the backup it accompanies.
   *
   * <p>Never fails the surrounding backup: an XML export that succeeded is worth keeping even if
   * the snapshot could not be produced.
   */
  public void writeBeside(Path xmlBackup) {
    String stem = xmlBackup.getFileName().toString().replaceAll("\\.xml$", "");
    Path target = xmlBackup.resolveSibling(PREFIX + stripPrefixes(stem) + EXTENSION);
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      // Quoted and built from a path this application chose, never from user input.
      statement.execute("BACKUP TO '" + target.toAbsolutePath() + "'");
      LOG.infof("Wrote database snapshot %s", target.getFileName());
      prune(target.getParent());
    } catch (SQLException e) {
      LOG.warnf(e, "Could not write a database snapshot to %s", target);
    }
  }

  /** Snapshots in a folder, newest first. */
  public List<Path> list(Path directory) {
    if (directory == null || !Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().startsWith(PREFIX))
          .filter(path -> path.getFileName().toString().endsWith(EXTENSION))
          .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
          .toList();
    } catch (java.io.IOException e) {
      LOG.warnf(e, "Could not list snapshots in %s", directory);
      return List.of();
    }
  }

  private void prune(Path directory) {
    List<Path> snapshots = list(directory);
    for (Path old : snapshots.subList(Math.min(KEEP, snapshots.size()), snapshots.size())) {
      try {
        Files.deleteIfExists(old);
      } catch (java.io.IOException e) {
        LOG.warnf(e, "Could not remove the old snapshot %s", old.getFileName());
      }
    }
  }

  private static String stripPrefixes(String stem) {
    return stem.replace(BackupFiles.AUTOMATIC_PREFIX, "").replace(
        BackupFiles.BEFORE_RESTORE_PREFIX, "");
  }
}
