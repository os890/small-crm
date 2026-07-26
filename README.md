# Small CRM

A small CRM for self-employed people: contacts and companies, an activity log, a deal pipeline,
follow-up to-dos and a calendar that refuses to double book you.

One process serves everything — the REST API and the web interface — against a single H2 file on
disk. No database server, no container, no cloud account.

**New here?** There is an illustrated user manual that walks through every screen:
[English](docs/manual/index.html) · [Deutsch](docs/manual/index.de.html) in the browser, or as
PDF to print or pass on —
[small-crm-manual-en.pdf](docs/manual/small-crm-manual-en.pdf) ·
[small-crm-manual-de.pdf](docs/manual/small-crm-manual-de.pdf).

---

## Run it

Requirements: **Java 25**, **Maven 3.9+**, **Node 22+** and **pnpm** (only for building the
frontend).

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

On the very first start one administrator account is created and its password is **printed to
the console**, in a block that is hard to miss:

```
=================== Small CRM first start ===================
  A single administrator account has been created.

      user name: admin
      password:  4Xb2p-…

  Sign in now and choose your own password; this one stops working
  as soon as you do. It is not shown again.
=============================================================
```

Open <http://localhost:8080>, sign in with it, and choose your own password when prompted. There
is no default password to look up, and none is written anywhere else. If you would rather set the
first password yourself, put it in `SMALLCRM_BOOTSTRAP_ADMIN_PASSWORD` before the first start; it
still has to be changed at the first sign-in.

The database lives in `./data/smallcrm.mv.db`. Point `SMALLCRM_DATA_DIR` somewhere else to move
it.

### Backing it up

Two mechanisms, for two different situations.

**While the application runs**, it looks after itself: every change writes the whole dataset to a
timestamped XML file in `./backup`, and the Backup screen restores from one. Those files hold your
business data — contacts, companies, activity, deals, to-dos, appointments — but deliberately
**not** the user accounts. Restoring one onto an empty installation gives you your data back, and
you then re-create the accounts.

**For a complete copy**, including accounts, take the database file — but *not* with `cp` while
the application is running. H2 keeps the store open, and a copy taken mid-write is a copy of a
half-finished write. Either:

- stop the application, then copy `data/smallcrm.mv.db`; or
- leave it running and ask H2 for a consistent archive:

  ```bash
  java -cp "$(ls ~/.m2/repository/com/h2database/h2/*/h2-*.jar | head -1)" org.h2.tools.Shell \
    -url "jdbc:h2:file:./data/smallcrm;AUTO_SERVER=TRUE" -user sa \
    -sql "BACKUP TO 'smallcrm-backup.zip'"
  ```

  `AUTO_SERVER=TRUE` is only enabled in dev mode; in production, stopping the application first is
  the supported route.

### Development

```bash
mvn quarkus:dev
```

Quinoa starts the Angular dev server alongside Quarkus, so both the backend and the frontend
reload on save. Everything stays on <http://localhost:8080>. Dev mode — and only dev mode — has a
known first password, `dev-only-password`, so a fresh checkout can be signed into without reading
the console.

### Configuration worth knowing

| Setting | Environment variable | Default |
| --- | --- | --- |
| HTTP port | `QUARKUS_HTTP_PORT` | `8080` |
| Database directory | `SMALLCRM_DATA_DIR` | `./data` |
| Backup folder | `SMALLCRM_BACKUP_DIR` | `./backup` |
| Bootstrap administrator | `SMALLCRM_BOOTSTRAP_ADMIN_USERNAME` / `..._PASSWORD` | `admin` / generated and printed once |
| Database file password | `SMALLCRM_DB_PASSWORD` | empty; the file's own permissions are the protection |
| Served over HTTPS | `SMALLCRM_HTTPS` | `false`; set to `true` so the session cookie is marked `Secure` |
| Behind a TLS reverse proxy | `SMALLCRM_BEHIND_PROXY` | `false`; set to `true` to trust `X-Forwarded-*` |
| Log file | `SMALLCRM_LOG_FILE` | `./logs/small-crm.log` (production profile only) |

Sessions are held server-side; there is no shared secret to configure and nothing an attacker can
forge from the cookie alone. They idle out after 8 hours and end after 12 regardless.

The bootstrap account is only created while the user table is empty; afterwards these settings
do nothing. How long backups are kept is not a file setting: it lives in the database and is
changed from the Backup screen.

### Schema changes

Flyway owns the schema. Migrations are plain SQL in `src/main/resources/db/migration`, applied at
startup, and Hibernate is set to `validate` so a mismatch between the entities and the database
fails the start rather than being silently patched. An installation that predates Flyway is
baselined at V1 and keeps its data.

---

## What it does

- **Contacts and companies** with tags, free-text notes and a search across name, e-mail and
  phone. Each contact has a page collecting its activity, deals and open to-dos.
