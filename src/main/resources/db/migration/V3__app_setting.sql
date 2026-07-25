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

-- Settings an administrator can change from the interface, as opposed to the ones an operator
-- sets in application.properties. Kept as key/value so a new setting needs no migration.
--
-- The table deliberately stays out of the XML backups: it describes this installation rather
-- than the customer data, and restoring a backup should not silently change how the
-- installation behaves.

CREATE TABLE app_setting (
    setting_key   VARCHAR(100) NOT NULL PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL
);

-- How long automatic backups are kept, in days. Fourteen is the default the interface offers.
INSERT INTO app_setting (setting_key, setting_value) VALUES ('backup.retention-days', '14');
