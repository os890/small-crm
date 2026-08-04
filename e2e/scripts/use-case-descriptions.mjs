/*
 * Copyright 2026 the Small CRM authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * What each use-case does, in prose, for the document `record-flows.mjs` writes.
 *
 * Keyed by `<spec file>#<test title>` rather than by directory, so adding a test in front of
 * another one does not silently move a description to the wrong use-case. A use-case with no
 * entry here is listed with its title alone - the diagrams are what matter, and the document
 * says which description is missing rather than inventing one.
 */
export const USE_CASE_DESCRIPTIONS = {
  'auth.setup.ts#first start forces the administrator to pick a new password':
    'The very first start of a fresh installation, which is also what a new owner walks through: ' +
    'signing in with the bootstrap password lands on the forced password change, a repetition ' +
    'that does not match is refused, and the chosen password opens the overview.',

  'appearance.spec.ts#the stylesheet is applied, not merely served':
    'Loads the login screen signed out and checks computed colours and sizes, so a stylesheet ' +
    'that is served and parsed but applied to nothing fails the build - which is how it once ' +
    'shipped, with the whole suite green.',

  'appearance.spec.ts#the styles survive into the signed-in application':
    'Opens a feature screen of the signed-in shell and checks that the component styles arrive ' +
    'there too, not only the global sheet.',

  'backups.spec.ts#a backup can be created, downloaded and restored from the folder':
    'Creates a company, takes a backup, downloads it to confirm the company is really in it, ' +
    'adds a second company, and restores the backup: the later company is gone, the earlier one ' +
    'is back, and a before-restore copy of what was replaced has appeared in the folder.',

  'backups.spec.ts#an unwanted restore can be undone with the before-restore file':
    'Restores an older backup on purpose and then restores the before-restore copy the ' +
    'application wrote by itself, which brings the newer state back.',

  'backups.spec.ts#the retention period can be changed from the screen':
    'Changes the retention period from the backup screen, reloads to prove it was persisted, and ' +
    'puts the default back for the specs that follow.',

  'backups.spec.ts#a file that is not a backup is refused without losing any data':
    'Uploads a file that is not a backup at all: the restore is refused with a message, and the ' +
    'data that was there is still there afterwards.',

  'backups.spec.ts#a plain user cannot reach the backup screen':
    'An administrator creates a plain user, who signs in, chooses their own password, and finds ' +
    'neither the backup navigation entry nor the screen behind its URL.',

  'calendar.spec.ts#the double booking guard blocks a taken slot and can be overridden':
    'Books a free slot, is warned and then refused on an overlapping one, moves that appointment ' +
    'to a free slot, and finally books a deliberate parallel appointment through the override.',

  'calendar.spec.ts#back to back appointments are not treated as a clash':
    'An appointment starting exactly when the previous one ends is booked with no conflict ' +
    'warning: the guard compares intervals, not endpoints.',

  'calendar.spec.ts#an end before the start is refused before the form can be sent':
    'An end time before the start is caught in the form - the message appears and the save button ' +
    'stays disabled, so the request is never made.',

  'calendar.spec.ts#an existing appointment can be edited without clashing with itself':
    'Edits an existing appointment: the conflict check leaves the appointment being edited out, ' +
    'so it does not clash with itself.',

  'contacts.spec.ts#a company and a contact can be created and linked':
    'Creates a company, then a contact whose company is picked through the typeahead that looks ' +
    'up what was typed, and checks the row shows the company and both tags.',

  'contacts.spec.ts#a contact with no name is refused with the message on the field':
    'Saves a contact without a last name: the validation message from the server lands on the ' +
    'field and the dialog stays open, so nothing that was typed is lost.',

  'contacts.spec.ts#search narrows the list down to the matching contact':
    'Creates two contacts and searches for one of them; exactly one row comes back.',

  'contacts.spec.ts#an activity logged on a contact appears in its history':
    'Creates a contact, opens it, logs an interaction on it and finds it in the history.',

  'contacts.spec.ts#deleting a contact asks first and then removes it':
    'Deleting a contact asks first, naming the contact in the prompt, and the row is gone once ' +
    'that is confirmed.',

  'deals-and-tasks.spec.ts#a deal moves through the pipeline and disappears once it is won':
    'Creates a deal, moves it from lead to proposal and then to won - which takes it out of the ' +
    'open pipeline until the open-only filter is turned off.',

  'deals-and-tasks.spec.ts#a negative amount is rejected with the message on the field':
    'A deal with a negative amount is refused, with the validation message in the dialog.',

  'deals-and-tasks.spec.ts#an overdue to-do is flagged and can be ticked off':
    'A to-do that was due two days ago is flagged as overdue; ticking it off takes it out of the ' +
    'open list, and it comes back when the filter is turned off.',

  'deals-and-tasks.spec.ts#the overview counts what was entered and lists what is overdue':
    'Enters an overdue to-do and checks the overview lists it among the overdue ones and counts ' +
    'what the installation holds.',

  'i18n-and-users.spec.ts#the interface switches to German and stays there after a reload':
    'Switches the interface to German, reloads to prove the choice survives, and checks that a ' +
    'validation message from the server comes back in German as well.',

  'i18n-and-users.spec.ts#an administrator adds a user who then has to choose their own password':
    'An administrator creates a user with an initial password; in a browser of its own that user ' +
    'signs in, is forced to choose their own password, and gets the CRM but not the ' +
    'administration area.',

  'i18n-and-users.spec.ts#a bookmarked page opens the application instead of a blank 404':
    'Opens four deep links and an unknown path directly: every one of them answers 200 with the ' +
    'application, so a bookmark, a reload and a deep link all work.',

  'i18n-and-users.spec.ts#a wrong password is refused and the session stays signed out':
    'A wrong password shows the login error and leaves the session signed out.',

  'i18n-and-users.spec.ts#signing out returns to the login screen and protects the pages again':
    'Signing out returns to the login screen, and a protected page opened afterwards shows the ' +
    'login form instead of the data.',
};
