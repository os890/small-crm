# To do

Deliberately left out of the proof of concept. Ordered roughly by how much it would hurt to ship
without it.

## Before this is used on real customer data

- **Off-site backups.** Backups land in a folder next to the database, which survives a mistaken
  delete but not a lost or stolen machine. Copying them somewhere else is still manual.
- **Nothing verifies a backup can be restored.** The files are written and never read back until
  someone needs them. A periodic "restore into a scratch database" check would turn a silent
  corruption into a warning.
- **A failed backup is only logged.** A permanently full or read-only backup folder stops the
  automatic backups; with the production log file that is at least recorded, but the interface
  says nothing. "Last successful backup" belongs on the Backup screen, or in the health check.
- **Audit trail.** Records carry `createdAt`, `updatedAt` and an owner, but nothing records who
  changed what. For a shared workspace that is worth having.
- **HTTPS is the operator's job.** The application marks its cookie `Secure` and trusts
  `X-Forwarded-*` when told to (`SMALLCRM_HTTPS`, `SMALLCRM_BEHIND_PROXY`), but ships no TLS
  configuration and no reverse-proxy example of its own.

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

- **Server-side sorting.** Lists are paged, but the sort order of each is fixed. Clicking a
  column heading to sort by it is the obvious next thing somebody will try.
- **Full-name search.** Searching "Maria Huber" finds nothing because first and last name are
  matched separately.
- **The deal board is not paged.** It fetches the API's maximum page and says so when there is
  more; splitting a pipeline across pages would make columns look empty when they are not, so
  the real answer is per-column paging, which has not been built.
- **Hidden source maps in production.** Maps are currently emitted openly so the Playwright
  coverage report can map back to TypeScript. A public deployment should use
  `"hidden": true` and upload them separately.
- **Accessibility audit.** Labels, focus order and roles were written with care, and the
  confirmation prompt is now a native `<dialog>` so the browser handles focus trapping and
  Escape. No screen reader test and no automated axe run has happened, and the other dialogs
  are still hand-rolled backdrops.
- **Frontend bundle for offline use.** No service worker, no offline mode.

## Operations

- **No dependency vulnerability scanning.** Nothing tells us when the pinned Quarkus, or either
  pnpm lockfile, needs a security bump. OWASP dependency-check in `verify`, or a scheduled
  audit, would.
- **No service definition.** No systemd unit or launchd plist ships with the application, so
  nothing restarts it after a crash or a reboot.
- **No documented H2 upgrade path.** H2's file format changes across major versions, so a future
  Quarkus bump can leave the application unable to open an existing `smallcrm.mv.db`. The
  database snapshots written alongside the XML backups cover the data; the procedure is not
  written down.

## Testing gaps

- No test covers two users working at the same time, which is where the shared workspace
  assumptions would actually be exercised.
- The end-to-end suite runs Chromium only. Firefox and WebKit are one line each in
  `playwright.config.ts` but were not run here.
- No load or volume testing; behaviour with 10,000 contacts is unknown.
- The Playwright suite shares one database and runs single-worker. Per-test isolation would let
  it run in parallel and would remove the need for unique names.