- **Activity log** of calls, e-mails, meetings and notes, always attached to a contact.
- **Deal pipeline** as a column per stage (Lead → Qualified → Proposal → Won / Lost), with a
  total per column. Stages change through a dropdown rather than drag and drop, so the target is
  always readable and it works on a touch screen.
- **To-dos** with due dates and priorities, optionally linked to a contact or deal, flagged when
  overdue.
- **Calendar** as an agenda grouped by day. The appointment dialog checks the chosen slot against
  your calendar while you type; a taken slot is refused, and booking it anyway is a separate,
  deliberate click.
- **Users**: an administrator adds accounts, each of which must choose its own password at the
  first sign-in. All users share one workspace and see the same records.
- **Backups**: every change writes the whole dataset to a timestamped XML file in a `backup`
  folder beside the database, and files older than the retention period (14 days by default) are
  removed automatically. The Backup screen creates one on demand, downloads any file, restores
  either from the folder or from an upload, and changes the retention period. A restore always
  writes a `before-restore-<timestamp>.xml` first, so it can itself be undone. Backups carry
  business data only, never accounts or password hashes.
- **Lists that stay fast as they fill up.** Every list endpoint serves one page at a time and
  says how many records there are in total, so the activity log — the one table that grows
  without limit — is never fetched whole. The screens show "51–100 of 812" with the paging
  buttons only when there is more than one page. Fields that point at another record look it up
  as you type instead of loading every contact or company into a dropdown.
- **English and German**, switched at any time from the header without a reload. Dates, times and
  currency follow the choice (`25/07/2026` and `€1,234.50` against `25.7.2026` and `€ 1.234,50`),
  and server-side validation messages come back translated too.

---

## Tests

| Suite | Command | Covers |
| --- | --- | --- |
| Backend | `mvn test` | 152 JUnit tests: REST, services, domain rules, paging, backup round trips, the real login flow |
| Frontend | `cd src/main/webui && pnpm test` | 214 Vitest tests: services, guards, i18n, the shared components and every page |
| End to end | `mvn verify -Pe2e` | 24 Playwright tests through the packaged application |

Coverage is enforced, not just reported — each build fails below its floor:

| Suite | Tool | Lines | Branches |
| --- | --- | --- | --- |
| Backend | JaCoCo (`quarkus-jacoco`) | 84% (floor 80%) | 71% (floor 70%) |
| Frontend | Vitest with `@vitest/coverage-v8` | 91% (floor 80%) | 76% (floor 75%) |
| End to end | monocart-reporter (V8) | 73% | 55% |

Reports land in `target/site/jacoco/`, `src/main/webui/coverage/` and `e2e/coverage/`.

### Quality gates

All of these run in `mvn verify` and fail the build:

- **apache-rat-plugin** — every source file carries the Apache 2.0 short header.
- **maven-checkstyle-plugin** — Google Java Style (`config/checkstyle/checkstyle.xml`).
- **Prettier** — checked before the frontend bundle is produced, through the `build:ci` script.
- **JaCoCo** and **Vitest** coverage thresholds.

---

## Layout

```
pom.xml                     the only build file; Quinoa builds the frontend into the jar
config/checkstyle/          Java style rules
src/main/java/org/os890/smallcrm/
  domain/                   JPA entities and the rules that belong on them
  backup/                   XML export and import, the rolling folder, the change trigger
  service/                  business logic, one service per aggregate
  api/                      REST resources, DTOs and the single error shape
  security/                 bootstrap administrator, current user, account state
src/main/resources/         application.properties
  db/migration/             Flyway migrations, applied at startup
src/main/webui/             Angular 22 application
  src/app/core/             API client, auth, i18n, formatting, error handling
  src/app/features/         one folder per screen
  src/app/shared/           toasts, the confirmation prompt, the pager and the record picker
e2e/                        Playwright suite against the packaged application
docs/manual/                illustrated user manual, English and German, HTML and PDF
docs/architecture.md        the diagrams, from the schema up to the deployment
docs/diagrams/              the same diagrams as PNG, plus an index page to browse them
```

[`docs/architecture.md`](docs/architecture.md) has the diagrams: the tables, the entities mapped
onto them, the code around those, the request pipeline, the key flows, and the single process it
all ships as. The Mermaid blocks in it are the source; `node docs/render-diagrams.mjs` renders
them to PNG in `docs/diagrams/` — open `docs/diagrams/index.html` to look at them without a
Mermaid-aware viewer. Rendering borrows a headless Chromium from the `mermaid-cli` container
image rather than adding a dependency.

`docs/manual/` is the illustrated user manual in English and German, as HTML and as PDF. The
screenshots come from a throwaway instance filled with demo data, in the matching language;
`node e2e/scripts/build-manual-pdf.mjs` regenerates the PDFs from the HTML.
`assumption.md` records the decisions taken where the requirements were open.
`todo.md` records what was deliberately left out.

---

## Licence

Apache License 2.0 — see [LICENSE](LICENSE).
