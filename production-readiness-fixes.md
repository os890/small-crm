# Production readiness review — what was done

Companion to [`production-readiness-review.md`](production-readiness-review.md). One row per
finding, in the review's order. Every fix is covered by a test unless the row says otherwise.

**Verified after the work:** 152 backend tests, 214 frontend tests, 24 end-to-end tests, all
passing; `mvn verify -Pe2e` green with RAT, Checkstyle, Prettier and every coverage floor met.

---

## Summary

| | Fixed | Fixed differently | Not done |
| --- | --- | --- | --- |
| Security (SEC) | 15 | – | – |
| API and logic (API) | 9 | 1 | – |
| Backup (BAK) | 6 | – | – |
| Frontend (UI) | 8 | – | – |
| Operations (OPS) | 5 | – | 1 |

All five release blockers (SEC-1, SEC-4, SEC-5, BAK-1, BAK-2) are closed.

---

## 1. Security

**SEC-1 · Hardcoded fallback session key.** Fixed, by removing the mechanism rather than the key.
Quarkus form authentication is gone; `SessionAuthenticationMechanism` issues a 256-bit random
token and keeps the session in an `app_session` row (`V4`), storing only its SHA-256. There is now
no shared secret anywhere in the configuration, so there is nothing to leak, guess or forge —
which also closes SEC-2, SEC-6, SEC-8, SEC-15 in the same move. Covered by `LoginFlowTest`.

**SEC-2 · Cookie not `HttpOnly`.** Fixed. `SessionCookie` sets `HttpOnly`, `SameSite=Strict` and
`Path=/`; the token is opaque and useless without the server-side row.

**SEC-3 · `Secure` never set, `X-Forwarded-Proto` not trusted.** Fixed. `SMALLCRM_HTTPS=true`
marks the cookie `Secure`; `SMALLCRM_BEHIND_PROXY=true` turns on `proxy-address-forwarding`.
Both default to off, because a `Secure` cookie over plain HTTP is silently dropped by the browser
and would lock everyone out with no message. Documented in the README table.

**SEC-4 · `AUTO_SERVER=TRUE` exposes a passwordless database listener.** Fixed. Removed from the
production JDBC URL and kept only under `%dev`, with the reason written next to it.
`SMALLCRM_DB_PASSWORD` was added for operators who want one.

**SEC-5 · Published default admin credentials.** Fixed. There is no default any more: with
`smallcrm.bootstrap.admin.password` unset, `BootstrapAdminService` generates 24 random bytes and
prints them once, in a framed block, to the operator's own console. `changeit` is gone from
`application.properties`, the README and both manuals. Dev mode keeps a known password
(`dev-only-password`) so a fresh checkout is usable; it cannot apply to a packaged run.

**SEC-6 · Password change does not invalidate sessions.** Fixed. Changing a password revokes every
session of that account; the browser doing the change is immediately given a fresh cookie, so the
user is not thrown back to the login screen by their own action.

**SEC-7 · No brute-force protection.** Fixed. `LoginService` locks an account after five failures
with a doubling backoff capped at 30 minutes, recorded in `failedLoginCount`/`lockedUntil`.

**SEC-8 · Deactivated accounts still authenticate.** Fixed. Deactivation revokes the account's
sessions on the spot, and a deactivated account gets exactly the answer a wrong password gets.

**SEC-9 · No CSRF token.** Fixed. `CrossOriginWriteFilter` refuses any `POST`/`PUT`/`DELETE`/
`PATCH` carrying an `Origin` header from another origin. With `SameSite=Strict` on the session
cookie this is belt and braces, which is the right amount for a single-origin application.

**SEC-10 · No security response headers.** Fixed. `X-Content-Type-Options`, `X-Frame-Options`,
`Referrer-Policy` and a `Content-Security-Policy` that needs no `unsafe-eval`; HSTS in `%prod`.

**SEC-11 · bcrypt cost 10.** Fixed. `Passwords.COST = 12`.

**SEC-12 · Admin password reset needs no re-authentication.** Fixed. Resetting somebody else's
password requires the acting administrator's own password, so a hijacked admin session cannot
quietly take over every other account.

**SEC-13 · Username enumeration via timing.** Fixed. A miss verifies a fixed dummy hash so the
work done is the same either way.

