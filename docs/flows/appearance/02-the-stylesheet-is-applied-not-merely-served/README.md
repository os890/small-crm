# the stylesheet is applied, not merely served

Recorded from `e2e/tests/appearance.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 4 application chains below, in the order the application handled them, one
block per request.

12 distinct call chain(s), out of 48 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215107532_20260804-215108245.mmd`](AuthResource_changePassword_20260804-215107532_20260804-215108245.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215106880_20260804-215107152.mmd`](AuthResource_login_20260804-215106880_20260804-215107152.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215107165_20260804-215107166.mmd`](AuthResource_me_20260804-215107165_20260804-215107166.mmd) | 1× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215105591_20260804-215105596.mmd`](BackupService_applyRetention_20260804-215105591_20260804-215105596.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215105230_20260804-215105580.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215105230_20260804-215105580.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215107164_20260804-215107165.mmd`](CurrentUser_find_20260804-215107164_20260804-215107165.mmd) | 3× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215108264_20260804-215108334.mmd`](DashboardResource_summary_20260804-215108264_20260804-215108334.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215106108_20260804-215106109.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215106108_20260804-215106109.mmd) | 22× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215107162_20260804-215107162.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215107162_20260804-215107162.mmd) | 7× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215106108_20260804-215106108.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215106108_20260804-215106108.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215106548_20260804-215106548.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215106548_20260804-215106548.mmd) | 2× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215107157_20260804-215107161.mmd`](SessionService_authenticate_20260804-215107157_20260804-215107161.mmd) | 7× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
