# a negative amount is rejected with the message on the field

Recorded from `e2e/tests/deals-and-tasks.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 8 application chains below, in the order the application handled them, one
block per request.

15 distinct call chain(s), out of 86 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `ApiExceptionMappers.handleConstraintViolation` | [`ApiExceptionMappers_handleConstraintViolation_20260804-215332568_20260804-215332568.mmd`](ApiExceptionMappers_handleConstraintViolation_20260804-215332568_20260804-215332568.mmd) | 1× |
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215331162_20260804-215331877.mmd`](AuthResource_changePassword_20260804-215331162_20260804-215331877.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215330494_20260804-215330765.mmd`](AuthResource_login_20260804-215330494_20260804-215330765.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215330779_20260804-215330780.mmd`](AuthResource_me_20260804-215330779_20260804-215330780.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215329195_20260804-215329200.mmd`](BackupService_applyRetention_20260804-215329195_20260804-215329200.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215328836_20260804-215329185.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215328836_20260804-215329185.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215330778_20260804-215330779.mmd`](CurrentUser_find_20260804-215330778_20260804-215330779.mmd) | 6× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215331895_20260804-215331965.mmd`](DashboardResource_summary_20260804-215331895_20260804-215331965.mmd) | 1× |
| `DealResource.create` | [`DealResource_create_20260804-215332559_20260804-215332567.mmd`](DealResource_create_20260804-215332559_20260804-215332567.mmd) | 1× |
| `DealResource.list` | [`DealResource_list_20260804-215332417_20260804-215332427.mmd`](DealResource_list_20260804-215332417_20260804-215332427.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215329725_20260804-215329726.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215329725_20260804-215329726.mmd) | 28× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215330776_20260804-215330776.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215330776_20260804-215330776.mmd) | 20× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215329725_20260804-215329725.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215329725_20260804-215329725.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215330159_20260804-215330159.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215330159_20260804-215330159.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215330771_20260804-215330775.mmd`](SessionService_authenticate_20260804-215330771_20260804-215330775.mmd) | 20× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
