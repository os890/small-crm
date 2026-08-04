# the interface switches to German and stays there after a reload

Recorded from `e2e/tests/i18n-and-users.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 12 application chains below, in the order the application handled them, one
block per request.

15 distinct call chain(s), out of 115 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `ApiExceptionMappers.handleConstraintViolation` | [`ApiExceptionMappers_handleConstraintViolation_20260804-215410117_20260804-215410117.mmd`](ApiExceptionMappers_handleConstraintViolation_20260804-215410117_20260804-215410117.mmd) | 1× |
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215408611_20260804-215409325.mmd`](AuthResource_changePassword_20260804-215408611_20260804-215409325.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215407960_20260804-215408231.mmd`](AuthResource_login_20260804-215407960_20260804-215408231.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215408245_20260804-215408246.mmd`](AuthResource_me_20260804-215408245_20260804-215408246.mmd) | 4× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215406975_20260804-215406980.mmd`](BackupService_applyRetention_20260804-215406975_20260804-215406980.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215406619_20260804-215406965.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215406619_20260804-215406965.mmd) | 1× |
| `ContactResource.create` | [`ContactResource_create_20260804-215410109_20260804-215410116.mmd`](ContactResource_create_20260804-215410109_20260804-215410116.mmd) | 1× |
| `ContactResource.list` | [`ContactResource_list_20260804-215410002_20260804-215410008.mmd`](ContactResource_list_20260804-215410002_20260804-215410008.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215408243_20260804-215408244.mmd`](CurrentUser_find_20260804-215408243_20260804-215408244.mmd) | 10× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215409344_20260804-215409410.mmd`](DashboardResource_summary_20260804-215409344_20260804-215409410.mmd) | 3× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215407199_20260804-215407199.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215407199_20260804-215407199.mmd) | 35× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215408241_20260804-215408241.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215408241_20260804-215408241.mmd) | 27× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215407198_20260804-215407198.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215407198_20260804-215407198.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215407632_20260804-215407632.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215407632_20260804-215407632.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215408237_20260804-215408240.mmd`](SessionService_authenticate_20260804-215408237_20260804-215408240.mmd) | 27× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
