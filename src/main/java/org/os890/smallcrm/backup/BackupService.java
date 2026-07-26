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

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.os890.smallcrm.api.error.BusinessRuleException;
import org.os890.smallcrm.api.error.NotFoundException;
import org.os890.smallcrm.backup.BackupModel.Backup;
import org.os890.smallcrm.backup.BackupModel.BackupAppointment;
import org.os890.smallcrm.backup.BackupModel.BackupCompany;
import org.os890.smallcrm.backup.BackupModel.BackupContact;
import org.os890.smallcrm.backup.BackupModel.BackupDeal;
import org.os890.smallcrm.backup.BackupModel.BackupInteraction;
import org.os890.smallcrm.backup.BackupModel.BackupTask;
import org.os890.smallcrm.domain.AppUser;
import org.os890.smallcrm.domain.Appointment;
import org.os890.smallcrm.domain.BaseEntity;
import org.os890.smallcrm.domain.Company;
import org.os890.smallcrm.domain.Contact;
import org.os890.smallcrm.domain.CrmTask;
import org.os890.smallcrm.domain.Deal;
import org.os890.smallcrm.domain.Interaction;
import org.os890.smallcrm.security.CurrentUser;

/**
 * Writes the customer data to an XML file and puts it back.
 *
 * <p>A backup holds business records only. Accounts are left out on purpose, so a backup file
 * can be handed to an accountant or moved between machines without carrying password hashes;
 * the trade is that a restore re-links records to their owner by user name, and leaves the owner
 * empty where no such account exists here.
 */
@ApplicationScoped
public class BackupService {

  private static final Logger LOG = Logger.getLogger(BackupService.class);

  /** Guards against a hand-edited or truncated file being loaded as if it were complete. */
  private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

  @ConfigProperty(name = "smallcrm.backup-dir", defaultValue = "./backup")
  String backupDir;

  @ConfigProperty(name = "smallcrm.backup.snapshot-enabled", defaultValue = "true")
  boolean snapshotEnabled;

  @Inject BackupXml xml;
  @Inject BackupSettingsService settings;
  @Inject CurrentUser currentUser;
  @Inject Clock clock;
  @Inject DatabaseSnapshot snapshot;

  /**
   * Serialises everything that writes into the backup folder or replaces the database.
   *
   * <p>Three problems share this one cause. Two backups written in the same second could pick
   * the same file name, and the atomic move that protects a half-written file would then
   * silently replace the other one. A restore ran as three separate steps, so a record saved
   * between the safety copy and the delete vanished from both. And two restores at once could
   * interleave their delete and insert phases.
   */
  private final java.util.concurrent.locks.ReentrantLock fileLock =
      new java.util.concurrent.locks.ReentrantLock();

  /** A file in the backup folder, as the interface lists it. */
  public record BackupFile(String name, long sizeBytes, Instant createdAt, boolean beforeRestore) {}

  public Path directory() {
    return Path.of(backupDir).toAbsolutePath().normalize();
  }

