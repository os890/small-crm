# a file that is not a backup is refused without losing any data

Recorded from `e2e/tests/backups.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 16 application chains below, in the order the application handled them, one
block per request.

19 distinct call chain(s), out of 128 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `ApiExceptionMappers.handleBusinessRule` | [`ApiExceptionMappers_handleBusinessRule_20260804-215217303_20260804-215217303.mmd`](ApiExceptionMappers_handleBusinessRule_20260804-215217303_20260804-215217303.mmd) | 1× |
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215215715_20260804-215216429.mmd`](AuthResource_changePassword_20260804-215215715_20260804-215216429.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215215048_20260804-215215320.mmd`](AuthResource_login_20260804-215215048_20260804-215215320.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215215334_20260804-215215334.mmd`](AuthResource_me_20260804-215215334_20260804-215215334.mmd) | 4× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215217095_20260804-215217095.mmd`](AutoBackupTrigger_dataChanged_20260804-215217095_20260804-215217095.mmd) | 1× |
| `BackupResource.list` | [`BackupResource_list_20260804-215217185_20260804-215217185.mmd`](BackupResource_list_20260804-215217185_20260804-215217185.mmd) | 1× |
| `BackupResource.restoreUpload` | [`BackupResource_restoreUpload_20260804-215217292_20260804-215217302.mmd`](BackupResource_restoreUpload_20260804-215217292_20260804-215217302.mmd) | 1× |
| `BackupResource.settings` | [`BackupResource_settings_20260804-215217185_20260804-215217185.mmd`](BackupResource_settings_20260804-215217185_20260804-215217185.mmd) | 1× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215213746_20260804-215213751.mmd`](BackupService_applyRetention_20260804-215213746_20260804-215213751.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215213386_20260804-215213735.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215213386_20260804-215213735.mmd) | 1× |
| `CompanyResource.create` | [`CompanyResource_create_20260804-215217092_20260804-215217095.mmd`](CompanyResource_create_20260804-215217092_20260804-215217095.mmd) | 1× |
| `CompanyResource.list` | [`CompanyResource_list_20260804-215216952_20260804-215216954.mmd`](CompanyResource_list_20260804-215216952_20260804-215216954.mmd) | 3× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215215332_20260804-215215333.mmd`](CurrentUser_find_20260804-215215332_20260804-215215333.mmd) | 13× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215216448_20260804-215216522.mmd`](DashboardResource_summary_20260804-215216448_20260804-215216522.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215214263_20260804-215214263.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215214263_20260804-215214263.mmd) | 37× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215215330_20260804-215215331.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215215330_20260804-215215331.mmd) | 29× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215214262_20260804-215214262.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215214262_20260804-215214262.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215214707_20260804-215214707.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215214707_20260804-215214707.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215215326_20260804-215215329.mmd`](SessionService_authenticate_20260804-215215326_20260804-215215329.mmd) | 29× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
