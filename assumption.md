# Assumptions

Decisions taken during implementation that were not fixed by the requirements. Each one is
reversible; where a change would ripple, that is noted.

## Product

1. **Overlap is checked per owner, not per workspace.** Two users can hold appointments in the
   same time slot; only the same person cannot be double booked. A shared workspace is about
   seeing each other's records, not about sharing one physical calendar.
2. **A conflicting slot is refused, not silently allowed.** `POST`/`PUT /api/appointments`
   answers `409` and lists the colliding appointments. Because "book both anyway" is a real
   need (a webinar during a client call), `?allowConflict=true` saves regardless, and the UI
   surfaces that as a separate "Save anyway" button that only appears after a refusal.
3. **Touching boundaries do not collide.** An appointment ending at 10:00 and one starting at
   10:00 both fit. Without this, back-to-back scheduling would be impossible.
4. **Interactions cannot be dated in the future.** The activity log records what happened; a
   future entry is almost always a mistyped year or a confusion with the calendar.
5. **Deletes detach rather than cascade, except for interactions.** Removing a company keeps its
   contacts and deals and only clears the link; removing a contact deletes its interaction
   history, because an interaction without a contact has no meaning. Losing a company must never
   silently lose the people behind it.
6. **New accounts always start with a forced password change.** Both the bootstrap administrator
   and every account an administrator adds get `mustChangePassword`, so a password typed by
   somebody else never becomes permanent.
7. **A deactivated account is refused at login, and its open sessions end at once.** It gets the
   same answer as a wrong password, so deactivation cannot be used to discover who has an
   account. This replaces an earlier arrangement where the login succeeded and every subsequent
   call answered `403`.
8. **The last active administrator is protected.** Demoting, deactivating or deleting them is
   refused, and nobody can delete or deactivate their own account. For a one-person business a
   lockout would mean losing access to their own customer data.
9. **Interaction type names (Call, Email, Meeting, Note) are shown untranslated.** They read the
   same in both languages; four keys per language would add work without adding clarity.
10. **Native date and time inputs follow the operating system, not the language switch.** A
    German user on an English macOS will see `07/26/2026` in the date picker while every date
    the application renders itself shows `26.7.2026`. That is how browsers work with
    `<input type="date">`, and matching the system convention is arguably the right behaviour;
    replacing them with custom pickers would be the alternative.
11. **The XML backup holds business data only, never accounts.** Contacts, companies, deals,
    activities, to-dos and appointments travel; user names and password hashes do not. A backup
    can therefore be handed to an accountant or moved between machines without leaking
    credentials. The cost is that a restore re-links each record to its owner by user name and
    leaves the owner empty where no such account exists on the target installation. A full
    database snapshot is written alongside it for the disaster case, and that one *does* contain
    accounts — it is the copy to keep and not the copy to send.
12. **A change schedules a backup rather than writing one.** Changes arriving inside a 30 second
    window (`smallcrm.backup.coalesce-seconds`) end up in one file, so editing five fields in a
    row produces one backup instead of five and an idle installation produces none. At most a
    few seconds of work is ever missing from the newest file.
13. **The rolling clean-up only ever touches files this application wrote.** A file is deleted
    only if its name matches the `smallcrm-backup-` or `before-restore-` pattern, so anything a
    user copied into the folder is left alone, and equally is never offered as a restore
    candidate.
14. **Retention applies to the before-restore copies too.** They are safety nets for an
    accidental restore, not an archive, and two weeks of undo is ample.
15. **A restore replaces everything; there is no merge option.** Merging would need identity
    rules the data does not have and would silently produce duplicates.
16. **Money is stored per deal with a free-text currency and defaults to EUR.** No conversion, no
    rates: the audience invoices in one currency and occasionally in another.

## Technical

17. **Angular 22.0.8 with Vitest and jsdom**, which is what `ng new` produces on Angular 22.
18. **Quarkus 3.37.4, not 3.38.0.** 3.38.0 was released two days before this work started; the
    latest patch of the previous minor is the lower risk choice. Only the property in
    `pom.xml` changes to move up.
19. **Runtime translation instead of Angular's build-time i18n.** Angular's own i18n emits one
    bundle per locale, which Quinoa would have to serve behind a locale prefix and which cannot
    be switched without a reload. A signal-backed catalogue (`I18nService`) switches instantly,
    keeps a single bundle, and makes a missing German key a compile error, because `DE` is typed
    as `Record<TranslationKey, string>` against the English catalogue.
20. **Server-side validation messages follow `Accept-Language`.** The frontend sends the chosen
    language on every request, so Hibernate Validator answers in it. `quarkus.default-locale=en`
    pins the fallback so it does not depend on the locale of the host.
21. **The application's own session mechanism, not Quarkus form authentication.** A random token
    in an `HttpOnly` cookie, with the session itself in the database and only its SHA-256 stored.
    Quarkus' built-in cookie is self-contained, which would mean sessions that cannot be ended
    server-side, a password change that does not invalidate them, and a configured key that
    anybody holding could use to mint a cookie for any account. `POST /api/auth/login` still
    takes a form encoded body, not JSON.
22. **Flyway owns the schema, Hibernate only validates it.** Migrations live in
    `src/main/resources/db/migration` and run at startup; `schema-management.strategy=validate`
    then fails fast if the entities and the schema have drifted apart. An installation created
    before Flyway existed is baselined at V1 rather than rebuilt.
