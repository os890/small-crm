# back to back appointments are not treated as a clash

Recorded from `e2e/tests/calendar.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 13 application chains below, in the order the application handled them, one
block per request.

16 distinct call chain(s), out of 104 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AppointmentResource.conflicts` | [`AppointmentResource_conflicts_20260804-215238965_20260804-215238965.mmd`](AppointmentResource_conflicts_20260804-215238965_20260804-215238965.mmd) | 1× |
| `AppointmentResource.create` | [`AppointmentResource_create_20260804-215238564_20260804-215238577.mmd`](AppointmentResource_create_20260804-215238564_20260804-215238577.mmd) | 2× |
| `AppointmentResource.list` | [`AppointmentResource_list_20260804-215238398_20260804-215238399.mmd`](AppointmentResource_list_20260804-215238398_20260804-215238399.mmd) | 3× |
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215237163_20260804-215237877.mmd`](AuthResource_changePassword_20260804-215237163_20260804-215237877.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215236512_20260804-215236784.mmd`](AuthResource_login_20260804-215236512_20260804-215236784.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215236799_20260804-215236799.mmd`](AuthResource_me_20260804-215236799_20260804-215236799.mmd) | 2× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215238578_20260804-215238578.mmd`](AutoBackupTrigger_dataChanged_20260804-215238578_20260804-215238578.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215235220_20260804-215235225.mmd`](BackupService_applyRetention_20260804-215235220_20260804-215235225.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215234862_20260804-215235209.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215234862_20260804-215235209.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215236797_20260804-215236798.mmd`](CurrentUser_find_20260804-215236797_20260804-215236798.mmd) | 10× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215237896_20260804-215237964.mmd`](DashboardResource_summary_20260804-215237896_20260804-215237964.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215235746_20260804-215235747.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215235746_20260804-215235747.mmd) | 31× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215236795_20260804-215236795.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215236795_20260804-215236795.mmd) | 23× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215235745_20260804-215235745.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215235745_20260804-215235745.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215236181_20260804-215236181.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215236181_20260804-215236181.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215236790_20260804-215236794.mmd`](SessionService_authenticate_20260804-215236790_20260804-215236794.mmd) | 23× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
