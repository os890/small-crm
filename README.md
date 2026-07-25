# Small CRM

A small CRM for self-employed people: contacts and companies, an activity log, a deal pipeline,
follow-up to-dos and a calendar that refuses to double book you.

One process serves everything — the REST API and the web interface — against a single H2 file on
disk. No database server, no container, no cloud account.

**New here?** There is an illustrated user manual that walks through every screen — open it in a
browser: [English](docs/manual/index.html) · [Deutsch](docs/manual/index.de.html).

---

## Run it

Requirements: **Java 25**, **Maven 3.9+**, **Node 22+** and **pnpm** (only for building the
frontend).

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

Open <http://localhost:8080> and sign in as `admin` with the password from
`smallcrm.bootstrap.admin.password` (`changeit` out of the box). The first sign-in forces you to
choose your own password.

The database lives in `./data/smallcrm.mv.db`. Point `SMALLCRM_DATA_DIR` somewhere else to move
it. That one file is the entire installation — copy it and you have a backup.

### Development

```bash
mvn quarkus:dev
```

Quinoa starts the Angular dev server alongside Quarkus, so both the backend and the frontend
reload on save. Everything stays on <http://localhost:8080>.

### Configuration worth knowing

| Setting | Environment variable | Default |
| --- | --- | --- |
| HTTP port | `QUARKUS_HTTP_PORT` | `8080` |
| Database directory | `SMALLCRM_DATA_DIR` | `./data` |
| Backup folder | `SMALLCRM_BACKUP_DIR` | `./backup` |
| Session encryption key | `SMALLCRM_SESSION_KEY` | a placeholder — **set this in production** |
| Bootstrap administrator | `SMALLCRM_BOOTSTRAP_ADMIN_USERNAME` / `..._PASSWORD` | `admin` / `changeit` |

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
- **English and German**, switched at any time from the header without a reload. Dates, times and
  currency follow the choice (`25/07/2026` and `€1,234.50` against `25.7.2026` and `€ 1.234,50`),
  and server-side validation messages come back translated too.

---

## Tests

| Suite | Command | Covers |
| --- | --- | --- |
| Backend | `mvn test` | 132 JUnit tests: REST, services, domain rules, backup round trips, the real login flow |
| Frontend | `cd src/main/webui && pnpm test` | 174 Vitest tests: services, guards, i18n and every page |
| End to end | `mvn verify -Pe2e` | 23 Playwright tests through the packaged application |

Coverage is enforced, not just reported — each build fails below its floor:

| Suite | Tool | Lines | Branches |
| --- | --- | --- | --- |
| Backend | JaCoCo (`quarkus-jacoco`) | 91% (floor 80%) | 80% (floor 70%) |
| Frontend | Vitest with `@vitest/coverage-v8` | 94% (floor 80%) | 78% (floor 75%) |
| End to end | monocart-reporter (V8) | 74% | 63% |

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
src/main/java/org/smallcrm/
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
  src/app/shared/           toasts and the confirmation prompt
e2e/                        Playwright suite against the packaged application
docs/manual/                illustrated user manual, English and German
```

`docs/manual/` is the illustrated user manual in English and German; the screenshots are taken
from a throwaway instance filled with demo data, in the matching language.
`assumption.md` records the decisions taken where the requirements were open.
`todo.md` records what was deliberately left out.

---

## Licence

Apache License 2.0 — see [LICENSE](LICENSE).
