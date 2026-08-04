# an unwanted restore can be undone with the before-restore file

Recorded from `e2e/tests/backups.spec.ts`, passed, run with its whole spec in front of it.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd)**
— the 78 application chains below, in the order the application handled them, one
block per request.

Not rendered to PNG: 78 requests make an image thousands of pixels tall. The
`.mmd` above renders in any Mermaid viewer that can scroll.

25 distinct call chain(s), out of 571 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `ApiExceptionMappers.handleBusinessRule` | [`ApiExceptionMappers_handleBusinessRule_20260804-215204012_20260804-215204012.mmd`](ApiExceptionMappers_handleBusinessRule_20260804-215204012_20260804-215204012.mmd) | 1× |
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215200432_20260804-215201148.mmd`](AuthResource_changePassword_20260804-215200432_20260804-215201148.mmd) | 2× |
| `AuthResource.login` | [`AuthResource_login_20260804-215159779_20260804-215200052.mmd`](AuthResource_login_20260804-215159779_20260804-215200052.mmd) | 2× |
| `AuthResource.me` | [`AuthResource_me_20260804-215200066_20260804-215200066.mmd`](AuthResource_me_20260804-215200066_20260804-215200066.mmd) | 19× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215201812_20260804-215201812.mmd`](AutoBackupTrigger_dataChanged_20260804-215201812_20260804-215201812.mmd) | 4× |
| `BackupResource.create` | [`BackupResource_create_20260804-215201940_20260804-215201979.mmd`](BackupResource_create_20260804-215201940_20260804-215201979.mmd) | 1× |
| `BackupResource.download` | [`BackupResource_download_20260804-215202035_20260804-215202036.mmd`](BackupResource_download_20260804-215202035_20260804-215202036.mmd) | 1× |
| `BackupResource.list` | [`BackupResource_list_20260804-215201898_20260804-215201898.mmd`](BackupResource_list_20260804-215201898_20260804-215201898.mmd) | 10× |
| `BackupResource.restore` | [`BackupResource_restore_20260804-215202357_20260804-215202393.mmd`](BackupResource_restore_20260804-215202357_20260804-215202393.mmd) | 3× |
| `BackupResource.restoreUpload` | [`BackupResource_restoreUpload_20260804-215204010_20260804-215204011.mmd`](BackupResource_restoreUpload_20260804-215204010_20260804-215204011.mmd) | 1× |
| `BackupResource.settings` | [`BackupResource_settings_20260804-215201898_20260804-215201898.mmd`](BackupResource_settings_20260804-215201898_20260804-215201898.mmd) | 10× |
| `BackupResource.updateSettings` | [`BackupResource_updateSettings_20260804-215203442_20260804-215203446.mmd`](BackupResource_updateSettings_20260804-215203442_20260804-215203446.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215158490_20260804-215158495.mmd`](BackupService_applyRetention_20260804-215158490_20260804-215158495.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215158130_20260804-215158480.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215158130_20260804-215158480.mmd) | 1× |
| `CompanyResource.create` | [`CompanyResource_create_20260804-215201809_20260804-215201812.mmd`](CompanyResource_create_20260804-215201809_20260804-215201812.mmd) | 4× |
| `CompanyResource.list` | [`CompanyResource_list_20260804-215201660_20260804-215201662.mmd`](CompanyResource_list_20260804-215201660_20260804-215201662.mmd) | 12× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215200064_20260804-215200065.mmd`](CurrentUser_find_20260804-215200064_20260804-215200065.mmd) | 71× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215201167_20260804-215201236.mmd`](DashboardResource_summary_20260804-215201167_20260804-215201236.mmd) | 3× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215158998_20260804-215158999.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215158998_20260804-215158999.mmd) | 149× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215200062_20260804-215200062.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215200062_20260804-215200062.mmd) | 134× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215158998_20260804-215158998.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215158998_20260804-215158998.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215159435_20260804-215159435.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215159435_20260804-215159435.mmd) | 2× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215200058_20260804-215200061.mmd`](SessionService_authenticate_20260804-215200058_20260804-215200061.mmd) | 134× |
| `UserResource.create` | [`UserResource_create_20260804-215204359_20260804-215204596.mmd`](UserResource_create_20260804-215204359_20260804-215204596.mmd) | 1× |
| `UserResource.list` | [`UserResource_list_20260804-215204241_20260804-215204242.mmd`](UserResource_list_20260804-215204241_20260804-215204242.mmd) | 2× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
