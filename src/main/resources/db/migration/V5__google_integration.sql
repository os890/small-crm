-- Copyright 2026 the Small CRM authors.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Optional Google integration: signing in with a Google account that an administrator has
-- already created here, and keeping contacts, appointments and to-dos in step with it.
--
-- Everything in this migration is inert until somebody connects an account. An installation
-- that never does carries three empty tables and four unused columns.

-- One connected Google account per user. Deliberately not a place accounts can be created
-- from: the row only ever appears for a user that already exists.
CREATE TABLE google_account
(
    user_id       BIGINT                      NOT NULL PRIMARY KEY,
    -- Google's stable identifier for the person. The e-mail address is not stable — people
    -- rename them — so the subject is what a sign-in is matched on.
    subject       VARCHAR(255)                NOT NULL,
    email         VARCHAR(320)                NOT NULL,
    -- Encrypted with the key in SMALLCRM_TOKEN_KEY, never stored in clear. A refresh token is
    -- a live credential to somebody's whole Google account, which is a good deal more
    -- dangerous than anything else in this database.
    refreshToken  VARCHAR(2000)               NOT NULL,
    accessToken   VARCHAR(4000),
    accessExpires TIMESTAMP(6) WITH TIME ZONE,
    -- What the user actually consented to, which is not necessarily what was asked for.
    scopes        VARCHAR(1000)               NOT NULL,
    connectedAt   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version       BIGINT DEFAULT 0            NOT NULL,
    updatedAt     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_google_account_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uk_google_account_subject UNIQUE (subject)
);

-- Where each resource got to, so a sync asks Google for what changed rather than for
-- everything. Google hands back an opaque token per resource; it expires, and when it does the
-- only remedy is a full pass, which is why the failure is recorded rather than retried blindly.
CREATE TABLE google_sync_state
(
    user_id     BIGINT                      NOT NULL,
    resource    VARCHAR(20)                 NOT NULL,
    syncToken   VARCHAR(4000),
    lastRunAt   TIMESTAMP(6) WITH TIME ZONE,
    lastOkAt    TIMESTAMP(6) WITH TIME ZONE,
    lastError   VARCHAR(1000),
    -- Counted rather than merely logged, so "it has been failing since March" is answerable.
    failures    INTEGER DEFAULT 0           NOT NULL,
    version     BIGINT DEFAULT 0            NOT NULL,
    updatedAt   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_google_sync_state PRIMARY KEY (user_id, resource),
    CONSTRAINT fk_google_sync_state_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

-- Contacts and tasks gain what appointments were given at the start: somewhere to remember
-- which Google record they are, and how fresh that knowledge is.
ALTER TABLE contact ADD COLUMN externalId VARCHAR(200);
ALTER TABLE contact ADD COLUMN externalEtag VARCHAR(200);
ALTER TABLE contact ADD COLUMN lastSyncedAt TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE crm_task ADD COLUMN externalId VARCHAR(200);
ALTER TABLE crm_task ADD COLUMN externalEtag VARCHAR(200);
ALTER TABLE crm_task ADD COLUMN lastSyncedAt TIMESTAMP(6) WITH TIME ZONE;

-- Google Tasks belong to a list, and the list a to-do came from has to be remembered to write
-- it back to the right one.
ALTER TABLE crm_task ADD COLUMN externalListId VARCHAR(200);

-- A sync looks records up by their Google id constantly; without these it is a table scan per
-- record, which for the activity of a whole address book is the difference between seconds and
-- minutes.
CREATE UNIQUE INDEX idx_contact_external ON contact (externalId);
CREATE UNIQUE INDEX idx_crm_task_external ON crm_task (externalId);
CREATE UNIQUE INDEX idx_appointment_external ON appointment (externalEventId);
