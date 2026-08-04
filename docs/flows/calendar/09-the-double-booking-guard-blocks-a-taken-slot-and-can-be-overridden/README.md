# the double booking guard blocks a taken slot and can be overridden

Recorded from `e2e/tests/calendar.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 22 application chains below, in the order the application handled them, one
block per request.

18 distinct call chain(s), out of 137 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `ApiExceptionMappers.handleAppointmentConflict` | [`ApiExceptionMappers_handleAppointmentConflict_20260804-215231787_20260804-215231787.mmd`](ApiExceptionMappers_handleAppointmentConflict_20260804-215231787_20260804-215231787.mmd) | 2× |
| `AppointmentResource.conflicts` | [`AppointmentResource_conflicts_20260804-215230277_20260804-215230282.mmd`](AppointmentResource_conflicts_20260804-215230277_20260804-215230282.mmd) | 3× |
| `AppointmentResource.create` | [`AppointmentResource_create_20260804-215230829_20260804-215230838.mmd`](AppointmentResource_create_20260804-215230829_20260804-215230838.mmd) | 3× |
| `AppointmentResource.create` | [`AppointmentResource_create_20260804-215231780_20260804-215231786.mmd`](AppointmentResource_create_20260804-215231780_20260804-215231786.mmd) | 2× |
| `AppointmentResource.list` | [`AppointmentResource_list_20260804-215229848_20260804-215229850.mmd`](AppointmentResource_list_20260804-215229848_20260804-215229850.mmd) | 4× |
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215228581_20260804-215229293.mmd`](AuthResource_changePassword_20260804-215228581_20260804-215229293.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215227929_20260804-215228200.mmd`](AuthResource_login_20260804-215227929_20260804-215228200.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215228214_20260804-215228215.mmd`](AuthResource_me_20260804-215228214_20260804-215228215.mmd) | 2× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215230838_20260804-215230838.mmd`](AutoBackupTrigger_dataChanged_20260804-215230838_20260804-215230838.mmd) | 3× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215226965_20260804-215226970.mmd`](BackupService_applyRetention_20260804-215226965_20260804-215226970.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215226609_20260804-215226955.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215226609_20260804-215226955.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215228213_20260804-215228214.mmd`](CurrentUser_find_20260804-215228213_20260804-215228214.mmd) | 16× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215229312_20260804-215229386.mmd`](DashboardResource_summary_20260804-215229312_20260804-215229386.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215227166_20260804-215227167.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215227166_20260804-215227167.mmd) | 37× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215228211_20260804-215228211.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215228211_20260804-215228211.mmd) | 29× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215227165_20260804-215227165.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215227165_20260804-215227165.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215227606_20260804-215227606.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215227606_20260804-215227606.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215228206_20260804-215228210.mmd`](SessionService_authenticate_20260804-215228206_20260804-215228210.mmd) | 29× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
