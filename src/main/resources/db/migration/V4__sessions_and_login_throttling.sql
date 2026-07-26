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

-- Server side sessions, replacing the self-contained encrypted cookie.
--
-- The previous cookie carried "<expiry>:<username>" encrypted with a key from the
-- configuration, and nothing else. That has three consequences this table removes:
-- signing out could not actually end a session anywhere but in the user's own browser,
-- changing a password did not invalidate sessions already issued, and anyone who learned
-- the key could mint a cookie for any account without touching the login endpoint.
--
-- The cookie now holds a random token. Only its SHA-256 is stored, so a leaked database
-- does not hand over usable sessions.

CREATE TABLE app_session (
    token_hash VARCHAR(64)                  NOT NULL PRIMARY KEY,
    user_id    BIGINT                       NOT NULL,
    createdAt  TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    lastSeenAt TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    -- Absolute end of life, independent of activity, so a stolen token cannot be kept
    -- alive for ever by polling.
    expiresAt  TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT fk_app_session_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

-- Revoking every session of one account, on password change or deactivation.
CREATE INDEX idx_app_session_user ON app_session (user_id);
-- Sweeping expired rows.
CREATE INDEX idx_app_session_expires ON app_session (expiresAt);

-- Login throttling. Without these, the only brake on guessing is the cost of bcrypt.
ALTER TABLE app_user ADD COLUMN failedLoginCount INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE app_user ADD COLUMN lockedUntil TIMESTAMP(6) WITH TIME ZONE;

-- Optimistic locking and an update timestamp for accounts, which the other tables already
-- had. Without them two administrators editing the same account silently overwrite.
ALTER TABLE app_user ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE app_user ADD COLUMN updatedAt TIMESTAMP(6) WITH TIME ZONE;
UPDATE app_user SET updatedAt = createdAt WHERE updatedAt IS NULL;
ALTER TABLE app_user ALTER COLUMN updatedAt SET NOT NULL;

-- Indexes for the two sorts that were full table scans.
CREATE INDEX idx_interaction_occurred ON interaction (occurredAt);
CREATE INDEX idx_crm_task_due ON crm_task (dueDate);
