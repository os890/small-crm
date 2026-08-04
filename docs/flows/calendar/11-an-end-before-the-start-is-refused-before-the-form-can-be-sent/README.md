# an end before the start is refused before the form can be sent

Recorded from `e2e/tests/calendar.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 6 application chains below, in the order the application handled them, one
block per request.

13 distinct call chain(s), out of 77 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AppointmentResource.list` | [`AppointmentResource_list_20260804-215244741_20260804-215244742.mmd`](AppointmentResource_list_20260804-215244741_20260804-215244742.mmd) | 1× |
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215243497_20260804-215244212.mmd`](AuthResource_changePassword_20260804-215243497_20260804-215244212.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215242845_20260804-215243118.mmd`](AuthResource_login_20260804-215242845_20260804-215243118.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215243133_20260804-215243134.mmd`](AuthResource_me_20260804-215243133_20260804-215243134.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215241872_20260804-215241877.mmd`](BackupService_applyRetention_20260804-215241872_20260804-215241877.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215241513_20260804-215241862.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215241513_20260804-215241862.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215243131_20260804-215243132.mmd`](CurrentUser_find_20260804-215243131_20260804-215243132.mmd) | 5× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215244230_20260804-215244314.mmd`](DashboardResource_summary_20260804-215244230_20260804-215244314.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215242081_20260804-215242082.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215242081_20260804-215242082.mmd) | 26× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215243129_20260804-215243129.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215243129_20260804-215243129.mmd) | 18× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215242080_20260804-215242080.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215242080_20260804-215242080.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215242521_20260804-215242521.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215242521_20260804-215242521.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215243124_20260804-215243128.mmd`](SessionService_authenticate_20260804-215243124_20260804-215243128.mmd) | 18× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
