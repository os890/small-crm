# deleting a contact asks first and then removes it

Recorded from `e2e/tests/contacts.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 12 application chains below, in the order the application handled them, one
block per request.

16 distinct call chain(s), out of 99 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215319763_20260804-215320475.mmd`](AuthResource_changePassword_20260804-215319763_20260804-215320475.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215319113_20260804-215319386.mmd`](AuthResource_login_20260804-215319113_20260804-215319386.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215319399_20260804-215319400.mmd`](AuthResource_me_20260804-215319399_20260804-215319400.mmd) | 2× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215321164_20260804-215321165.mmd`](AutoBackupTrigger_dataChanged_20260804-215321164_20260804-215321165.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215317805_20260804-215317810.mmd`](BackupService_applyRetention_20260804-215317805_20260804-215317810.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215317448_20260804-215317795.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215317448_20260804-215317795.mmd) | 1× |
| `ContactResource.create` | [`ContactResource_create_20260804-215321157_20260804-215321164.mmd`](ContactResource_create_20260804-215321157_20260804-215321164.mmd) | 1× |
| `ContactResource.delete` | [`ContactResource_delete_20260804-215321324_20260804-215321339.mmd`](ContactResource_delete_20260804-215321324_20260804-215321339.mmd) | 1× |
| `ContactResource.list` | [`ContactResource_list_20260804-215321013_20260804-215321018.mmd`](ContactResource_list_20260804-215321013_20260804-215321018.mmd) | 3× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215319398_20260804-215319399.mmd`](CurrentUser_find_20260804-215319398_20260804-215319399.mmd) | 9× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215320494_20260804-215320563.mmd`](DashboardResource_summary_20260804-215320494_20260804-215320563.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215318321_20260804-215318324.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215318321_20260804-215318324.mmd) | 30× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215319396_20260804-215319396.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215319396_20260804-215319396.mmd) | 22× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215318320_20260804-215318320.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215318320_20260804-215318320.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215318774_20260804-215318774.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215318774_20260804-215318774.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215319391_20260804-215319395.mmd`](SessionService_authenticate_20260804-215319391_20260804-215319395.mmd) | 22× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
