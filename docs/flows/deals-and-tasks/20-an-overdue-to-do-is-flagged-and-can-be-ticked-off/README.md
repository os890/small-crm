# an overdue to-do is flagged and can be ticked off

Recorded from `e2e/tests/deals-and-tasks.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 13 application chains below, in the order the application handled them, one
block per request.

16 distinct call chain(s), out of 110 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215336562_20260804-215337277.mmd`](AuthResource_changePassword_20260804-215336562_20260804-215337277.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215335895_20260804-215336166.mmd`](AuthResource_login_20260804-215335895_20260804-215336166.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215336180_20260804-215336181.mmd`](AuthResource_me_20260804-215336180_20260804-215336181.mmd) | 2× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215337947_20260804-215337947.mmd`](AutoBackupTrigger_dataChanged_20260804-215337947_20260804-215337947.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215334889_20260804-215334894.mmd`](BackupService_applyRetention_20260804-215334889_20260804-215334894.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215334533_20260804-215334879.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215334533_20260804-215334879.mmd) | 1× |
| `CrmTaskResource.create` | [`CrmTaskResource_create_20260804-215337943_20260804-215337946.mmd`](CrmTaskResource_create_20260804-215337943_20260804-215337946.mmd) | 1× |
| `CrmTaskResource.list` | [`CrmTaskResource_list_20260804-215337798_20260804-215337802.mmd`](CrmTaskResource_list_20260804-215337798_20260804-215337802.mmd) | 4× |
| `CrmTaskResource.setDone` | [`CrmTaskResource_setDone_20260804-215338005_20260804-215338007.mmd`](CrmTaskResource_setDone_20260804-215338005_20260804-215338007.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215336178_20260804-215336180.mmd`](CurrentUser_find_20260804-215336178_20260804-215336180.mmd) | 10× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215337295_20260804-215337371.mmd`](DashboardResource_summary_20260804-215337295_20260804-215337371.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215335119_20260804-215335120.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215335119_20260804-215335120.mmd) | 33× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215336176_20260804-215336177.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215336176_20260804-215336177.mmd) | 25× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215335118_20260804-215335118.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215335118_20260804-215335118.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215335554_20260804-215335555.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215335554_20260804-215335555.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215336172_20260804-215336175.mmd`](SessionService_authenticate_20260804-215336172_20260804-215336175.mmd) | 25× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
