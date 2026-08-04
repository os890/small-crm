# Recorded flows

One sequence diagram per call chain, recorded by cdi-flow while the end-to-end suite drove
each use-case of the application. Written by `node e2e/scripts/record-flows.mjs --render`
against a build made with `-Dcdi-flow.enabled=true`; nothing here is hand-drawn.

A flow ends when its outermost call returns, and each request is its own outermost call on its
own thread, so a use-case is a handful of chains rather than one. `use-case.mmd` stitches them
back together in the order the application handled them, one block per request - the blocks are
the recorded chains, unchanged.

Every use-case starts from an empty installation, so the first blocks of each combined diagram
are the sign-in the suite does before the test itself: `AuthResource.login`, the forced
`changePassword`, `me`, and the overview that follows. The use-case's own work comes after it.

Identical chains are collapsed to one file - the `Flows` column counts how many chains were
recorded, `Diagrams` how many distinct ones remain. Only public methods of this application's
own beans appear: a method a bean calls on itself never leaves the instance, so no interceptor
sees it, and the static Panache calls of the entities are not bean calls at all.

| # | Use-case | Spec | Flows | Diagrams | Run |
|---|---|---|---|---|---|
| 01 | [first start forces the administrator to pick a new password](auth/01-first-start-forces-the-administrator-to-pick-a-new-password/use-case.mmd) | `auth.setup.ts` | 40 | [12 kept](auth/01-first-start-forces-the-administrator-to-pick-a-new-password/README.md) | passed |
| 02 | [the stylesheet is applied, not merely served](appearance/02-the-stylesheet-is-applied-not-merely-served/use-case.mmd) | `appearance.spec.ts` | 48 | [12 kept](appearance/02-the-stylesheet-is-applied-not-merely-served/README.md) | passed |
| 03 | [the styles survive into the signed-in application](appearance/03-the-styles-survive-into-the-signed-in-application/use-case.mmd) | `appearance.spec.ts` | 77 | [13 kept](appearance/03-the-styles-survive-into-the-signed-in-application/README.md) | passed |
| 04 | [a backup can be created, downloaded and restored from the folder](backups/04-a-backup-can-be-created-downloaded-and-restored-from-the-folder/use-case.mmd) | `backups.spec.ts` | 193 | [20 kept](backups/04-a-backup-can-be-created-downloaded-and-restored-from-the-folder/README.md) | passed |
| 05 | [an unwanted restore can be undone with the before-restore file](backups/05-an-unwanted-restore-can-be-undone-with-the-before-restore-file/use-case.mmd) | `backups.spec.ts` | 571 | [25 kept](backups/05-an-unwanted-restore-can-be-undone-with-the-before-restore-file/README.md) | passed, run with its whole spec in front of it |
| 06 | [the retention period can be changed from the screen](backups/06-the-retention-period-can-be-changed-from-the-screen/use-case.mmd) | `backups.spec.ts` | 120 | [15 kept](backups/06-the-retention-period-can-be-changed-from-the-screen/README.md) | passed |
| 07 | [a file that is not a backup is refused without losing any data](backups/07-a-file-that-is-not-a-backup-is-refused-without-losing-any-data/use-case.mmd) | `backups.spec.ts` | 128 | [19 kept](backups/07-a-file-that-is-not-a-backup-is-refused-without-losing-any-data/README.md) | passed |
| 08 | [a plain user cannot reach the backup screen](backups/08-a-plain-user-cannot-reach-the-backup-screen/use-case.mmd) | `backups.spec.ts` | 133 | [14 kept](backups/08-a-plain-user-cannot-reach-the-backup-screen/README.md) | passed |
| 09 | [the double booking guard blocks a taken slot and can be overridden](calendar/09-the-double-booking-guard-blocks-a-taken-slot-and-can-be-overridden/use-case.mmd) | `calendar.spec.ts` | 137 | [18 kept](calendar/09-the-double-booking-guard-blocks-a-taken-slot-and-can-be-overridden/README.md) | passed |
| 10 | [back to back appointments are not treated as a clash](calendar/10-back-to-back-appointments-are-not-treated-as-a-clash/use-case.mmd) | `calendar.spec.ts` | 104 | [16 kept](calendar/10-back-to-back-appointments-are-not-treated-as-a-clash/README.md) | passed |
| 11 | [an end before the start is refused before the form can be sent](calendar/11-an-end-before-the-start-is-refused-before-the-form-can-be-sent/use-case.mmd) | `calendar.spec.ts` | 77 | [13 kept](calendar/11-an-end-before-the-start-is-refused-before-the-form-can-be-sent/README.md) | passed |
| 12 | [an existing appointment can be edited without clashing with itself](calendar/12-an-existing-appointment-can-be-edited-without-clashing-with-itself/use-case.mmd) | `calendar.spec.ts` | 104 | [17 kept](calendar/12-an-existing-appointment-can-be-edited-without-clashing-with-itself/README.md) | passed |
| 13 | [a company and a contact can be created and linked](contacts/13-a-company-and-a-contact-can-be-created-and-linked/use-case.mmd) | `contacts.spec.ts` | 123 | [17 kept](contacts/13-a-company-and-a-contact-can-be-created-and-linked/README.md) | passed |
| 14 | [a contact with no name is refused with the message on the field](contacts/14-a-contact-with-no-name-is-refused-with-the-message-on-the-field/use-case.mmd) | `contacts.spec.ts` | 83 | [15 kept](contacts/14-a-contact-with-no-name-is-refused-with-the-message-on-the-field/README.md) | passed |
| 15 | [search narrows the list down to the matching contact](contacts/15-search-narrows-the-list-down-to-the-matching-contact/use-case.mmd) | `contacts.spec.ts` | 104 | [15 kept](contacts/15-search-narrows-the-list-down-to-the-matching-contact/README.md) | passed |
| 16 | [an activity logged on a contact appears in its history](contacts/16-an-activity-logged-on-a-contact-appears-in-its-history/use-case.mmd) | `contacts.spec.ts` | 128 | [20 kept](contacts/16-an-activity-logged-on-a-contact-appears-in-its-history/README.md) | passed |
| 17 | [deleting a contact asks first and then removes it](contacts/17-deleting-a-contact-asks-first-and-then-removes-it/use-case.mmd) | `contacts.spec.ts` | 99 | [16 kept](contacts/17-deleting-a-contact-asks-first-and-then-removes-it/README.md) | passed |
| 18 | [a deal moves through the pipeline and disappears once it is won](deals-and-tasks/18-a-deal-moves-through-the-pipeline-and-disappears-once-it-is-won/use-case.mmd) | `deals-and-tasks.spec.ts` | 118 | [16 kept](deals-and-tasks/18-a-deal-moves-through-the-pipeline-and-disappears-once-it-is-won/README.md) | passed |
| 19 | [a negative amount is rejected with the message on the field](deals-and-tasks/19-a-negative-amount-is-rejected-with-the-message-on-the-field/use-case.mmd) | `deals-and-tasks.spec.ts` | 86 | [15 kept](deals-and-tasks/19-a-negative-amount-is-rejected-with-the-message-on-the-field/README.md) | passed |
| 20 | [an overdue to-do is flagged and can be ticked off](deals-and-tasks/20-an-overdue-to-do-is-flagged-and-can-be-ticked-off/use-case.mmd) | `deals-and-tasks.spec.ts` | 110 | [16 kept](deals-and-tasks/20-an-overdue-to-do-is-flagged-and-can-be-ticked-off/README.md) | passed |
| 21 | [the overview counts what was entered and lists what is overdue](deals-and-tasks/21-the-overview-counts-what-was-entered-and-lists-what-is-overdue/use-case.mmd) | `deals-and-tasks.spec.ts` | 304 | [21 kept](deals-and-tasks/21-the-overview-counts-what-was-entered-and-lists-what-is-overdue/README.md) | recorded up to the assertion it failed on — it expects data that a spec earlier in the suite leaves behind |
| 22 | [the interface switches to German and stays there after a reload](i18n-and-users/22-the-interface-switches-to-german-and-stays-there-after-a-reload/use-case.mmd) | `i18n-and-users.spec.ts` | 115 | [15 kept](i18n-and-users/22-the-interface-switches-to-german-and-stays-there-after-a-reload/README.md) | passed |
| 23 | [an administrator adds a user who then has to choose their own password](i18n-and-users/23-an-administrator-adds-a-user-who-then-has-to-choose-their-own-password/use-case.mmd) | `i18n-and-users.spec.ts` | 133 | [14 kept](i18n-and-users/23-an-administrator-adds-a-user-who-then-has-to-choose-their-own-password/README.md) | passed |
| 24 | [a bookmarked page opens the application instead of a blank 404](i18n-and-users/24-a-bookmarked-page-opens-the-application-instead-of-a-blank-404/use-case.mmd) | `i18n-and-users.spec.ts` | 164 | [17 kept](i18n-and-users/24-a-bookmarked-page-opens-the-application-instead-of-a-blank-404/README.md) | passed |
| 25 | [a wrong password is refused and the session stays signed out](i18n-and-users/25-a-wrong-password-is-refused-and-the-session-stays-signed-out/use-case.mmd) | `i18n-and-users.spec.ts` | 49 | [13 kept](i18n-and-users/25-a-wrong-password-is-refused-and-the-session-stays-signed-out/README.md) | passed |
| 26 | [signing out returns to the login screen and protects the pages again](i18n-and-users/26-signing-out-returns-to-the-login-screen-and-protects-the-pages-again/use-case.mmd) | `i18n-and-users.spec.ts` | 79 | [13 kept](i18n-and-users/26-signing-out-returns-to-the-login-screen-and-protects-the-pages-again/README.md) | passed |
