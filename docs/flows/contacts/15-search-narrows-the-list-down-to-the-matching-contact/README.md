# search narrows the list down to the matching contact

Recorded from `e2e/tests/contacts.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 13 application chains below, in the order the application handled them, one
block per request.

15 distinct call chain(s), out of 104 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215307147_20260804-215307860.mmd`](AuthResource_changePassword_20260804-215307147_20260804-215307860.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215306479_20260804-215306752.mmd`](AuthResource_login_20260804-215306479_20260804-215306752.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215306766_20260804-215306767.mmd`](AuthResource_me_20260804-215306766_20260804-215306767.mmd) | 2× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215308535_20260804-215308535.mmd`](AutoBackupTrigger_dataChanged_20260804-215308535_20260804-215308535.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215305222_20260804-215305227.mmd`](BackupService_applyRetention_20260804-215305222_20260804-215305227.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215304864_20260804-215305212.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215304864_20260804-215305212.mmd) | 1× |
| `ContactResource.create` | [`ContactResource_create_20260804-215308526_20260804-215308535.mmd`](ContactResource_create_20260804-215308526_20260804-215308535.mmd) | 2× |
| `ContactResource.list` | [`ContactResource_list_20260804-215308376_20260804-215308380.mmd`](ContactResource_list_20260804-215308376_20260804-215308380.mmd) | 4× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215306764_20260804-215306766.mmd`](CurrentUser_find_20260804-215306764_20260804-215306766.mmd) | 10× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215307879_20260804-215307951.mmd`](DashboardResource_summary_20260804-215307879_20260804-215307951.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215305703_20260804-215305704.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215305703_20260804-215305704.mmd) | 31× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215306762_20260804-215306762.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215306762_20260804-215306762.mmd) | 23× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215305703_20260804-215305703.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215305703_20260804-215305703.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215306141_20260804-215306141.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215306141_20260804-215306141.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215306758_20260804-215306761.mmd`](SessionService_authenticate_20260804-215306758_20260804-215306761.mmd) | 23× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