  /** Every backup this application wrote, newest first. */
  public List<BackupFile> list() {
    Path dir = directory();
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> BackupFiles.isOwnBackup(path.getFileName().toString()))
          .map(BackupService::describe)
          .sorted(Comparator.comparing(BackupFile::createdAt).reversed())
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the backup folder " + dir, e);
    }
  }

  public Path resolve(String fileName) {
    if (!BackupFiles.isSafeName(fileName)) {
      throw new NotFoundException("Backup", fileName);
    }
    Path file = directory().resolve(fileName).normalize();
    if (!file.startsWith(directory()) || !Files.isRegularFile(file)) {
      throw new NotFoundException("Backup", fileName);
    }
    return file;
  }

  /** Reads the current data out of the database. Runs in a transaction for a consistent view. */
  @Transactional
  public Backup export() {
    String by = currentUser.find().map(user -> user.username).orElse(null);
    return new Backup(
        BackupModel.FORMAT_VERSION,
        Instant.now(clock),
        by,
        Company.<Company>listAll(Sort.by("id")).stream().map(BackupService::toBackup).toList(),
        Contact.<Contact>listAll(Sort.by("id")).stream().map(BackupService::toBackup).toList(),
        Deal.<Deal>listAll(Sort.by("id")).stream().map(BackupService::toBackup).toList(),
        Interaction.<Interaction>listAll(Sort.by("id")).stream()
            .map(BackupService::toBackup)
            .toList(),
        CrmTask.<CrmTask>listAll(Sort.by("id")).stream().map(BackupService::toBackup).toList(),
        Appointment.<Appointment>listAll(Sort.by("id")).stream()
            .map(BackupService::toBackup)
            .toList());
  }

  /**
   * Writes a backup of the current data.
   *
   * @param beforeRestore whether this is the safety copy taken ahead of a restore, which is named
   *     differently so it stands out in the folder
   * @return the file that was written
   */
  public BackupFile write(boolean beforeRestore) {
    fileLock.lock();
    try {
      return writeLocked(beforeRestore);
    } finally {
      fileLock.unlock();
    }
  }

  private BackupFile writeLocked(boolean beforeRestore) {
    Backup data = export();
    Instant at = Instant.now(clock);
    Path dir = directory();
    try {
      Files.createDirectories(dir);
      Path file = uniquePath(dir, beforeRestore, at);
      // Written beside the target and moved into place, so a crash mid-write cannot leave a
      // half finished file looking like a usable backup.
      Path temp = Files.createTempFile(dir, ".writing-", ".xml");
      try {
        // Forced to disk before the rename. A journalling filesystem can otherwise persist the
        // rename while the contents are still in the page cache, so a power cut leaves an empty
        // file under a perfectly valid name that only reveals itself at restore time.
        xml.writeDurably(data, temp);
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
        Durability.syncDirectory(dir);
      } catch (IOException e) {
        Files.deleteIfExists(temp);
        throw e;
      }
      LOG.infof("Wrote backup %s with %d records", file.getFileName(), data.recordCount());
      if (snapshotEnabled) {
        // Unlike the XML this includes accounts and settings, which is what a rebuild after a
        // disk loss actually needs.
        snapshot.writeBeside(file);
      }
      applyRetention();
      return describe(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write a backup into " + dir, e);
    }
  }

  /** Deletes the backups that are older than the configured retention period. */
  public int applyRetention() {
    int days = settings.retentionDays();
    Instant cutoff = Instant.now(clock).minus(Duration.ofDays(days));
    int removed = 0;
    for (BackupFile file : list()) {
      if (file.createdAt().isBefore(cutoff)) {
        try {
          Files.deleteIfExists(directory().resolve(file.name()));
          removed++;
        } catch (IOException e) {
          LOG.warnf(e, "Could not remove the expired backup %s", file.name());
        }
      }
    }
    if (removed > 0) {
      LOG.infof("Removed %d backup(s) older than %d days", removed, days);
    }
    return removed;
  }

  /** Parses and sanity checks a backup without touching the database. */
  public Backup parse(byte[] content) {
    if (content == null || content.length == 0) {
      throw new BusinessRuleException("BACKUP_EMPTY", "The backup file is empty");
    }
    if (content.length > MAX_FILE_BYTES) {
      throw new BusinessRuleException(
          "BACKUP_TOO_LARGE", "The backup file is larger than " + (MAX_FILE_BYTES >> 20) + " MB");
    }
    Backup backup = xml.read(content);
    if (backup.formatVersion() > BackupModel.FORMAT_VERSION) {
      throw new BusinessRuleException(
          "BACKUP_VERSION_UNSUPPORTED",
          "This backup was written by a newer version of Small CRM (format "
              + backup.formatVersion()
              + ")");
    }
    requireUniqueIds(backup);
    return backup;
  }

  /**
   * Refuses a file whose records do not have distinct ids.
   *
   * <p>The restore rebuilds the links between records through maps keyed on the id in the file.
   * A hand-edited file with a repeated or missing id silently loses entries from those maps, and
   * the result is a restore that appears to succeed while attaching activity to the wrong
   * contact. Better to refuse the file and say why.
   */
  private static void requireUniqueIds(Backup backup) {
    checkIds("companies", backup.companies(), BackupModel.BackupCompany::id);
    checkIds("contacts", backup.contacts(), BackupModel.BackupContact::id);
    checkIds("deals", backup.deals(), BackupModel.BackupDeal::id);
    checkIds("interactions", backup.interactions(), BackupModel.BackupInteraction::id);
    checkIds("tasks", backup.tasks(), BackupModel.BackupTask::id);
    checkIds("appointments", backup.appointments(), BackupModel.BackupAppointment::id);
  }

  private static <T> void checkIds(
      String section, List<T> records, java.util.function.Function<T, Long> idOf) {
    Set<Long> seen = new HashSet<>();
    for (T record : nullToEmpty(records)) {
      Long id = idOf.apply(record);
      if (id == null) {
        throw new BusinessRuleException(
            "BACKUP_ID_MISSING", "A record in the " + section + " of this backup has no id");
      }
      if (!seen.add(id)) {
        throw new BusinessRuleException(
            "BACKUP_ID_DUPLICATE",
            "The id " + id + " appears more than once in the " + section + " of this backup");
      }
    }
  }

  /**
   * Replaces all business data with the contents of a backup.
   *
   * <p>The caller is expected to have written a safety copy first; {@link #restore(byte[])} does
   * that. Accounts and settings are left untouched.
   *
   * @return how many records were loaded
   */
  @Transactional
  public RestoreOutcome replaceAll(Backup backup) {
    deleteAllBusinessData();

    Map<String, AppUser> usersByName = new HashMap<>();
    for (AppUser user : AppUser.<AppUser>listAll()) {
      usersByName.put(user.username, user);
    }
    // Counted rather than merely logged: restoring onto a fresh installation leaves every
    // record ownerless, and a cheerful record count alone made that look like a clean restore.
    OwnerResolver owners = new OwnerResolver(usersByName);
    int skipped = 0;

    Map<Long, Company> companies = new HashMap<>();
    for (BackupCompany source : nullToEmpty(backup.companies())) {
      Company company = new Company();
      company.name = source.name();
      company.vatId = source.vatId();
      company.website = source.website();
      company.email = source.email();
      company.phone = source.phone();
      company.street = source.street();
      company.postalCode = source.postalCode();
      company.city = source.city();
      company.country = source.country();
      company.notes = source.notes();
      company.owner = owners.resolve(source.owner());
      applyTimestamps(company, source.createdAt(), source.updatedAt());
      company.persist();
      companies.put(source.id(), company);
    }

    Map<Long, Contact> contacts = new HashMap<>();
    for (BackupContact source : nullToEmpty(backup.contacts())) {
      Contact contact = new Contact();
      contact.firstName = source.firstName();
      contact.lastName = source.lastName();
      contact.email = source.email();
      contact.phone = source.phone();
      contact.mobile = source.mobile();
      contact.position = source.position();
      contact.company = companies.get(source.companyId());
      contact.notes = source.notes();
      contact.owner = owners.resolve(source.owner());
      if (source.tags() != null) {
        contact.tags.addAll(source.tags());
      }
      applyTimestamps(contact, source.createdAt(), source.updatedAt());
      contact.persist();
      contacts.put(source.id(), contact);
    }

    Map<Long, Deal> deals = new HashMap<>();
    for (BackupDeal source : nullToEmpty(backup.deals())) {
      Deal deal = new Deal();
      deal.title = source.title();
      deal.contact = contacts.get(source.contactId());
      deal.company = companies.get(source.companyId());
      deal.amount = source.amount();
      deal.currency = source.currency() == null ? "EUR" : source.currency();
      deal.stage = source.stage();
      deal.expectedCloseDate = source.expectedCloseDate();
      deal.notes = source.notes();
      deal.owner = owners.resolve(source.owner());
      applyTimestamps(deal, source.createdAt(), source.updatedAt());
      deal.persist();
      deals.put(source.id(), deal);
    }

    for (BackupInteraction source : nullToEmpty(backup.interactions())) {
      Contact contact = contacts.get(source.contactId());
      if (contact == null) {
        // An interaction cannot exist without its contact; a file that lost one is skipped
        // rather than failing the whole restore.
        LOG.warnf("Skipping interaction '%s': its contact is not part of the file",
            source.subject());
        skipped++;
        continue;
      }
      Interaction interaction = new Interaction();
      interaction.type = source.type();
      interaction.occurredAt = source.occurredAt();
      interaction.subject = source.subject();
      interaction.notes = source.notes();
      interaction.contact = contact;
      interaction.deal = deals.get(source.dealId());
      interaction.owner = owners.resolve(source.owner());
      applyTimestamps(interaction, source.createdAt(), source.updatedAt());
      interaction.persist();
    }

    for (BackupTask source : nullToEmpty(backup.tasks())) {
      CrmTask task = new CrmTask();
      task.title = source.title();
      task.description = source.description();
      task.dueDate = source.dueDate();
      task.done = source.done();
      task.completedAt = source.completedAt();
      task.priority = source.priority();
      task.contact = contacts.get(source.contactId());
      task.deal = deals.get(source.dealId());
      task.owner = owners.resolve(source.owner());
      applyTimestamps(task, source.createdAt(), source.updatedAt());
      task.persist();
    }

    for (BackupAppointment source : nullToEmpty(backup.appointments())) {
      Appointment appointment = new Appointment();
      appointment.title = source.title();
      appointment.startsAt = source.startsAt();
      appointment.endsAt = source.endsAt();
      appointment.timeZone = source.timeZone() == null ? "UTC" : source.timeZone();
      appointment.location = source.location();
      appointment.notes = source.notes();
      appointment.contact = contacts.get(source.contactId());
      appointment.deal = deals.get(source.dealId());
      appointment.externalCalendarId = source.externalCalendarId();
      appointment.externalEventId = source.externalEventId();
      appointment.externalEtag = source.externalEtag();
      appointment.lastSyncedAt = source.lastSyncedAt();
      appointment.owner = owners.resolve(source.owner());
      applyTimestamps(appointment, source.createdAt(), source.updatedAt());
      appointment.persist();
    }

    return new RestoreOutcome(
        backup.recordCount() - skipped, skipped, owners.unresolved());
  }

  /** Maps an exported owner name onto a local account, counting the ones that do not exist. */
  private static final class OwnerResolver {
    private final Map<String, AppUser> byName;
    private int unresolved;

    OwnerResolver(Map<String, AppUser> byName) {
      this.byName = byName;
    }

    AppUser resolve(String username) {
      if (username == null) {
        return null;
      }
      AppUser user = byName.get(username);
      if (user == null) {
        unresolved++;
      }
      return user;
    }

    int unresolved() {
      return unresolved;
    }
  }

  /**
   * Result of a restore, reported back to the interface.
   *
   * @param skipped records the file contained but that could not be loaded, for instance an
   *     interaction whose contact was missing; previously only a log line
   * @param unresolvedOwners records whose owner name matches no account here, which is expected
   *     when restoring onto a fresh installation and worth saying out loud
   */
  public record RestoreResult(
      int recordCount, String safetyCopy, int skipped, int unresolvedOwners) {}

  /** What {@link #replaceAll} loaded, before the file names are attached. */
  public record RestoreOutcome(int recordCount, int skipped, int unresolvedOwners) {}

  /** Takes a safety copy of the current data, then replaces it with the given file. */
  public RestoreResult restore(byte[] content) {
    Backup backup = parse(content);
    fileLock.lock();
    try {
      // Safety copy and replacement inside one guarded section: otherwise a record saved in
      // between is wiped by the restore and appears in neither the safety copy nor any
      // automatic backup, because the coalescing window means it was probably not written yet.
      String safetyCopy = writeLocked(true).name();
      RestoreOutcome outcome = replaceAll(backup);
      LOG.infof(
          "Restored %d records; previous data kept in %s", outcome.recordCount(), safetyCopy);
      return new RestoreResult(
          outcome.recordCount(), safetyCopy, outcome.skipped(), outcome.unresolvedOwners());
    } finally {
      fileLock.unlock();
    }
  }

  /** Takes a safety copy, then restores the named file from the backup folder. */
  public RestoreResult restoreFromFolder(String fileName) {
    Path file = resolve(fileName);
    try {
      // Checked before reading: the upload path is capped by the HTTP layer, but a file placed
      // in the folder by hand is not, and reading a multi-gigabyte one would exhaust the heap
      // before the limit inside parse() is ever consulted.
      long size = Files.size(file);
      if (size > MAX_FILE_BYTES) {
        throw new BusinessRuleException(
            "BACKUP_TOO_LARGE",
            "The backup file is larger than " + (MAX_FILE_BYTES >> 20) + " MB");
      }
      return restore(Files.readAllBytes(file));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the backup " + fileName, e);
    }
  }

  private void deleteAllBusinessData() {
    Interaction.deleteAll();
    CrmTask.deleteAll();
    Appointment.deleteAll();
    Deal.deleteAll();
    // Contacts own a tag collection table, so they go through the entity lifecycle. The flush
    // makes those removals reach the database before the bulk delete of companies runs.
    Contact.<Contact>listAll().forEach(Contact::delete);
    Contact.getEntityManager().flush();
    Company.deleteAll();
  }

  // --- entity to file --------------------------------------------------------------

  private static BackupCompany toBackup(Company company) {
    return new BackupCompany(
        company.id,
        company.name,
        company.vatId,
        company.website,
        company.email,
        company.phone,
        company.street,
        company.postalCode,
        company.city,
        company.country,
        company.notes,
        ownerName(company.owner),
        company.createdAt,
        company.updatedAt);
  }

  private static BackupContact toBackup(Contact contact) {
    return new BackupContact(
        contact.id,
        contact.firstName,
        contact.lastName,
        contact.email,
        contact.phone,
        contact.mobile,
        contact.position,
        contact.company == null ? null : contact.company.id,
        List.copyOf(contact.tags),
        contact.notes,
        ownerName(contact.owner),
        contact.createdAt,
        contact.updatedAt);
  }

  private static BackupDeal toBackup(Deal deal) {
    return new BackupDeal(
        deal.id,
        deal.title,
        deal.contact == null ? null : deal.contact.id,
        deal.company == null ? null : deal.company.id,
        deal.amount,
        deal.currency,
        deal.stage,
        deal.expectedCloseDate,
        deal.notes,
        ownerName(deal.owner),
        deal.createdAt,
        deal.updatedAt);
  }

  private static BackupInteraction toBackup(Interaction interaction) {
    return new BackupInteraction(
        interaction.id,
        interaction.type,
        interaction.occurredAt,
        interaction.subject,
        interaction.notes,
        interaction.contact == null ? null : interaction.contact.id,
        interaction.deal == null ? null : interaction.deal.id,
        ownerName(interaction.owner),
        interaction.createdAt,
        interaction.updatedAt);
  }

  private static BackupTask toBackup(CrmTask task) {
    return new BackupTask(
        task.id,
        task.title,
        task.description,
        task.dueDate,
        task.done,
        task.completedAt,
        task.priority,
        task.contact == null ? null : task.contact.id,
        task.deal == null ? null : task.deal.id,
        ownerName(task.owner),
        task.createdAt,
        task.updatedAt);
  }

  private static BackupAppointment toBackup(Appointment appointment) {
    return new BackupAppointment(
        appointment.id,
        appointment.title,
        appointment.startsAt,
        appointment.endsAt,
        appointment.timeZone,
        appointment.location,
        appointment.notes,
        appointment.contact == null ? null : appointment.contact.id,
        appointment.deal == null ? null : appointment.deal.id,
        appointment.externalCalendarId,
        appointment.externalEventId,
        appointment.externalEtag,
        appointment.lastSyncedAt,
        ownerName(appointment.owner),
        appointment.createdAt,
        appointment.updatedAt);
  }

  /** Accounts are not part of a backup, so the owner travels as a plain user name. */
  private static String ownerName(AppUser owner) {
    return owner == null ? null : owner.username;
  }

  /**
   * Restores the timestamps a record was exported with.
   *
   * <p>"When was this contact created, when was it last touched" is business information in a
   * CRM. Leaving these to the lifecycle callback would rewrite the entire history to the moment
   * of the restore, and the next automatic backup would then make that permanent.
   */
  private static void applyTimestamps(BaseEntity entity, Instant createdAt, Instant updatedAt) {
    entity.createdAt = createdAt;
    entity.updatedAt = updatedAt != null ? updatedAt : createdAt;
  }

  private static <T> List<T> nullToEmpty(List<T> list) {
    return list == null ? List.of() : new ArrayList<>(list);
  }

  /**
   * Picks a file name that is not taken yet.
   *
   * <p>Two backups inside the same second are possible when a restore is triggered right after a
   * change, and the second one must not overwrite the first.
   */
  private static Path uniquePath(Path dir, boolean beforeRestore, Instant at) {
    String base =
        beforeRestore ? BackupFiles.beforeRestoreName(at) : BackupFiles.automaticName(at);
    Path candidate = dir.resolve(base);
    int suffix = 1;
    while (Files.exists(candidate)) {
      String name =
          base.substring(0, base.length() - BackupFiles.EXTENSION.length())
              + "-"
              + suffix++
              + BackupFiles.EXTENSION;
      candidate = dir.resolve(name);
    }
    return candidate;
  }

  private static BackupFile describe(Path path) {
    String name = path.getFileName().toString();
    try {
      return new BackupFile(
          name,
          Files.size(path),
          Files.getLastModifiedTime(path).toInstant(),
          name.startsWith(BackupFiles.BEFORE_RESTORE_PREFIX));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the backup " + name, e);
    }
  }
}
