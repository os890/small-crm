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

package org.os890.smallcrm.api;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.os890.smallcrm.api.dto.BackupFileDto;
import org.os890.smallcrm.api.dto.BackupSettingsDto;
import org.os890.smallcrm.api.error.BusinessRuleException;
import org.os890.smallcrm.backup.BackupService;
import org.os890.smallcrm.backup.BackupSettingsService;
import org.os890.smallcrm.domain.AppUser;

/**
 * Backup and restore, reserved for administrators.
 *
 * <p>Every endpoint here either exposes the whole customer database or replaces it, so the role
 * check sits on the class and there is no read-only subset for ordinary users.
 */
@Path("/backups")
@RolesAllowed(AppUser.ROLE_ADMIN)
@Produces(MediaType.APPLICATION_JSON)
public class BackupResource {

  @Inject BackupService backupService;
  @Inject BackupSettingsService settingsService;

  /** Every backup in the folder, newest first. */
  @GET
  public List<BackupFileDto> list() {
    return backupService.list().stream().map(BackupFileDto::from).toList();
  }

  /** Writes a backup of the current data straight away. */
  @POST
  public Response create() {
    BackupFileDto created = BackupFileDto.from(backupService.write(false));
    return Response.status(Response.Status.CREATED).entity(created).build();
  }

  /** Downloads a backup so it can be kept somewhere other than this machine. */
  @GET
  @Path("/{name}/content")
  @Produces(MediaType.APPLICATION_XML)
  public Response download(@PathParam("name") String name) {
    java.nio.file.Path file = backupService.resolve(name);
    try {
      return Response.ok(Files.readAllBytes(file))
          .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
          .build();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the backup " + name, e);
    }
  }

  /**
   * Replaces all data with a backup from the folder.
   *
   * <p>A safety copy of the current data is written first and named in the response, so an
   * unwanted restore can be undone by restoring that file in turn.
   */
  @POST
  @Path("/{name}/restore")
  public Map<String, Object> restore(@PathParam("name") String name) {
    BackupService.RestoreResult result = backupService.restoreFromFolder(name);
    return Map.of("recordCount", result.recordCount(), "safetyCopy", result.safetyCopy());
  }

  /** Replaces all data with an uploaded backup file. */
  @POST
  @Path("/restore-upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Map<String, Object> restoreUpload(@RestForm("file") FileUpload upload) {
    if (upload == null) {
      throw new BusinessRuleException("BACKUP_EMPTY", "No file was uploaded");
    }
    byte[] content;
    try {
      content = Files.readAllBytes(upload.uploadedFile());
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the uploaded backup", e);
    }
    BackupService.RestoreResult result = backupService.restore(content);
    return Map.of("recordCount", result.recordCount(), "safetyCopy", result.safetyCopy());
  }

  @GET
  @Path("/settings")
  public BackupSettingsDto settings() {
    return currentSettings();
  }

  /** Changes how long backups are kept, and applies the new period immediately. */
  @PUT
  @Path("/settings")
  @Consumes(MediaType.APPLICATION_JSON)
  public BackupSettingsDto updateSettings(@Valid BackupSettingsDto input) {
    settingsService.updateRetentionDays(input.retentionDays());
    backupService.applyRetention();
    return currentSettings();
  }

  private BackupSettingsDto currentSettings() {
    return new BackupSettingsDto(
        settingsService.retentionDays(),
        BackupSettingsService.MIN_RETENTION_DAYS,
        BackupSettingsService.MAX_RETENTION_DAYS,
        backupService.directory().toString());
  }
}
