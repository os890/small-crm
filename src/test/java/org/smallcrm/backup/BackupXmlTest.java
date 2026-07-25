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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.smallcrm.api.error.BusinessRuleException;
import org.smallcrm.backup.BackupModel.Backup;
import org.smallcrm.backup.BackupModel.BackupCompany;
import org.smallcrm.backup.BackupModel.BackupContact;
import org.smallcrm.backup.BackupModel.BackupDeal;
import org.smallcrm.domain.DealStage;

/** The backup file format: it has to survive its own round trip and reject hostile input. */
class BackupXmlTest {

  private final BackupXml xml = new BackupXml();

  private static Backup sample() {
    return new Backup(
        BackupModel.FORMAT_VERSION,
        Instant.parse("2026-07-26T08:00:00Z"),
        "admin",
        List.of(
            new BackupCompany(
                1L, "Muster GmbH", "ATU123", "https://muster.example", "office@muster.example",
                "+43 1 234", "Hauptstrasse 1", "1010", "Vienna", "Austria", "A note", "admin",
                Instant.parse("2026-07-01T08:00:00Z"), Instant.parse("2026-07-02T08:00:00Z"))),
        List.of(
            new BackupContact(
                2L, "Maria", "Huber", "maria@example.org", "+43 1 234", "+43 660 1", "Owner", 1L,
                List.of("vip", "retainer"), "Prefers e-mail", "admin",
                Instant.parse("2026-07-01T08:00:00Z"), Instant.parse("2026-07-02T08:00:00Z"))),
        List.of(
            new BackupDeal(
                3L, "Website relaunch", 2L, 1L, new BigDecimal("8400.00"), "EUR",
                DealStage.PROPOSAL, LocalDate.of(2026, 9, 15), null, "admin",
                Instant.parse("2026-07-01T08:00:00Z"), Instant.parse("2026-07-02T08:00:00Z"))),
        List.of(),
        List.of(),
        List.of());
  }

  @Test
  void a_backup_survives_being_written_and_read_again() {
    Backup written = sample();

    Backup read = xml.read(xml.toXml(written).getBytes(StandardCharsets.UTF_8));

    assertThat(read.formatVersion()).isEqualTo(BackupModel.FORMAT_VERSION);
    assertThat(read.createdAt()).isEqualTo(written.createdAt());
    assertThat(read.createdBy()).isEqualTo("admin");
    assertThat(read.companies()).hasSize(1);
    assertThat(read.companies().getFirst().name()).isEqualTo("Muster GmbH");
    assertThat(read.contacts()).hasSize(1);
    assertThat(read.contacts().getFirst().tags()).containsExactly("vip", "retainer");
    assertThat(read.contacts().getFirst().companyId()).isEqualTo(1L);
    assertThat(read.deals().getFirst().amount()).isEqualByComparingTo("8400.00");
    assertThat(read.deals().getFirst().stage()).isEqualTo(DealStage.PROPOSAL);
    assertThat(read.deals().getFirst().expectedCloseDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(read.recordCount()).isEqualTo(3);
  }

  @Test
  void timestamps_are_written_as_readable_text_rather_than_numbers() {
    assertThat(xml.toXml(sample())).contains("2026-07-26T08:00:00Z").doesNotContain("1785");
  }

  @Test
  void an_empty_backup_round_trips_too() {
    Backup empty =
        new Backup(
            BackupModel.FORMAT_VERSION, Instant.parse("2026-07-26T08:00:00Z"), "admin",
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    Backup read = xml.read(xml.toXml(empty).getBytes(StandardCharsets.UTF_8));

    assertThat(read.recordCount()).isZero();
  }

  @Test
  void a_field_a_later_version_added_does_not_stop_an_older_reader() {
    String withExtra =
        xml.toXml(sample()).replace("<name>Muster GmbH</name>",
            "<name>Muster GmbH</name><somethingNew>x</somethingNew>");

    assertThat(xml.read(withExtra.getBytes(StandardCharsets.UTF_8)).companies()).hasSize(1);
  }

  @Test
  void content_that_is_not_a_backup_is_reported_as_such() {
    assertThatThrownBy(() -> xml.read("this is not xml".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("not a readable Small CRM backup");
  }

  @Test
  void a_document_type_definition_is_refused_rather_than_expanded() {
    // The classic billion laughs shape: without DTD support this fails to parse instead of
    // consuming the heap.
    String bomb =
        """
        <?xml version="1.0"?>
        <!DOCTYPE lolz [<!ENTITY lol "lol"><!ENTITY lol2 "&lol;&lol;&lol;&lol;">]>
        <smallCrmBackup formatVersion="1"><createdBy>&lol2;</createdBy></smallCrmBackup>
        """;

    assertThatThrownBy(() -> xml.read(bomb.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(BusinessRuleException.class);
  }
}