23. **Enum columns are plain `VARCHAR`, not H2's native `ENUM`.** Hibernate maps
    `@Enumerated(STRING)` to an `ENUM` listing every constant, which ties the schema to H2 and
    turns "add a deal stage" into a migration. `@JdbcTypeCode(SqlTypes.VARCHAR)` pins them to
    text; migration V2 converts installations built the old way.
24. **The money on a deal is called `amount`, not `value`.** `VALUE` is a reserved word in H2 and
    the `CREATE TABLE` fails on it, so the column, the entity field and the API field all say
    `amount`.
25. **Panache active record with public fields.** The idiomatic Quarkus style; it keeps the
    entities short and there is no behaviour to hide behind accessors.
26. **Every list endpoint is paged, 50 by default and 200 at most**, with the total in
    `X-Total-Count`. A self-employed person has hundreds of contacts, but the activity log gains
    a row for every call and e-mail ever logged and is never pruned, so no request may ask for
    a whole table. The deal board is the one screen that is not paged — splitting a pipeline
    across pages would leave columns looking empty when they are not — so it fetches the
    maximum and says when there is more.
27. **Checkstyle runs a Google Java Style subset** (`config/checkstyle/checkstyle.xml`): the
    mechanically checkable formatting and naming rules, without the Javadoc completeness checks.
    Requiring Javadoc on everything produces ceremony, not explanation; the rules that police
    Javadoc that *is* written are kept.
28. **Production builds emit source maps.** They make the Playwright coverage report point at
    TypeScript instead of minified bundles. For a self-hosted single-user tool that is a fair
    trade; `todo.md` notes switching to hidden maps for a public deployment.
29. **pnpm is the package manager, and installs run with `--ignore-scripts`**, wired through
    `quarkus.quinoa.package-manager-command.install`.
30. **Unit tests pin the time zone to `Europe/Vienna` and the locale per test.** Date grouping
    and currency formatting are zone and locale dependent; without pinning, the same test would
    pass locally and fail in a UTC container.
31. **The end-to-end suite runs on port 8099 against a throwaway H2 directory** under the system
    temp folder, so every run starts from a genuinely empty installation, and it runs
    single-worker because all specs share that one database.
32. **`@QuarkusTest` coverage needs the `quarkus-jacoco` extension.** Quarkus rewrites bytecode
    at build time, so the plain JaCoCo agent measures classes that are never loaded and reports
    almost nothing. The extension appends to the same `jacoco.exec`, and the Maven plugin still
    owns the report and the threshold check.

## Google integration

These were decided when the optional Google integration was added; each one changes what happens
to somebody's real Google data, so none of them is an implementation detail.

- **Google links an account, it never creates one.** An administrator invites somebody as before;
  connecting Google is something that person then does from their own settings. Letting a Google
  sign-in create an account would mean anyone with a Google account could walk into a database of
  somebody's customers.
- **Only contacts carrying one Google label are synced.** This is a shared workspace, so mirroring
  a whole address book into it would put somebody's dentist and their mother in front of their
  colleagues. The user chooses what is shared by labelling it in Google.
- **Records Google holds more richly than this application can are read-only here.** A recurring
  meeting, an all-day event, a person with several e-mail addresses, a Google subtask. They are
  pulled in and shown, and the API refuses to change them. The alternative — writing back what
  this application knows — would replace a standing weekly meeting with a single event, or
  delete five addresses, in data this application does not own.
- **A pull touches only the fields Google owns.** A to-do's priority, contact and deal have
  nowhere to live in Google Tasks, so they are never overwritten by an incoming record and
  survive a round trip.
- **Conflicts are resolved per record by whichever side changed last.** Field-level merging would
  be better and is not built; the timestamps involved are Google's `updateTime` and this
  application's `updatedAt`.
- **The refresh token is encrypted at rest and the key has no default.** It is a live credential
  to somebody's whole Google account, which makes it more dangerous than anything else in the
  database. Without `SMALLCRM_TOKEN_KEY` the integration declines to store credentials at all.
- **The OAuth flow issues an ordinary session.** Google is an identity source, not a second
  authentication mechanism, so a Google sign-in ends in the same server-side session a password
  login produces — one kind of session, one place to revoke it.
- **The id token's signature is not verified.** It arrives over a direct TLS connection to
  Google's token endpoint rather than through the browser, which is the case OpenID Connect
  explicitly allows server validation to stand in for. Verifying would mean fetching and rotating
  Google's JWKS to prove what the transport already proved.
- **The sync runs on a timer whose interval is configuration, with `off` as a value.** How often
  is a matter of taste and of how much of somebody's Google quota they want spent, so it is not a
  constant. `off` matters as much as the number: while somebody is still watching what a two-way
  sync does to their real data, the button should be the only thing that moves it.
- **A pass that outlasts its interval is skipped, not queued.** Stacking a second pass on a slow
  one makes both slower and can have the two halves of one account disagreeing with each other.
- **A resource that fails three times running is left alone for an hour.** A scheduled job that
  retries a broken call every quarter of an hour for ever is rude to Google and useless to the
  user: the quota drains and the log fills with the same line. The backoff deliberately does not
  apply to the Sync now button, because somebody who has just fixed their Google settings should
  not be told to wait.
- **Scheduling uses the Quarkus scheduler rather than the bare executor the backups use.** The
  interval has to come from configuration, `off` has to be one of its values, and overlapping
  passes have to be skipped — all of which the extension already does. The two mechanisms
  sitting side by side is a small inconsistency accepted on purpose rather than rewriting
  working backup code.
- **Consent state lives in memory.** It matters for the minutes a browser is away at Google, and a
  restart mid-consent costs one click; a table for it would hold nothing but rubbish within the
  hour.
