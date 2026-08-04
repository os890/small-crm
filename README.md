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

Nothing installed? Two commands, and they need none of the above:

```bash
./build.sh          # build.cmd on Windows
./start.sh          # start.cmd on Windows
```

`build.sh` fetches its own Java, Maven, Node and pnpm into `.build-tools/`, builds the
application, and writes the ready-to-hand-on packages described under
[Packaging it for someone else](#packaging-it-for-someone-else). See
[Building with nothing installed](#building-with-nothing-installed) for what that does and does
not touch.

`start.sh` then runs what was built, using that same fetched Java — so there is still nothing
installed and no archive to unpack. It opens the browser once the application answers.
`SMALLCRM_PORT=9000 ./start.sh` moves it off port 8080, and `SMALLCRM_NO_BROWSER=1` leaves the
browser alone. After a plain `mvn package` it works just as well, falling back to `JAVA_HOME` or
the `java` on your `PATH`.

With the tools already there — **Java 25**, **Maven 3.9+**, **Node 22+** and **pnpm** (the last
two only for the frontend) — the ordinary way round works as well:

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

Or take a ready-made package instead, which needs no Java at all — see
[Packaging it for someone else](#packaging-it-for-someone-else).

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
is no default password to look up: each installation generates its own.

**If you closed the window before reading it**, the same block is in the log — the start script
logs it as well as printing it:

```bash
grep -A4 'password:' logs/small-crm.log
```

It stays there in clear text until the log rotates out, so on a machine other people can read,
delete that log once you have signed in and chosen your own password. There is no other copy: if
both the window and the log are gone, the only way back into a running installation is to start
over from an empty `data/` folder.

If you would rather set the first password yourself, put it in
`SMALLCRM_BOOTSTRAP_ADMIN_PASSWORD` before the first start; it still has to be changed at the
first sign-in.

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

### Recording what a use-case actually does

[cdi-flow](../java-flow) records every public method call of this application's beans and writes
the chain out as a Mermaid sequence diagram. It is off unless a build asks for it:

```bash
mvn package -DskipTests -Dcdi-flow.enabled=true   # build with the recorder in
node e2e/scripts/record-flows.mjs --render        # drive every use-case, render the diagrams
```

The script runs each Playwright test on its own, against a freshly started application whose
recorder writes into that use-case's own directory below [`docs/flows/`](docs/flows) — which is
what keeps one use-case's diagrams apart from the next without any labelling inside the
application. Identical chains are collapsed to one file, with the number of occurrences in the
index written beside them.

A flow ends when its outermost call returns, and each request is its own outermost call on its own
thread, so a use-case is a handful of chains rather than one. Every directory therefore also holds
a `use-case.mmd` that stitches them back together in the order the application handled them, one
block per request — the blocks are the recorded chains, unchanged.

[`docs/flows/use-cases.md`](docs/flows/use-cases.md) is the one file to read: every use-case with a
short description and its diagram inline. The prose lives in
`e2e/scripts/use-case-descriptions.mjs`; everything else in it is recorded.

`--render` turns each combined diagram into a PNG, borrowing the same `mermaid-cli` container image
the architecture diagrams use, and `--render-chains` adds an image per single chain — dozens per
use-case, worth a local look and not worth committing. Rendering is by far the slowest part, so
`--render-only` finishes an interrupted one without driving the application again, and
`--only <substring>` records a single use-case.

cdi-flow ships as a portable CDI extension, which ArC never runs: Quarkus resolves beans,
interceptors and bindings while the application is built. Its interceptor and its `@FlowRecorded`
binding are ordinary CDI artefacts though, so `org.os890.smallcrm.flow` supplies the two pieces
that were missing — a build compatible extension that attaches the binding during augmentation,
and a startup observer that arms the recorder. Both check `cdi-flow.enabled` first, so an ordinary
build instruments nothing at all and the shipped application carries no interceptor.

### Packaging it for someone else

```bash
./build.sh                       # fetches its own toolchain; build.cmd on Windows
```

or, with Java, Maven, Node and pnpm already installed:

```bash
mvn package
node packaging/build-distributions.mjs
```

Produces one self-contained archive per platform in `target/dist/`, about 100 MB each:

| Platform | Archive | Start it with |
| --- | --- | --- |
| macOS (Apple silicon) | `small-crm-<version>-macos-aarch64.tar.gz` | `./start.sh` |
| Linux (Intel/AMD 64-bit) | `small-crm-<version>-linux-x64.tar.gz` | `./start.sh` |
| Windows (Intel/AMD 64-bit) | `small-crm-<version>-windows-x64.zip` | double-click `start.cmd` |

Each carries its own Java runtime — the Eclipse Temurin JRE that Adoptium publishes for that
platform, downloaded during the build and checked against its published SHA-256 — so the person
receiving it installs nothing. The start script waits for the application to come up and then
opens the browser at it. `data/`, `backup/` and `logs/` live inside the package folder, which
makes the folder the whole installation.

All three are built from one machine. Note that this deliberately does not use `jlink`, which
would make them smaller: given another platform's modules it still writes the host's launcher, so
a Linux package built on a Mac came out with a macOS `java` in it. An official per-platform JRE is
genuinely native and needs no build machine of that kind.

Pass a target id to build just one: `./build.sh linux-x64`, or
`node packaging/build-distributions.mjs linux-x64`.

### Building with nothing installed

`./build.sh` (`build.cmd` on Windows) builds everything on a machine that has no Java, no Maven,
no Node and no pnpm. It is the same build — it ends in `mvn package` and the packaging script
above — with the toolchain fetched first instead of assumed.

What it needs is what every macOS and Linux install already has: a shell, `curl`, `tar`, and
`shasum` or `sha256sum`. On Windows, PowerShell and `tar`, both present since Windows 10.

What it fetches, each checked against the checksum its publisher announces and discarded if it
does not match:

| | From | How the version is chosen |
| --- | --- | --- |
| Node 22 | nodejs.org | current 22.x, read from the published `SHASUMS256.txt` |
| Temurin JDK 25 | Adoptium | current build for this platform, from the Adoptium API |
| Maven | Apache | pinned in `packaging/bootstrap-build.mjs`, checksum read from Apache |
| pnpm | Corepack | the version pinned in `package.json`, so it matches the lockfiles |

Everything lands in `.build-tools/` inside the project folder. Nothing is installed, no `PATH`
or `JAVA_HOME` outside the build is read or written, and a Java or Maven already on the machine
is deliberately ignored rather than used — so what comes out does not depend on what happened to
be there. `rm -rf .build-tools` undoes it completely.

Only the first run pays for the downloads; after that they are cached and reused.

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
build.sh, build.cmd         builds on a machine with nothing installed
start.sh, start.cmd         runs what was built, with the Java build.sh fetched
config/checkstyle/          Java style rules
src/main/java/org/os890/smallcrm/
  domain/                   JPA entities and the rules that belong on them
  backup/                   XML export and import, the rolling folder, the change trigger
  service/                  business logic, one service per aggregate
  api/                      REST resources, DTOs and the single error shape
  security/                 bootstrap administrator, current user, account state
  flow/                     development only: arms the cdi-flow recorder, off unless asked for
src/main/resources/         application.properties
  db/migration/             Flyway migrations, applied at startup
src/main/webui/             Angular 22 application
  src/app/core/             API client, auth, i18n, formatting, error handling
  src/app/features/         one folder per screen
  src/app/shared/           toasts, the confirmation prompt, the pager and the record picker
packaging/                  builds the self-contained per-platform archives, and fetches the
                            toolchain build.sh hands over to
e2e/                        Playwright suite against the packaged application
docs/manual/                illustrated user manual, English and German, HTML and PDF
docs/architecture.md        the diagrams, from the schema up to the deployment
docs/diagrams/              the same diagrams as PNG, plus an index page to browse them
docs/flows/                 recorded sequence diagrams, one directory per use-case
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
