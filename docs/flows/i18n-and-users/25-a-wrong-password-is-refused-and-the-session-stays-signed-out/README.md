# a wrong password is refused and the session stays signed out

Recorded from `e2e/tests/i18n-and-users.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 5 application chains below, in the order the application handled them, one
block per request.

13 distinct call chain(s), out of 49 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215426861_20260804-215427574.mmd`](AuthResource_changePassword_20260804-215426861_20260804-215427574.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215426208_20260804-215426479.mmd`](AuthResource_login_20260804-215426208_20260804-215426479.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215428152_20260804-215428387.mmd`](AuthResource_login_20260804-215428152_20260804-215428387.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215426492_20260804-215426493.mmd`](AuthResource_me_20260804-215426492_20260804-215426493.mmd) | 1× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215425209_20260804-215425214.mmd`](BackupService_applyRetention_20260804-215425209_20260804-215425214.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215424852_20260804-215425199.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215424852_20260804-215425199.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215426491_20260804-215426492.mmd`](CurrentUser_find_20260804-215426491_20260804-215426492.mmd) | 3× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215427593_20260804-215427675.mmd`](DashboardResource_summary_20260804-215427593_20260804-215427675.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215425434_20260804-215425435.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215425434_20260804-215425435.mmd) | 22× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215426489_20260804-215426489.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215426489_20260804-215426489.mmd) | 7× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215425433_20260804-215425433.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215425433_20260804-215425433.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215425875_20260804-215425875.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215425875_20260804-215425875.mmd) | 2× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215426484_20260804-215426488.mmd`](SessionService_authenticate_20260804-215426484_20260804-215426488.mmd) | 7× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
