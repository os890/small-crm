# a plain user cannot reach the backup screen

Recorded from `e2e/tests/backups.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 14 application chains below, in the order the application handled them, one
block per request.

14 distinct call chain(s), out of 133 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215221682_20260804-215222395.mmd`](AuthResource_changePassword_20260804-215221682_20260804-215222395.mmd) | 2× |
| `AuthResource.login` | [`AuthResource_login_20260804-215221013_20260804-215221286.mmd`](AuthResource_login_20260804-215221013_20260804-215221286.mmd) | 2× |
| `AuthResource.me` | [`AuthResource_me_20260804-215221299_20260804-215221300.mmd`](AuthResource_me_20260804-215221299_20260804-215221300.mmd) | 4× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215219730_20260804-215219735.mmd`](BackupService_applyRetention_20260804-215219730_20260804-215219735.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215219372_20260804-215219718.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215219372_20260804-215219718.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215221298_20260804-215221299.mmd`](CurrentUser_find_20260804-215221298_20260804-215221299.mmd) | 12× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215222414_20260804-215222481.mmd`](DashboardResource_summary_20260804-215222414_20260804-215222481.mmd) | 3× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215220243_20260804-215220244.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215220243_20260804-215220244.mmd) | 44× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215221296_20260804-215221296.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215221296_20260804-215221296.mmd) | 29× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215220242_20260804-215220242.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215220242_20260804-215220242.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215220683_20260804-215220683.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215220683_20260804-215220683.mmd) | 2× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215221291_20260804-215221295.mmd`](SessionService_authenticate_20260804-215221291_20260804-215221295.mmd) | 29× |
| `UserResource.create` | [`UserResource_create_20260804-215223057_20260804-215223292.mmd`](UserResource_create_20260804-215223057_20260804-215223292.mmd) | 1× |
| `UserResource.list` | [`UserResource_list_20260804-215222913_20260804-215222914.mmd`](UserResource_list_20260804-215222913_20260804-215222914.mmd) | 2× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
