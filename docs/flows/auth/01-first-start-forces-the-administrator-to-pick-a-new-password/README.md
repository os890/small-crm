# first start forces the administrator to pick a new password

Recorded from `e2e/tests/auth.setup.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 4 application chains below, in the order the application handled them, one
block per request.

12 distinct call chain(s), out of 40 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215102474_20260804-215103188.mmd`](AuthResource_changePassword_20260804-215102474_20260804-215103188.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215101815_20260804-215102087.mmd`](AuthResource_login_20260804-215101815_20260804-215102087.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215102101_20260804-215102102.mmd`](AuthResource_me_20260804-215102101_20260804-215102102.mmd) | 1× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215100540_20260804-215100545.mmd`](BackupService_applyRetention_20260804-215100540_20260804-215100545.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215100185_20260804-215100530.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215100185_20260804-215100530.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215102099_20260804-215102101.mmd`](CurrentUser_find_20260804-215102099_20260804-215102101.mmd) | 3× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215103208_20260804-215103301.mmd`](DashboardResource_summary_20260804-215103208_20260804-215103301.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215101041_20260804-215101042.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215101041_20260804-215101042.mmd) | 15× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215102098_20260804-215102098.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215102098_20260804-215102098.mmd) | 7× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215101040_20260804-215101040.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215101040_20260804-215101040.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215101474_20260804-215101474.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215101474_20260804-215101474.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215102093_20260804-215102097.mmd`](SessionService_authenticate_20260804-215102093_20260804-215102097.mmd) | 7× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
