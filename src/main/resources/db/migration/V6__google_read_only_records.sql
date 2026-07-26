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

-- Records that came from Google carrying something this CRM cannot hold.
--
-- Google's model is richer than this one. A weekly meeting has a recurrence rule and an
-- Appointment does not; a person can have six e-mail addresses and a Contact has one. Pulling
-- such a record in and later writing it back would quietly flatten the series, or drop five
-- addresses, in the user's own Google account — data this application never owned and has no
-- business destroying.
--
-- So they are pulled in and shown, and marked here. The API refuses to change them and the
-- interface says why: that record is managed in Google. Everything simple enough to represent
-- faithfully stays fully two-way.
ALTER TABLE contact ADD COLUMN externalReadOnly BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE crm_task ADD COLUMN externalReadOnly BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE appointment ADD COLUMN externalReadOnly BOOLEAN DEFAULT FALSE NOT NULL;