**SEC-14 · Weak password policy, silent bcrypt truncation.** Fixed. 12–72 characters, enforced by
validation. Deliberately *not* pre-hashed before bcrypt: the login path verifies the raw password,
so pre-hashing would have locked out every existing account.

**SEC-15 · Logout hardcodes the cookie name.** Fixed. One `SessionCookie` component owns the name,
and logout revokes the server-side row, so a stale cookie is inert regardless.

---

## 2. Backend API and business logic

**API-1 · Optimistic locking inert.** Fixed. Every DTO carries `version`, `Versions.check`
compares it before applying an edit, and a mismatch is a 409 `STALE_VERSION`. Bulk detach updates
use `update versioned` so they cannot leave another transaction holding a version that looks
current.

**API-2 · Double-booking guard is check-then-act.** Fixed. The owner's account row is taken with
`PESSIMISTIC_WRITE` before the overlap query, which makes check-and-insert atomic per calendar
while leaving other users' calendars fully concurrent.

**API-3 · Every list endpoint unbounded.** Fixed. `page` and `size` on contacts, companies, deals,
interactions and tasks; 50 by default, 200 at most, with `X-Total-Count`, `X-Page` and
`X-Page-Size` in the response. `PaginationTest` covers the boundaries. The deal pipeline order had
to move from an in-memory re-sort into the SQL `order by` — re-sorting after the database has
already chosen the page is meaningless.

**API-4 · N+1 when mapping to DTOs.** Fixed. `hibernate.default_batch_fetch_size=64`.

**API-5 · Dashboard sums mixed currencies.** Fixed. The dashboard returns
`openDealValueByCurrency` and the tile shows one figure per currency.

**API-6 · Pipeline order alphabetical.** Fixed, in SQL — see API-3.

**API-7 · Contact tags bypass validation, 500.** Fixed. Length validation on the tag elements.

**API-8 · Invalid enum query parameter returns a bare 404.** Fixed.
`EnumParamConverterProvider` turns it into a 400 with the accepted values named.

**API-9 · No catch-all mapper; duplicate-username race returns 500.** *Fixed differently.* The
review recommended a lowest-priority `@ServerExceptionMapper(Throwable.class)`. That was
implemented — and it silently broke the entire application. A mapper that can handle `Throwable`
also handles the framework's own `NotFoundException`, which is what Quarkus REST raises for a path
no resource matches. Answering it stops the request falling through to Quinoa's single-page
fallback, so every bookmark, reload and deep link (`/contacts`, `/deals`, …) came back as an empty
404 while the application still looked fine from the inside.

The fix keeps both: `quarkus.rest.path=/api` gives the REST layer its own root, so it never sees a
front-end URL, and the catch-all is free to do its job for everything under `/api`. An e2e test
now loads four deep links and asserts the application comes back, so this cannot break again
unnoticed. The duplicate-username race itself is handled by a `PersistenceException` mapper
returning 409.

