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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.stream.XMLInputFactory;
import org.smallcrm.api.error.BusinessRuleException;
import org.smallcrm.backup.BackupModel.Backup;

/**
 * Turns a backup into XML text and back.
 *
 * <p>The reader is deliberately strict about nothing except structure: an older file that lacks
 * a field a later version added still loads, which is what makes the format survive upgrades.
 * It is however locked down against the classic XML attacks, since a file can arrive as an
 * upload from a browser.
 */
@ApplicationScoped
public class BackupXml {

  private final XmlMapper mapper = createMapper();

  private static XmlMapper createMapper() {
    XMLInputFactory input = XMLInputFactory.newFactory();
    // A backup is plain data: no external entities, no DTDs. Without this an uploaded file
    // could read local files or hang the server on an entity expansion.
    input.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    input.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

    XmlMapper mapper = XmlMapper.builder(new XmlFactory(input)).build();
    mapper.registerModule(new JavaTimeModule());
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    // A file written by a newer version may carry fields this one does not know yet.
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    return mapper;
  }

  public void write(Backup backup, Path target) throws IOException {
    Files.writeString(target, toXml(backup));
  }

  public String toXml(Backup backup) {
    try {
      return mapper.writeValueAsString(backup);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot render the backup as XML", e);
    }
  }

  /**
   * Parses a backup file.
   *
   * @throws BusinessRuleException if the content is not a Small CRM backup
   */
  public Backup read(byte[] content) {
    try {
      Backup backup = mapper.readValue(content, Backup.class);
      if (backup == null) {
        throw new BusinessRuleException("BACKUP_UNREADABLE", "The file is not a Small CRM backup");
      }
      return backup;
    } catch (IOException e) {
      throw new BusinessRuleException(
          "BACKUP_UNREADABLE", "The file is not a readable Small CRM backup: " + e.getMessage());
    }
  }
}
