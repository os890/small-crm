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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.jboss.logging.Logger;

/**
 * Forces written files all the way to the disk.
 *
 * <p>Writing a file and renaming it into place protects against a crash halfway through the
 * write, but not against a power cut: a journalling filesystem may persist the rename while the
 * contents are still in the page cache. The result is a zero length file under a name that looks
 * entirely valid, listed newest-first in the interface, which only reveals itself as unreadable
 * at the moment somebody needs it.
 */
final class Durability {

  private static final Logger LOG = Logger.getLogger(Durability.class);

  private Durability() {
  }

  /** Writes text and forces it to disk before returning. */
  static void writeAndSync(Path file, String content) throws IOException {
    Files.writeString(file, content);
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
  }

  /**
   * Forces a directory entry to disk, so the rename itself survives a power cut.
   *
   * <p>Not supported on every platform — Windows refuses to open a directory as a channel — so a
   * failure here is logged and tolerated rather than failing the backup that has already been
   * written.
   */
  static void syncDirectory(Path directory) {
    try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    } catch (IOException | UnsupportedOperationException e) {
      LOG.debugf("Could not flush the directory entry for %s: %s", directory, e.toString());
    }
  }
}
