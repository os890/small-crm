# the retention period can be changed from the screen

Recorded from `e2e/tests/backups.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 14 application chains below, in the order the application handled them, one
block per request.

15 distinct call chain(s), out of 120 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215209965_20260804-215210682.mmd`](AuthResource_changePassword_20260804-215209965_20260804-215210682.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215209297_20260804-215209568.mmd`](AuthResource_login_20260804-215209297_20260804-215209568.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215209582_20260804-215209583.mmd`](AuthResource_me_20260804-215209582_20260804-215209583.mmd) | 3× |
| `BackupResource.list` | [`BackupResource_list_20260804-215211192_20260804-215211193.mmd`](BackupResource_list_20260804-215211192_20260804-215211193.mmd) | 3× |
| `BackupResource.settings` | [`BackupResource_settings_20260804-215211192_20260804-215211193.mmd`](BackupResource_settings_20260804-215211192_20260804-215211193.mmd) | 3× |
| `BackupResource.updateSettings` | [`BackupResource_updateSettings_20260804-215211278_20260804-215211281.mmd`](BackupResource_updateSettings_20260804-215211278_20260804-215211281.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215208310_20260804-215208315.mmd`](BackupService_applyRetention_20260804-215208310_20260804-215208315.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215207951_20260804-215208299.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215207951_20260804-215208299.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215209580_20260804-215209582.mmd`](CurrentUser_find_20260804-215209580_20260804-215209582.mmd) | 13× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215210702_20260804-215210771.mmd`](DashboardResource_summary_20260804-215210702_20260804-215210771.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215208511_20260804-215208512.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215208511_20260804-215208512.mmd) | 35× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215209578_20260804-215209579.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215209578_20260804-215209579.mmd) | 27× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215208510_20260804-215208510.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215208510_20260804-215208510.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215208964_20260804-215208964.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215208964_20260804-215208964.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215209574_20260804-215209577.mmd`](SessionService_authenticate_20260804-215209574_20260804-215209577.mmd) | 27× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
