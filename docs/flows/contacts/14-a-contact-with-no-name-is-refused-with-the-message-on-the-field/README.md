# a contact with no name is refused with the message on the field

Recorded from `e2e/tests/contacts.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 8 application chains below, in the order the application handled them, one
block per request.

15 distinct call chain(s), out of 83 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `ApiExceptionMappers.handleConstraintViolation` | [`ApiExceptionMappers_handleConstraintViolation_20260804-215302835_20260804-215302836.mmd`](ApiExceptionMappers_handleConstraintViolation_20260804-215302835_20260804-215302836.mmd) | 1× |
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215301446_20260804-215302161.mmd`](AuthResource_changePassword_20260804-215301446_20260804-215302161.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215300778_20260804-215301054.mmd`](AuthResource_login_20260804-215300778_20260804-215301054.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215301069_20260804-215301070.mmd`](AuthResource_me_20260804-215301069_20260804-215301070.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215259782_20260804-215259787.mmd`](BackupService_applyRetention_20260804-215259782_20260804-215259787.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215259424_20260804-215259771.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215259424_20260804-215259771.mmd) | 1× |
| `ContactResource.create` | [`ContactResource_create_20260804-215302826_20260804-215302835.mmd`](ContactResource_create_20260804-215302826_20260804-215302835.mmd) | 1× |
| `ContactResource.list` | [`ContactResource_list_20260804-215302686_20260804-215302691.mmd`](ContactResource_list_20260804-215302686_20260804-215302691.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215301067_20260804-215301068.mmd`](CurrentUser_find_20260804-215301067_20260804-215301068.mmd) | 6× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215302180_20260804-215302261.mmd`](DashboardResource_summary_20260804-215302180_20260804-215302261.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215300002_20260804-215300003.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215300002_20260804-215300003.mmd) | 27× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215301065_20260804-215301065.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215301065_20260804-215301065.mmd) | 19× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215300001_20260804-215300001.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215300001_20260804-215300001.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215300443_20260804-215300443.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215300443_20260804-215300443.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215301060_20260804-215301064.mmd`](SessionService_authenticate_20260804-215301060_20260804-215301064.mmd) | 19× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
