-- V1: database extensions only.
--
-- The foundation phase deliberately creates no domain tables. Those arrive
-- with the module that owns them, starting with the identity phase.
--
-- pgcrypto  : gen_random_uuid() for the UUID primary keys used by every
--             table in the approved data model.
-- citext    : case-insensitive text, required by the unique email column
--             on users so that two addresses differing only in case cannot
--             both be registered.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;
