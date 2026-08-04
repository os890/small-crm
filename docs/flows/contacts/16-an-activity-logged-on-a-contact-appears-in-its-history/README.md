# an activity logged on a contact appears in its history

Recorded from `e2e/tests/contacts.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 16 application chains below, in the order the application handled them, one
block per request.

20 distinct call chain(s), out of 128 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215313780_20260804-215314493.mmd`](AuthResource_changePassword_20260804-215313780_20260804-215314493.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215313128_20260804-215313400.mmd`](AuthResource_login_20260804-215313128_20260804-215313400.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215313414_20260804-215313415.mmd`](AuthResource_me_20260804-215313414_20260804-215313415.mmd) | 2× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215315168_20260804-215315168.mmd`](AutoBackupTrigger_dataChanged_20260804-215315168_20260804-215315168.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215311823_20260804-215311828.mmd`](BackupService_applyRetention_20260804-215311823_20260804-215311828.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215311465_20260804-215311812.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215311465_20260804-215311812.mmd) | 1× |
| `ContactResource.create` | [`ContactResource_create_20260804-215315159_20260804-215315167.mmd`](ContactResource_create_20260804-215315159_20260804-215315167.mmd) | 1× |
| `ContactResource.get` | [`ContactResource_get_20260804-215315279_20260804-215315281.mmd`](ContactResource_get_20260804-215315279_20260804-215315281.mmd) | 1× |
| `ContactResource.list` | [`ContactResource_list_20260804-215315024_20260804-215315029.mmd`](ContactResource_list_20260804-215315024_20260804-215315029.mmd) | 2× |
| `CrmTaskResource.list` | [`CrmTaskResource_list_20260804-215315279_20260804-215315281.mmd`](CrmTaskResource_list_20260804-215315279_20260804-215315281.mmd) | 1× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215313412_20260804-215313414.mmd`](CurrentUser_find_20260804-215313412_20260804-215313414.mmd) | 13× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215314512_20260804-215314582.mmd`](DashboardResource_summary_20260804-215314512_20260804-215314582.mmd) | 1× |
| `DealResource.list` | [`DealResource_list_20260804-215315279_20260804-215315290.mmd`](DealResource_list_20260804-215315279_20260804-215315290.mmd) | 1× |
| `InteractionResource.create` | [`InteractionResource_create_20260804-215315427_20260804-215315430.mmd`](InteractionResource_create_20260804-215315427_20260804-215315430.mmd) | 1× |
| `InteractionResource.list` | [`InteractionResource_list_20260804-215315278_20260804-215315282.mmd`](InteractionResource_list_20260804-215315278_20260804-215315282.mmd) | 2× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215312345_20260804-215312346.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215312345_20260804-215312346.mmd) | 37× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215313410_20260804-215313410.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215313410_20260804-215313410.mmd) | 29× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215312344_20260804-215312344.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215312344_20260804-215312344.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215312788_20260804-215312788.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215312788_20260804-215312788.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215313406_20260804-215313409.mmd`](SessionService_authenticate_20260804-215313406_20260804-215313409.mmd) | 29× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