**API-10 · Assorted.** All fixed:
"today" is judged in a configurable zone (`SMALLCRM_TIME_ZONE`, defaulting to the machine's);
entity timestamps go through the injectable clock (`Clocks`), in `AppUser` as well as
`BaseEntity`; every bulk detach is `update versioned` and sets `updatedAt`; `@Digits` on the deal
amount; the appointment javadoc matches the code; the last-administrator guard reads the other
admins under a write lock so two simultaneous demotions cannot both succeed; `AppUser` has
`@Version` and `updatedAt`; indexes on `interaction.occurredAt` and `crm_task.dueDate`.

Appointments whose owner was deleted still drop out of conflict checks. That is deliberate and
documented in `AppointmentService`: those records have no calendar to belong to.

---

## 3. Backup, restore and durability

**BAK-1 · Restore destroys `createdAt`/`updatedAt`.** Fixed. `applyTimestamps` puts the values
from the file back after persisting, so a restore returns the data as it was rather than as if
every record had been created at the moment of the restore.

**BAK-2 · Disaster recovery impossible from XML alone.** Fixed. `DatabaseSnapshot` writes an H2
`BACKUP TO` archive alongside each XML file. The XML remains the portable, human-readable business
export; the snapshot is the complete copy, accounts included. The README says plainly which is
which, and that copying a live `.mv.db` file is not a backup.

**BAK-3 · No concurrency control around restore.** Fixed. A `ReentrantLock` serialises every write
to the backup folder, which also closes the BAK-6 filename race.

**BAK-4 · `export()` is not a consistent snapshot.** Fixed. The export runs in one transaction.

**BAK-5 · Files never fsynced.** Fixed. `Durability` fsyncs the file and its directory before the
atomic move, so a backup that exists is a backup that survives a power cut.

**BAK-6 · Further issues.** Fixed: the filename race (via BAK-3); a pending coalesced backup is
now written synchronously on shutdown, so the last change before a service stop is not lost;
`restoreFromFolder` checks `Files.size` before reading, so a huge file in the folder cannot OOM
the JVM; `DataChangeFilter` matches whole path segments instead of prefixes; the parser rejects
duplicate and null ids rather than silently corrupting the re-linking maps.

Backup write failures are still only logged. With `%prod` file logging (OPS-2) they are at least
retained; surfacing "last successful backup" in the interface is recorded in `todo.md`.

---

## 4. Frontend

**UI-1 · Session expiry mid-form discards the work.** Fixed. The interceptor keeps the current URL
and hands it back after signing in again, so the user returns to the page they were on. The return
address must start with `/`, otherwise a crafted login link would be an open redirect.

**UI-2 · Blank error toast on 401 and unmapped codes.** Fixed. `errorMessage` falls back to a
readable sentence, and the derived codes now match the translation keys they are looked up under —
a test enumerates them so a mismatch cannot creep back in.

**UI-3 · No double-submit protection on the interaction form.** Fixed, the same way as every other
form on the site.

**UI-4 · Search responses can arrive out of order.** Fixed. Each list page and the record picker
count their requests and ignore anything but the newest answer.

**UI-5 · Deal column totals add mixed currencies.** Fixed. One total per currency per column.

**UI-6 · Confirm dialog: Escape, focus trap, orphaned promises.** Fixed. A native `<dialog>` opened
with `showModal()`, so focus trapping, Escape and inertness are the browser's job; the role is
`alertdialog`. A second question raised while one is open resolves as declined instead of leaving
the first caller's promise pending for ever.

**UI-7 · No pagination; pickers fetch entire tables.** Fixed. A shared `app-pager` on the contact,
company and task lists, which renders nothing while everything fits on one page. The deal board is
not paged — splitting a pipeline across pages would leave columns looking empty when they are not
— so it fetches the maximum page and says plainly when there is more. Every field that points at
another record is an `app-entity-picker`: it looks up what is typed, offers ten matches, says how
many it is not showing, and reports honestly that nothing is selected once the name is typed over.
Contact detail now asks the server for that contact's deals instead of fetching every deal in the
installation and sifting through them.

**UI-8 · Further issues.** All fixed: contact detail reloads when the route id changes; a new
appointment no longer defaults to midnight after 23:00; a cleared date gives a field error rather
than "unexpected error"; signing out over a flaky network still lands on the login screen; a
transient 500 at boot no longer signs a valid session out; the backup upload input is cleared
after use. Source maps still ship, as recorded in `todo.md`.

---

## 5. Build and operations

**OPS-1 · README recommends an unsafe backup procedure.** Fixed. The README no longer says the
database file *is* the backup. It explains the two mechanisms — the automatic XML exports for a
running system, and stopping the application (or H2's `BACKUP TO`) for a complete copy — and states
plainly that the XML files exclude accounts.

**OPS-2 · No production logging.** Fixed. `%prod` writes to `./logs/small-crm.log` with 10 MB
rotation and ten kept files, plus an access log. Authentication failures are logged.

**OPS-3 · No `%prod` profile.** Fixed. Proxy forwarding, HSTS, file logging, OpenAPI off and a
10-second shutdown grace so an in-flight restore can finish.

**OPS-4 · `/q/openapi` publicly reachable.** Fixed. Off in `%prod`.

**OPS-5 · A mistyped `SMALLCRM_DATA_DIR` silently creates an empty database.** Fixed.
`DataDirectoryCheck` refuses to start when the configured directory looks wrong, rather than
cheerfully starting an empty installation over the top of the real one.

**OPS-6 · Further gaps. Not done.** Dependency vulnerability scanning, a sample systemd unit and a
documented H2 upgrade path are all still missing. They are project-infrastructure work rather than
code defects, and are recorded in `todo.md`. The bootstrap race window listed under this finding is
closed by SEC-5: there is no longer a password an attacker could know in advance.
