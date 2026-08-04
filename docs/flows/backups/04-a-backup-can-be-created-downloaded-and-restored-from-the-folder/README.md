# a backup can be created, downloaded and restored from the folder

Recorded from `e2e/tests/backups.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd)**
— the 29 application chains below, in the order the application handled them, one
block per request.

Not rendered to PNG: 29 requests make an image thousands of pixels tall. The
`.mmd` above renders in any Mermaid viewer that can scroll.

20 distinct call chain(s), out of 193 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215118614_20260804-215119328.mmd`](AuthResource_changePassword_20260804-215118614_20260804-215119328.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215117965_20260804-215118238.mmd`](AuthResource_login_20260804-215117965_20260804-215118238.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215118252_20260804-215118253.mmd`](AuthResource_me_20260804-215118252_20260804-215118253.mmd) | 6× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215119996_20260804-215119997.mmd`](AutoBackupTrigger_dataChanged_20260804-215119996_20260804-215119997.mmd) | 2× |
| `BackupResource.create` | [`BackupResource_create_20260804-215120124_20260804-215120163.mmd`](BackupResource_create_20260804-215120124_20260804-215120163.mmd) | 1× |
| `BackupResource.download` | [`BackupResource_download_20260804-215120220_20260804-215120220.mmd`](BackupResource_download_20260804-215120220_20260804-215120220.mmd) | 1× |
| `BackupResource.list` | [`BackupResource_list_20260804-215120081_20260804-215120082.mmd`](BackupResource_list_20260804-215120081_20260804-215120082.mmd) | 4× |
| `BackupResource.restore` | [`BackupResource_restore_20260804-215120542_20260804-215120580.mmd`](BackupResource_restore_20260804-215120542_20260804-215120580.mmd) | 1× |
| `BackupResource.settings` | [`BackupResource_settings_20260804-215120081_20260804-215120082.mmd`](BackupResource_settings_20260804-215120081_20260804-215120082.mmd) | 4× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215116671_20260804-215116676.mmd`](BackupService_applyRetention_20260804-215116671_20260804-215116676.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215116311_20260804-215116661.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215116311_20260804-215116661.mmd) | 1× |
| `CompanyResource.create` | [`CompanyResource_create_20260804-215119993_20260804-215119996.mmd`](CompanyResource_create_20260804-215119993_20260804-215119996.mmd) | 2× |
| `CompanyResource.list` | [`CompanyResource_list_20260804-215119855_20260804-215119857.mmd`](CompanyResource_list_20260804-215119855_20260804-215119857.mmd) | 5× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215118251_20260804-215118252.mmd`](CurrentUser_find_20260804-215118251_20260804-215118252.mmd) | 26× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215119347_20260804-215119418.mmd`](DashboardResource_summary_20260804-215119347_20260804-215119418.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215117175_20260804-215117176.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215117175_20260804-215117176.mmd) | 50× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215118249_20260804-215118249.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215118249_20260804-215118249.mmd) | 42× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215117174_20260804-215117174.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215117174_20260804-215117174.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215117636_20260804-215117636.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215117636_20260804-215117636.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215118244_20260804-215118248.mmd`](SessionService_authenticate_20260804-215118244_20260804-215118248.mmd) | 42× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
