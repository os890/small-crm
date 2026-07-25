# To do

Deliberately left out of the proof of concept. Ordered roughly by how much it would hurt to ship
without it.

## Before this is used on real customer data

- **Versioned database migrations.** Hibernate currently runs with
  `quarkus.hibernate-orm.schema-management.strategy=update`, which quietly alters the schema and
  never removes anything. Add Flyway, generate a baseline from the current schema and switch the
  strategy to `validate`.
- **Backups.** The whole database is `data/smallcrm.mv.db`. There is no export, no scheduled copy
  and no restore path. At minimum: a "download a backup" button and a documented restore.
- **A real session secret.** `quarkus.http.auth.session.encryption-key` falls back to a value
  committed to the repository. The application should refuse to start in production unless
  `SMALLCRM_SESSION_KEY` is set.
- **HTTPS and secure cookies.** The session cookie is not marked `secure` because the proof of
  concept is served over plain HTTP on localhost. Behind TLS, set
  `quarkus.http.auth.form.cookie-secure=true`.
- **Password rules and rate limiting.** The only rule today is a minimum of eight characters.
  There is no lockout, no delay and no limit on failed sign-in attempts.
- **Reject deactivated accounts during authentication.** They currently receive a valid session
  cookie and are refused afterwards, on every API call, with `403 ACCOUNT_DEACTIVATED`. A
  `SecurityIdentityAugmentor` should fail the authentication itself.
- **Audit trail.** Records carry `createdAt`, `updatedAt` and an owner, but nothing records who
  changed what. For a shared workspace that is worth having.
- **CSRF protection.** Session cookies are `SameSite=strict`, which covers the common cases, but
  Quarkus' CSRF extension would be the belt-and-braces answer once the app is exposed.

## Google Calendar synchronisation

The schema already carries `externalCalendarId`, `externalEventId`, `externalEtag`,
`lastSyncedAt` and a per-appointment `timeZone`, so appointments created now can be adopted by a
future sync instead of needing a migration. Still missing:

- OAuth2 consent flow and refresh token storage per user.
- A scheduled pull, plus push on create, update and delete, reconciled through the etag.
- Conflict resolution when both sides changed since `lastSyncedAt`.
- Recurring appointments. The current model has no recurrence rule at all, and Google Calendar
  is full of them; this is the largest single gap.

## Features the audience will ask for next

- **Recurring appointments and all-day events.**
- **Invoicing or at least quote export**, which is the other half of a self-employed person's
  paperwork.
- **Document and file attachments** on contacts and deals.
- **E-mail integration**: log an e-mail as an interaction without retyping it.
- **Import and export** of contacts as CSV or vCard, so moving in and out is possible.
- **Reminders**: the dashboard shows what is overdue, but nothing notifies the user.
- **Deal history**: stage changes are applied without recording when they happened, so no funnel
  or cycle-time reporting is possible.

## Scale and polish

- **Pagination and server-side sorting.** Every list endpoint returns everything it finds. Fine
  for a few hundred records, not for tens of thousands.
- **Full-name search.** Searching "Maria Huber" finds nothing because first and last name are
  matched separately.
- **Optimistic locking is stored but not surfaced.** Entities carry `@Version`, yet the API
  neither accepts nor returns it, so two people editing the same record silently overwrite one
  another.
- **Hidden source maps in production.** Maps are currently emitted openly so the Playwright
  coverage report can map back to TypeScript. A public deployment should use
  `"hidden": true` and upload them separately.
- **Accessibility audit.** Labels, focus order and roles were written with care and the dialogs
  use `aria-modal`, but no screen reader test and no automated axe run has happened. Focus is
  not trapped inside dialogs.
- **Frontend bundle for offline use.** No service worker, no offline mode.

## Testing gaps

- No test covers two users working at the same time, which is where the shared workspace
  assumptions would actually be exercised.
- The end-to-end suite runs Chromium only. Firefox and WebKit are one line each in
  `playwright.config.ts` but were not run here.
- No load or volume testing; behaviour with 10,000 contacts is unknown.
- The Playwright suite shares one database and runs single-worker. Per-test isolation would let
  it run in parallel and would remove the need for unique names.
