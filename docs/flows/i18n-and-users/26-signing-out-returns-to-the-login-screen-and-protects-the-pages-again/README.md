# signing out returns to the login screen and protects the pages again

Recorded from `e2e/tests/i18n-and-users.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 7 application chains below, in the order the application handled them, one
block per request.

13 distinct call chain(s), out of 79 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215432411_20260804-215433125.mmd`](AuthResource_changePassword_20260804-215432411_20260804-215433125.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215431742_20260804-215432015.mmd`](AuthResource_login_20260804-215431742_20260804-215432015.mmd) | 1× |
| `AuthResource.logout` | [`AuthResource_logout_20260804-215433720_20260804-215433723.mmd`](AuthResource_logout_20260804-215433720_20260804-215433723.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215432029_20260804-215432029.mmd`](AuthResource_me_20260804-215432029_20260804-215432029.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215430747_20260804-215430752.mmd`](BackupService_applyRetention_20260804-215430747_20260804-215430752.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215430392_20260804-215430737.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215430392_20260804-215430737.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215432027_20260804-215432028.mmd`](CurrentUser_find_20260804-215432027_20260804-215432028.mmd) | 6× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215433143_20260804-215433225.mmd`](DashboardResource_summary_20260804-215433143_20260804-215433225.mmd) | 2× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215430967_20260804-215430968.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215430967_20260804-215430968.mmd) | 29× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215432025_20260804-215432025.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215432025_20260804-215432025.mmd) | 16× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215430967_20260804-215430967.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215430967_20260804-215430967.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215431405_20260804-215431405.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215431405_20260804-215431405.mmd) | 2× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215432020_20260804-215432024.mmd`](SessionService_authenticate_20260804-215432020_20260804-215432024.mmd) | 16× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
