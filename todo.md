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
- **`SMALLCRM_TOKEN_KEY` has no rotation story.** Once Google accounts are connected, changing
  the key or losing it means every user reconnects, and nothing warns anybody before that
  happens. Re-encrypting the stored tokens under a new key would be a few lines and is not
  written.
- **HTTPS is the operator's job.** The application marks its cookie `Secure` and trusts
  `X-Forwarded-*` when told to (`SMALLCRM_HTTPS`, `SMALLCRM_BEHIND_PROXY`), but ships no TLS
  configuration and no reverse-proxy example of its own.

## Google integration

Built on the `feat/google-integration` branch: sign-in, and two-way sync of contacts, calendar
and to-dos. What is still open there:

- **Nothing has ever run against Google.** Every test is against a stub built from the published
  API documentation. The field names, the shape of the sync tokens and the exact error codes are
  unverified, and the first run with real credentials should be treated as the real test. This
  is the one item here that could turn out to be more than a small fix.
- **The scheduled sync is per installation, not per user.** One interval covers everybody, and a
  user who wants their own cadence cannot have one.
- **A failing resource backs off for an hour and then tries again for ever.** It never gives up
  and never tells anybody beyond the settings screen, so a connection somebody revoked at Google
  keeps being retried until they notice.
- **Recurring appointments are read-only.** The model has no recurrence rule, so a series is
  shown and edited in Google. Giving appointments a rule of their own would make them writable
  and is the largest remaining piece.
- **Conflicts are per record, not per field.** Two people editing different fields of the same
  contact at the same time still loses one of the edits.
- **One Google account per user, primary calendar and default task list only.** No choice of
  which calendar or which list.
- **Contacts synced by two colleagues arrive twice.** Google resource names are per account, so
  the same person in two address books becomes two records here. Matching on e-mail address on
  the way in would fix it.

## Peer-to-peer sync between team members

Everyone runs their own instance and they reconcile whenever they can reach each other, with no
central server. Sketched out but not started; it is comparable in size to everything else in this
file put together, and the first phase alone is about the size of the production-readiness pass.

**Nothing can merge until identity changes.** `BaseEntity` uses `GenerationType.IDENTITY` —
per-database autoincrement — so two people both creating a contact both get id 5. UUIDv7 primary
keys are the prerequisite for every other part of this: time ordered, so index locality and
"newest first" survive, and generated locally with no coordination. Migrating properly beats
adding a `uuid` column beside the numeric one, which would leak a second identity into every
join and every sync path for ever. `BackupService` already rebuilds relationships through id
maps, so export → migrate → re-import gets existing installations across.

**The shape: an append-only change log, replicated by anti-entropy, resolved per field.**

- A `change_log` table of `(peer_id, seq, entity_uuid, entity_type, op, payload, hlc, actor)`,
  written by every mutation. Deletes are tombstones — without them a record you delete comes
  back the next time a peer that still has it syncs.
- A hybrid logical clock for ordering: physical millis, a counter and the peer id as tiebreak.
  `Clocks` and the injectable `Clock` are where it hangs.
- Last writer wins **per field**, not per record. Per record silently discards a colleague's
  edit to a different field, which is the thing people notice and do not forgive.
- Tags become an add-wins set; they are already their own table. Free-text notes are the one
  field where last-writer-wins genuinely destroys work, so a real conflict should keep both and
  say so rather than pick.

**Transport, in the order worth building it.**

1. mDNS on the local network (`_smallcrm._tcp.local`). Zero configuration, and it covers
   everyone who is ever in the same room. Sync itself is two endpoints on the HTTP server each
   instance already runs, so every peer is both client and server.
2. Change bundles as files, for everyone else. A sync is "my changes since your position",
   which is a file; the merge code does not care whether it arrived over HTTP, on a memory
   stick or through a folder that Syncthing keeps in step. This needs no discovery, no pairing
   handshake and no liveness handling, so it is arguably less work than the network path.
   Note that syncing *bundles* this way is safe where syncing the database file is not: each
   bundle is written once by one peer and never modified, so there is no concurrent write to
   corrupt and applying one twice is harmless.
3. A "mailbox" peer, only if the team wants continuous sync while apart. One instance somewhere
   reachable, running the same code and holding the same log, with no authority over anybody:
   not a source of truth, and its loss costs nothing. Worth checking whether the team's
   connections have working IPv6 first — with no NAT in the way, peers can simply connect.

**Trust.** An Ed25519 keypair per instance, pairing by a one-time short code, changes signed and
only accepted from paired peers. Not optional: this puts customer data on a wire that today has
no authentication between machines at all. Accounts also have to become synced identities —
uuid, display name, public key — with credentials staying local.
`RestoreOutcome.unresolvedOwners` already models that gap.

**One invariant does not survive, and that is a product decision.** The double-booking guard is a
uniqueness constraint, and no merge strategy preserves those across disconnected peers: two
people offline will both book 10:00 and nothing prevents it. The honest design is to detect the
overlap when the changes meet, mark both appointments conflicted, and surface them on the
calendar and the dashboard for a person to settle.

**Knock-on effects elsewhere.** Optimistic locking becomes a local-only guard — `STALE_VERSION`
still means something inside one instance but is not the cross-peer story. The backup format
needs uuids and tombstones, so `BackupModel.FORMAT_VERSION` goes to 2. Restoring an old backup on
a synced peer would resurrect deleted records unless a restore is itself expressed as changes,
which is worth designing for rather than discovering. And it needs a two-peer test harness; the
suite has never run two instances at once.

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
- Nothing exercises the Google integration end to end through a browser. The backend is covered
  against a stub; the settings screen and the sign-in button are covered by unit tests only,
  because a Playwright run would need a Google account or a stub the packaged application could
  be pointed at.
