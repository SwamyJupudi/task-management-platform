-- V10: the admin panel.
--
-- Three things, and nothing else: two permissions, one change to an existing
-- table, and three indexes. Phase nine adds no table and no column, because an
-- admin panel is composition over what phases two to eight already store plus
-- four account verbs that write columns that already exist.
--
-- THE TWO STANDING OBLIGATIONS, AND HOW THIS FILE MEETS THEM:
--
--   1. Map every new permission to SUPER_ADMIN in this same file. There is no
--      bypass branch for the platform administrator anywhere in the
--      authorization path, so a permission added without that row is one it
--      silently does not hold. Met below.
--
--   2. Backfill the workspace roles that already exist. DELIBERATELY NOT DONE
--      HERE, and this is the third distinct answer that obligation has had:
--
--        V4-V7  backfilled by role slug, because the codes named work that
--               workspace roles do.
--        V8-V9  added no permission at all, so there was nothing to backfill.
--        V10    adds permissions that NO WORKSPACE ROLE SHOULD EVER HOLD.
--
--      Both codes below name platform administration. Reading figures across
--      every workspace, and granting somebody the platform role, are not things
--      an administrator of one workspace has any business doing, and
--      @perm.onPlatform never consults workspace membership anyway, so a
--      workspace grant would gate nothing while looking as though it did.
--
--      The precedent already exists: workspace:delete has sat in the catalog
--      since V3, mapped to SUPER_ADMIN, granted to no workspace role, because
--      removing a workspace is platform administration. These two are the same
--      shape.
--
--      SystemRole is therefore unchanged, and WorkspaceRoleGrantsIT stays green
--      by holding the two halves of a pair that both stayed still.

-- ---------------------------------------------------------------------------
-- permissions
--
-- Two codes. The table's composition check requires code = resource || ':' ||
-- action, so the resource of the second is 'platform_role' rather than 'user'.
--
-- platform_role:assign is deliberately NOT folded into user:update. Promoting
-- somebody to platform administrator is the highest-privilege operation the
-- platform has, and it must be separately nameable so that a future custom
-- platform role can be given account administration without being given the
-- ability to mint its own peers. Editing a surname and minting an administrator
-- are not the same capability and must not share a code.
--
-- admin:read_system is not workspace:read under another name. workspace:read at
-- platform scope means "list the workspaces"; this means "count what is inside
-- all of them". It is not project:read_any either, which is a WORKSPACE-scoped
-- grant that three seeded roles can hold.
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, resource, action, description) VALUES
    ('admin:read_system',    'admin',         'read_system', 'Read platform-wide statistics, the cross-workspace project overview, and the platform audit trail'),
    ('platform_role:assign', 'platform_role', 'assign',      'Grant or revoke the platform administrator role');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug = 'SUPER_ADMIN'
  AND r.scope = 'PLATFORM'
  AND p.code IN ('admin:read_system', 'platform_role:assign');

-- ---------------------------------------------------------------------------
-- activity_logs: platform actions become recordable
--
-- This is the load-bearing change in the migration, and it discharges a promise
-- architecture.md has carried since phase two. Of SUPER_ADMIN it says: "Every
-- action it takes is audited from the phase that adds auditing." Phase six
-- added auditing and could not meet that for platform actions, because
-- workspace_id was NOT NULL and no platform action happens inside a workspace.
-- Nothing checked, because no platform action had an endpoint. Phase nine gives
-- it several, so phase nine is where the promise falls due.
--
-- ONE AUDIT TRAIL, NOT TWO. A separate admin_audit_logs table would mean two
-- answers to "what happened to this account", a second append-only trigger, a
-- second reader, and a permanent question about which one an auditor should
-- believe.
--
-- A platform row carries workspace_id = NULL. Every existing query filters by
-- workspace_id, so those rows are invisible to workspace browsing without one
-- line of application code changing, and the platform browse asks for
-- workspace_id IS NULL. That invariant is now load-bearing and is recorded on
-- ActivityLogRepository beside the methods that rely on it.
--
-- The ON DELETE CASCADE is untouched and still applies to rows that do name a
-- workspace. The append-only trigger is untouched and unaffected: it is BEFORE
-- UPDATE OR DELETE FOR EACH ROW, so DDL does not fire it. AdminSchemaIT proves
-- that rather than assuming it.
-- ---------------------------------------------------------------------------
ALTER TABLE activity_logs ALTER COLUMN workspace_id DROP NOT NULL;

-- Dropped and recreated rather than widened: PostgreSQL has no ALTER CONSTRAINT
-- for a check predicate. The recreation validates every existing row, which is
-- a scan this table can afford today. If it is ever large at deployment time,
-- the replacement is ADD CONSTRAINT ... NOT VALID followed by a separate
-- VALIDATE CONSTRAINT, which takes a weaker lock.
--
-- USER and ROLE join the seven that were there. WORKSPACE was already present
-- and unwritten, put there by V7 precisely so a later phase would not have to
-- widen the constraint for it.
ALTER TABLE activity_logs DROP CONSTRAINT activity_logs_entity_type_check;
ALTER TABLE activity_logs ADD CONSTRAINT activity_logs_entity_type_check
    CHECK (entity_type IN ('WORKSPACE', 'TEAM', 'PROJECT', 'TASK', 'SUBTASK',
                           'COMMENT', 'ATTACHMENT', 'USER', 'ROLE'));

-- ---------------------------------------------------------------------------
-- indexes
--
-- Two, and each is justified by a query this phase actually writes. Phase
-- eight's rule applies unchanged: shipping an index nothing queries is worse
-- than not shipping one, and so is shipping one that already exists.
--
-- Deliberately absent: an index for each count(*) on projects, tasks, teams and
-- attachments. A count over a whole table with no predicate cannot be usefully
-- narrowed by an index, and those tables already carry partial deleted_at IS
-- NULL indexes that the planner will reach for if it reaches for anything.
-- ---------------------------------------------------------------------------

-- The platform audit browse. activity_logs_workspace_created_idx from V7 leads
-- with workspace_id, so it cannot serve an IS NULL scan ordered by time without
-- reading every platform row it finds. Partial on the predicate, this one holds
-- only those rows and is tiny beside the workspace history.
CREATE INDEX activity_logs_platform_created_idx
    ON activity_logs (created_at DESC) WHERE workspace_id IS NULL;

-- NOT created here: an index for the account status breakdown. V2 already made
-- users_status_idx as (status) WHERE deleted_at IS NULL, which is exactly what
-- that GROUP BY wants, so adding one would have been a duplicate under a second
-- name. Recorded rather than silently omitted, because the planning note for
-- this phase claimed the status filter had nothing behind it and it was wrong.

-- The locked-account count, and the locked filter on the account directory.
-- Very small: one row per account that has ever been locked and still carries
-- the timestamp. Not partial on deleted_at as well, because the predicate above
-- already makes it a fraction of the table and a second condition would stop it
-- serving a bare "who is locked" question.
CREATE INDEX users_locked_until_idx
    ON users (locked_until) WHERE locked_until IS NOT NULL;
