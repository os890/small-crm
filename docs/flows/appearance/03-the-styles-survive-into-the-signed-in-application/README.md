# the styles survive into the signed-in application

Recorded from `e2e/tests/appearance.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 6 application chains below, in the order the application handled them, one
block per request.

13 distinct call chain(s), out of 77 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215113048_20260804-215113763.mmd`](AuthResource_changePassword_20260804-215113048_20260804-215113763.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215112397_20260804-215112670.mmd`](AuthResource_login_20260804-215112397_20260804-215112670.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215112684_20260804-215112685.mmd`](AuthResource_me_20260804-215112684_20260804-215112685.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215111168_20260804-215111172.mmd`](BackupService_applyRetention_20260804-215111168_20260804-215111172.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215110792_20260804-215111157.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215110792_20260804-215111157.mmd) | 1× |
| `ContactResource.list` | [`ContactResource_list_20260804-215114281_20260804-215114285.mmd`](ContactResource_list_20260804-215114281_20260804-215114285.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215112682_20260804-215112684.mmd`](CurrentUser_find_20260804-215112682_20260804-215112684.mmd) | 5× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215113782_20260804-215113857.mmd`](DashboardResource_summary_20260804-215113782_20260804-215113857.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215111618_20260804-215111619.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215111618_20260804-215111619.mmd) | 26× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215112681_20260804-215112681.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215112681_20260804-215112681.mmd) | 18× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215111617_20260804-215111617.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215111617_20260804-215111617.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215112059_20260804-215112060.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215112059_20260804-215112060.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215112676_20260804-215112680.mmd`](SessionService_authenticate_20260804-215112676_20260804-215112680.mmd) | 18× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
