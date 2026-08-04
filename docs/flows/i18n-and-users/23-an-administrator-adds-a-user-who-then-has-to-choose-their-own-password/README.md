# an administrator adds a user who then has to choose their own password

Recorded from `e2e/tests/i18n-and-users.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 14 application chains below, in the order the application handled them, one
block per request.

14 distinct call chain(s), out of 133 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215414111_20260804-215414826.mmd`](AuthResource_changePassword_20260804-215414111_20260804-215414826.mmd) | 2× |
| `AuthResource.login` | [`AuthResource_login_20260804-215413443_20260804-215413713.mmd`](AuthResource_login_20260804-215413443_20260804-215413713.mmd) | 2× |
| `AuthResource.me` | [`AuthResource_me_20260804-215413726_20260804-215413727.mmd`](AuthResource_me_20260804-215413726_20260804-215413727.mmd) | 4× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215412450_20260804-215412454.mmd`](BackupService_applyRetention_20260804-215412450_20260804-215412454.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215412094_20260804-215412439.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215412094_20260804-215412439.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215413724_20260804-215413726.mmd`](CurrentUser_find_20260804-215413724_20260804-215413726.mmd) | 12× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215414844_20260804-215414913.mmd`](DashboardResource_summary_20260804-215414844_20260804-215414913.mmd) | 3× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215412670_20260804-215412671.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215412670_20260804-215412671.mmd) | 44× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215413723_20260804-215413723.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215413723_20260804-215413723.mmd) | 29× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215412669_20260804-215412669.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215412669_20260804-215412669.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215413103_20260804-215413104.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215413103_20260804-215413104.mmd) | 2× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215413718_20260804-215413722.mmd`](SessionService_authenticate_20260804-215413718_20260804-215413722.mmd) | 29× |
| `UserResource.create` | [`UserResource_create_20260804-215415488_20260804-215415724.mmd`](UserResource_create_20260804-215415488_20260804-215415724.mmd) | 1× |
| `UserResource.list` | [`UserResource_list_20260804-215415346_20260804-215415347.mmd`](UserResource_list_20260804-215415346_20260804-215415347.mmd) | 2× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
