# a deal moves through the pipeline and disappears once it is won

Recorded from `e2e/tests/deals-and-tasks.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 16 application chains below, in the order the application handled them, one
block per request.

16 distinct call chain(s), out of 118 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215325328_20260804-215326043.mmd`](AuthResource_changePassword_20260804-215325328_20260804-215326043.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215324661_20260804-215324933.mmd`](AuthResource_login_20260804-215324661_20260804-215324933.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215324948_20260804-215324949.mmd`](AuthResource_me_20260804-215324948_20260804-215324949.mmd) | 2× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215326714_20260804-215326714.mmd`](AutoBackupTrigger_dataChanged_20260804-215326714_20260804-215326714.mmd) | 3× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215323668_20260804-215323672.mmd`](BackupService_applyRetention_20260804-215323668_20260804-215323672.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215323311_20260804-215323657.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215323311_20260804-215323657.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215324946_20260804-215324947.mmd`](CurrentUser_find_20260804-215324946_20260804-215324947.mmd) | 12× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215326062_20260804-215326129.mmd`](DashboardResource_summary_20260804-215326062_20260804-215326129.mmd) | 1× |
| `DealResource.changeStage` | [`DealResource_changeStage_20260804-215326744_20260804-215326745.mmd`](DealResource_changeStage_20260804-215326744_20260804-215326745.mmd) | 2× |
| `DealResource.create` | [`DealResource_create_20260804-215326709_20260804-215326713.mmd`](DealResource_create_20260804-215326709_20260804-215326713.mmd) | 1× |
| `DealResource.list` | [`DealResource_list_20260804-215326571_20260804-215326581.mmd`](DealResource_list_20260804-215326571_20260804-215326581.mmd) | 5× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215323878_20260804-215323878.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215323878_20260804-215323878.mmd) | 34× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215324944_20260804-215324944.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215324944_20260804-215324944.mmd) | 26× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215323877_20260804-215323877.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215323877_20260804-215323877.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215324321_20260804-215324321.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215324321_20260804-215324321.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215324939_20260804-215324943.mmd`](SessionService_authenticate_20260804-215324939_20260804-215324943.mmd) | 26× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
