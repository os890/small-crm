# a bookmarked page opens the application instead of a blank 404

Recorded from `e2e/tests/i18n-and-users.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 15 application chains below, in the order the application handled them, one
block per request.

17 distinct call chain(s), out of 164 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AppointmentResource.list` | [`AppointmentResource_list_20260804-215422799_20260804-215422802.mmd`](AppointmentResource_list_20260804-215422799_20260804-215422802.mmd) | 1× |
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215421395_20260804-215422110.mmd`](AuthResource_changePassword_20260804-215421395_20260804-215422110.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215420726_20260804-215420999.mmd`](AuthResource_login_20260804-215420726_20260804-215420999.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215421013_20260804-215421014.mmd`](AuthResource_me_20260804-215421013_20260804-215421014.mmd) | 6× |
| `BackupResource.list` | [`BackupResource_list_20260804-215422849_20260804-215422850.mmd`](BackupResource_list_20260804-215422849_20260804-215422850.mmd) | 1× |
| `BackupResource.settings` | [`BackupResource_settings_20260804-215422849_20260804-215422850.mmd`](BackupResource_settings_20260804-215422849_20260804-215422850.mmd) | 1× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215419455_20260804-215419460.mmd`](BackupService_applyRetention_20260804-215419455_20260804-215419460.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215419097_20260804-215419445.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215419097_20260804-215419445.mmd) | 1× |
| `ContactResource.list` | [`ContactResource_list_20260804-215422654_20260804-215422658.mmd`](ContactResource_list_20260804-215422654_20260804-215422658.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215421011_20260804-215421013.mmd`](CurrentUser_find_20260804-215421011_20260804-215421013.mmd) | 14× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215422128_20260804-215422197.mmd`](DashboardResource_summary_20260804-215422128_20260804-215422197.mmd) | 2× |
| `DealResource.list` | [`DealResource_list_20260804-215422736_20260804-215422745.mmd`](DealResource_list_20260804-215422736_20260804-215422745.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215419944_20260804-215419945.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215419944_20260804-215419945.mmd) | 49× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215421010_20260804-215421010.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215421010_20260804-215421010.mmd) | 41× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215419943_20260804-215419943.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215419943_20260804-215419943.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215420393_20260804-215420393.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215420393_20260804-215420393.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215421005_20260804-215421009.mmd`](SessionService_authenticate_20260804-215421005_20260804-215421009.mmd) | 41× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
