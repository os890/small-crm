# an existing appointment can be edited without clashing with itself

Recorded from `e2e/tests/calendar.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 13 application chains below, in the order the application handled them, one
block per request.

17 distinct call chain(s), out of 104 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AppointmentResource.conflicts` | [`AppointmentResource_conflicts_20260804-215250885_20260804-215250893.mmd`](AppointmentResource_conflicts_20260804-215250885_20260804-215250893.mmd) | 1× |
| `AppointmentResource.create` | [`AppointmentResource_create_20260804-215250514_20260804-215250525.mmd`](AppointmentResource_create_20260804-215250514_20260804-215250525.mmd) | 1× |
| `AppointmentResource.list` | [`AppointmentResource_list_20260804-215250368_20260804-215250370.mmd`](AppointmentResource_list_20260804-215250368_20260804-215250370.mmd) | 3× |
| `AppointmentResource.update` | [`AppointmentResource_update_20260804-215251443_20260804-215251447.mmd`](AppointmentResource_update_20260804-215251443_20260804-215251447.mmd) | 1× |
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215249114_20260804-215249829.mmd`](AuthResource_changePassword_20260804-215249114_20260804-215249829.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215248445_20260804-215248717.mmd`](AuthResource_login_20260804-215248445_20260804-215248717.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215248732_20260804-215248732.mmd`](AuthResource_me_20260804-215248732_20260804-215248732.mmd) | 2× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215250525_20260804-215250525.mmd`](AutoBackupTrigger_dataChanged_20260804-215250525_20260804-215250525.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215247188_20260804-215247193.mmd`](BackupService_applyRetention_20260804-215247188_20260804-215247193.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215246827_20260804-215247177.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215246827_20260804-215247177.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215248730_20260804-215248731.mmd`](CurrentUser_find_20260804-215248730_20260804-215248731.mmd) | 10× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215249847_20260804-215249918.mmd`](DashboardResource_summary_20260804-215249847_20260804-215249918.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215247683_20260804-215247684.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215247683_20260804-215247684.mmd) | 31× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215248728_20260804-215248728.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215248728_20260804-215248728.mmd) | 23× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215247682_20260804-215247682.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215247682_20260804-215247682.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215248118_20260804-215248118.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215248118_20260804-215248118.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215248724_20260804-215248727.mmd`](SessionService_authenticate_20260804-215248724_20260804-215248727.mmd) | 23× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
