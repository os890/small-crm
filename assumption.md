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
7. **A deactivated account is blocked at the API, not at login.** The login itself still succeeds
   and issues a cookie, but every API call answers `403 ACCOUNT_DEACTIVATED`. Rejecting during
   authentication is cleaner and is listed in `todo.md`.
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
11. **Money is stored per deal with a free-text currency and defaults to EUR.** No conversion, no
    rates: the audience invoices in one currency and occasionally in another.

## Technical

12. **Angular 22.0.8 with Vitest and jsdom**, which is what `ng new` produces on Angular 22.
13. **Quarkus 3.37.4, not 3.38.0.** 3.38.0 was released two days before this work started; the
    latest patch of the previous minor is the lower risk choice. Only the property in
    `pom.xml` changes to move up.
14. **Runtime translation instead of Angular's build-time i18n.** Angular's own i18n emits one
    bundle per locale, which Quinoa would have to serve behind a locale prefix and which cannot
    be switched without a reload. A signal-backed catalogue (`I18nService`) switches instantly,
    keeps a single bundle, and makes a missing German key a compile error, because `DE` is typed
    as `Record<TranslationKey, string>` against the English catalogue.
15. **Server-side validation messages follow `Accept-Language`.** The frontend sends the chosen
    language on every request, so Hibernate Validator answers in it. `quarkus.default-locale=en`
    pins the fallback so it does not depend on the locale of the host.
16. **Form authentication with a session cookie**, configured with empty login/error/landing
    pages so it answers with status codes instead of redirects, which is what a single page
    application needs. `POST /api/auth/login` takes a form encoded body, not JSON.
17. **Hibernate `schema-management.strategy=update`.** Acceptable while the schema is still
    moving; `todo.md` records the switch to versioned migrations.
18. **`Deal.value` is mapped to the column `amount`.** `VALUE` is a reserved word in H2 and the
    `CREATE TABLE` fails on it. The API field is called `amount` for the same reason.
19. **Panache active record with public fields.** The idiomatic Quarkus style; it keeps the
    entities short and there is no behaviour to hide behind accessors.
20. **Lists are returned unpaged.** A self-employed person has hundreds, not millions, of
    contacts. Pagination is in `todo.md`.
21. **Checkstyle runs a Google Java Style subset** (`config/checkstyle/checkstyle.xml`): the
    mechanically checkable formatting and naming rules, without the Javadoc completeness checks.
    Requiring Javadoc on everything produces ceremony, not explanation; the rules that police
    Javadoc that *is* written are kept.
22. **Production builds emit source maps.** They make the Playwright coverage report point at
    TypeScript instead of minified bundles. For a self-hosted single-user tool that is a fair
    trade; `todo.md` notes switching to hidden maps for a public deployment.
23. **pnpm is the package manager, and installs run with `--ignore-scripts`**, wired through
    `quarkus.quinoa.package-manager-command.install`.
24. **Unit tests pin the time zone to `Europe/Vienna` and the locale per test.** Date grouping
    and currency formatting are zone and locale dependent; without pinning, the same test would
    pass locally and fail in a UTC container.
25. **The end-to-end suite runs on port 8099 against a throwaway H2 directory** under the system
    temp folder, so every run starts from a genuinely empty installation, and it runs
    single-worker because all specs share that one database.
26. **`@QuarkusTest` coverage needs the `quarkus-jacoco` extension.** Quarkus rewrites bytecode
    at build time, so the plain JaCoCo agent measures classes that are never loaded and reports
    almost nothing. The extension appends to the same `jacoco.exec`, and the Maven plugin still
    owns the report and the threshold check.
