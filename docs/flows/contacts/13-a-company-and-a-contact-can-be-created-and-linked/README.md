# a company and a contact can be created and linked

Recorded from `e2e/tests/contacts.spec.ts`, passed.

**The use-case as one diagram: [`use-case.mmd`](use-case.mmd) · [PNG](use-case.png)**
— the 15 application chains below, in the order the application handled them, one
block per request.

17 distinct call chain(s), out of 123 recorded:

| Entry point | Diagram | Recorded |
|---|---|---|
| `AuthResource.changePassword` | [`AuthResource_changePassword_20260804-215255746_20260804-215256460.mmd`](AuthResource_changePassword_20260804-215255746_20260804-215256460.mmd) | 1× |
| `AuthResource.login` | [`AuthResource_login_20260804-215255096_20260804-215255367.mmd`](AuthResource_login_20260804-215255096_20260804-215255367.mmd) | 1× |
| `AuthResource.me` | [`AuthResource_me_20260804-215255381_20260804-215255381.mmd`](AuthResource_me_20260804-215255381_20260804-215255381.mmd) | 3× |
| `AutoBackupTrigger.dataChanged` | [`AutoBackupTrigger_dataChanged_20260804-215257128_20260804-215257128.mmd`](AutoBackupTrigger_dataChanged_20260804-215257128_20260804-215257128.mmd) | 2× |
| `BackupService.applyRetention` | [`BackupService_applyRetention_20260804-215253812_20260804-215253817.mmd`](BackupService_applyRetention_20260804-215253812_20260804-215253817.mmd) | 1× |
| `BootstrapAdminService.createAdminIfNoUsersExist` | [`BootstrapAdminService_createAdminIfNoUsersExist_20260804-215253454_20260804-215253801.mmd`](BootstrapAdminService_createAdminIfNoUsersExist_20260804-215253454_20260804-215253801.mmd) | 1× |
| `CompanyResource.create` | [`CompanyResource_create_20260804-215257125_20260804-215257128.mmd`](CompanyResource_create_20260804-215257125_20260804-215257128.mmd) | 1× |
| `CompanyResource.list` | [`CompanyResource_list_20260804-215256989_20260804-215256991.mmd`](CompanyResource_list_20260804-215256989_20260804-215256991.mmd) | 3× |
| `ContactResource.create` | [`ContactResource_create_20260804-215257393_20260804-215257408.mmd`](ContactResource_create_20260804-215257393_20260804-215257408.mmd) | 1× |
| `ContactResource.list` | [`ContactResource_list_20260804-215257217_20260804-215257222.mmd`](ContactResource_list_20260804-215257217_20260804-215257222.mmd) | 2× |
| `CurrentUser.find` | [`CurrentUser_find_20260804-215255379_20260804-215255380.mmd`](CurrentUser_find_20260804-215255379_20260804-215255380.mmd) | 12× |
| `DashboardResource.summary` | [`DashboardResource_summary_20260804-215256479_20260804-215256566.mmd`](DashboardResource_summary_20260804-215256479_20260804-215256566.mmd) | 1× |
| `SessionAuthenticationMechanism.authenticate` | [`SessionAuthenticationMechanism_authenticate_20260804-215254321_20260804-215254322.mmd`](SessionAuthenticationMechanism_authenticate_20260804-215254321_20260804-215254322.mmd) | 36× |
| `SessionAuthenticationMechanism.getCredentialTransport` | [`SessionAuthenticationMechanism_getCredentialTransport_20260804-215255377_20260804-215255377.mmd`](SessionAuthenticationMechanism_getCredentialTransport_20260804-215255377_20260804-215255377.mmd) | 28× |
| `SessionAuthenticationMechanism.getCredentialTypes` | [`SessionAuthenticationMechanism_getCredentialTypes_20260804-215254320_20260804-215254320.mmd`](SessionAuthenticationMechanism_getCredentialTypes_20260804-215254320_20260804-215254320.mmd) | 1× |
| `SessionAuthenticationMechanism.sendChallenge` | [`SessionAuthenticationMechanism_sendChallenge_20260804-215254763_20260804-215254763.mmd`](SessionAuthenticationMechanism_sendChallenge_20260804-215254763_20260804-215254763.mmd) | 1× |
| `SessionService.authenticate` | [`SessionService_authenticate_20260804-215255373_20260804-215255376.mmd`](SessionService_authenticate_20260804-215255373_20260804-215255376.mmd) | 28× |

Only the combined diagram above is rendered to PNG; `--render-chains` renders these single
chains as well, which is worth doing locally and not worth committing. The chains that are
missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the
startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session
check Quarkus runs before every request and what the application does while it starts: the
same shapes in every use-case, recorded here like everything else.
