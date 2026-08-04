# the overview counts what was entered and lists what is overdue

Recorded from `e2e/tests/deals-and-tasks.spec.ts`, recorded up to the assertion it failed on — it expects data that a spec earlier in the suite leaves behind.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd)**
— the 36 application chains below, in the order the application handled them, one
block per request.

Not rendered to PNG: 36 requests make an image thousands of pixels tall. The
`.mmd` above renders in any Mermaid viewer that can scroll.

21 distinct call chain(s), out of 304 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `ApiExceptionMappers.handleConstraintViolation` | [`ApiExceptionMappers_handleConstraintViolation_20260804-215356729_20260804-215356729.mmd`](ApiExceptionMappers_handleConstraintViolation_20260804-215356729_20260804-215356729.mmd) | 1× |
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215354912_20260804-215355626.mmd`](AuthResource_changePassword_20260804-215354912_20260804-215355626.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215354242_20260804-215354514.mmd`](AuthResource_login_20260804-215354242_20260804-215354514.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215354527_20260804-215354528.mmd`](AuthResource_me_20260804-215354527_20260804-215354528.mmd) | 6× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215356296_20260804-215356296.mmd`](AutoBackupTrigger_dataChanged_20260804-215356296_20260804-215356296.mmd) | 6× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215352954_20260804-215352958.mmd`](BackupService_applyRetention_20260804-215352954_20260804-215352958.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215352596_20260804-215352944.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215352596_20260804-215352944.mmd) | 1× |
| `CrmTaskResource.create` | [`CrmTaskResource_create_20260804-215357041_20260804-215357043.mmd`](CrmTaskResource_create_20260804-215357041_20260804-215357043.mmd) | 2× |
| `CrmTaskResource.list` | [`CrmTaskResource_list_20260804-215356896_20260804-215356898.mmd`](CrmTaskResource_list_20260804-215356896_20260804-215356898.mmd) | 6× |
| `CrmTaskResource.setDone` | [`CrmTaskResource_setDone_20260804-215357105_20260804-215357106.mmd`](CrmTaskResource_setDone_20260804-215357105_20260804-215357106.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215354526_20260804-215354527.mmd`](CurrentUser_find_20260804-215354526_20260804-215354527.mmd) | 28× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215355645_20260804-215355731.mmd`](DashboardResource_summary_20260804-215355645_20260804-215355731.mmd) | 2× |
| `DealResource.changeStage` | [`DealResource_changeStage_20260804-215356328_20260804-215356330.mmd`](DealResource_changeStage_20260804-215356328_20260804-215356330.mmd) | 2× |
| `DealResource.create` | [`DealResource_create_20260804-215356292_20260804-215356295.mmd`](DealResource_create_20260804-215356292_20260804-215356295.mmd) | 1× |
| `DealResource.create` | [`DealResource_create_20260804-215356721_20260804-215356728.mmd`](DealResource_create_20260804-215356721_20260804-215356728.mmd) | 1× |
| `DealResource.list` | [`DealResource_list_20260804-215356150_20260804-215356161.mmd`](DealResource_list_20260804-215356150_20260804-215356161.mmd) | 6× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215353472_20260804-215353472.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215353472_20260804-215353472.mmd) | 84× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215354524_20260804-215354524.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215354524_20260804-215354524.mmd) | 76× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215353471_20260804-215353471.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215353471_20260804-215353471.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215353905_20260804-215353905.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215353905_20260804-215353905.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215354519_20260804-215354523.mmd`](SessionService_authenticate_20260804-215354519_20260804-215354523.mmd) | 76× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
