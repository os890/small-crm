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

-- Converts the three enum columns from H2's native ENUM type to plain text.
--
-- Installations created before Flyway existed were built by Hibernate, which maps a
-- @Enumerated(STRING) field to a native H2 ENUM listing every constant. That means adding a
-- deal stage or an interaction type would need a schema change, and it ties the schema to H2.
-- The entities now pin these columns to VARCHAR, so a new constant is purely a code change.
--
-- On a fresh installation V1 already created these columns as VARCHAR and each statement below
-- is a no-op; on a baselined installation it performs the real conversion. The existing values
-- are the constant names, so the cast is lossless either way.

ALTER TABLE crm_task ALTER COLUMN priority SET DATA TYPE VARCHAR(10);
ALTER TABLE deal ALTER COLUMN stage SET DATA TYPE VARCHAR(20);
ALTER TABLE interaction ALTER COLUMN type SET DATA TYPE VARCHAR(20);
