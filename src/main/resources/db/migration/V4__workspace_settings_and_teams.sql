-- V4: workspace settings and lifecycle, and teams.
--
-- Phase two created workspaces with only the columns roles and memberships had
-- to point at. This file gives the table the settings and the lifecycle that
-- were deferred, and adds the two tables the teams module owns.
--
-- OBLIGATION, restated from V3: every permission added here is mapped to
-- SUPER_ADMIN in this same file. There is deliberately no bypass branch for the
-- platform administrator anywhere in the authorization path, so a permission
-- added without that mapping is one it silently does not hold.

-- ---------------------------------------------------------------------------
-- workspaces: settings and lifecycle
--
-- Settings are columns on the table rather than a one-to-one settings table.
-- There are four of them, they are read on every request that renders a
-- workspace, and a second table would buy a join and nothing else. If the set
-- grows to where that stops being true, splitting it is a later migration.
-- ---------------------------------------------------------------------------
ALTER TABLE workspaces
    ADD COLUMN description         text,
    -- The zone deadlines and calendars are rendered in. Stored as an IANA name;
    -- the application validates it against the JDK's zone database, because a
    -- check constraint here would freeze that list into the schema.
    ADD COLUMN timezone            text        NOT NULL DEFAULT 'UTC',
    -- The role an invitation uses when it does not name one.
    ADD COLUMN default_role_id     uuid,
    ADD COLUMN archived_at         timestamptz,
    ADD COLUMN archived_by_user_id uuid        REFERENCES users (id);

ALTER TABLE workspaces
    ADD CONSTRAINT workspaces_description_length_check
        CHECK (description IS NULL OR length(description) <= 500),
    ADD CONSTRAINT workspaces_timezone_length_check
        CHECK (length(btrim(timezone)) BETWEEN 1 AND 64),
    -- The status column and the timestamp cannot disagree about whether the
    -- workspace is archived. Without this, one of the two would eventually be
    -- read by something that trusted the other.
    ADD CONSTRAINT workspaces_archived_consistency_check
        CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL)),
    -- The same composite-key trick the rest of the schema uses: the default role
    -- must belong to this workspace, which the database refuses rather than a
    -- service being trusted to check. A platform role has a null workspace and
    -- so cannot satisfy this key at all.
    ADD CONSTRAINT workspaces_default_role_in_workspace_fkey
        FOREIGN KEY (default_role_id, id) REFERENCES roles (id, workspace_id);

CREATE INDEX workspaces_default_role_id_idx ON workspaces (default_role_id);
CREATE INDEX workspaces_archived_by_user_id_idx ON workspaces (archived_by_user_id);

-- ---------------------------------------------------------------------------
-- teams
--
-- lead_user_id is a single column because the requirements say assign a team
-- lead, in the singular.
--
-- That the lead must be a member of the workspace is enforced by the database,
-- not by a service check: the pair (workspace_id, lead_user_id) is a foreign key
-- into workspace_members, which already carries the unique constraint it needs.
-- Removing somebody from a workspace while they lead a team is therefore a write
-- PostgreSQL refuses, and the teams module has to clear the lead first.
-- ---------------------------------------------------------------------------
CREATE TABLE teams (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id       uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name               text        NOT NULL,
    description        text,
    lead_user_id       uuid,
    status             text        NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id uuid        NOT NULL REFERENCES users (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz,

    CONSTRAINT teams_status_check
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT teams_name_length_check
        CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    CONSTRAINT teams_description_length_check
        CHECK (description IS NULL OR length(description) <= 500),
    CONSTRAINT teams_lead_is_workspace_member_fkey
        FOREIGN KEY (workspace_id, lead_user_id) REFERENCES workspace_members (workspace_id, user_id)
);

-- Redundant on its own, and the target of the composite key on team_members. It
-- is what lets the database refuse a team member recorded against the wrong
-- workspace.
ALTER TABLE teams
    ADD CONSTRAINT teams_id_workspace_unique UNIQUE (id, workspace_id);

-- Partial, so a deleted team does not reserve its name for ever, and folded, so
-- two teams cannot differ only in case or in surrounding space.
CREATE UNIQUE INDEX teams_workspace_name_unique_idx
    ON teams (workspace_id, lower(btrim(name))) WHERE deleted_at IS NULL;
CREATE INDEX teams_workspace_id_idx ON teams (workspace_id) WHERE deleted_at IS NULL;
CREATE INDEX teams_lead_user_id_idx ON teams (lead_user_id);
CREATE INDEX teams_created_by_user_id_idx ON teams (created_by_user_id);

-- ---------------------------------------------------------------------------
-- team_members
--
-- workspace_id is carried here for the same reason it is carried on
-- workspace_members: it turns two rules into foreign keys. The team must belong
-- to this workspace, and the person must be a member of it. Neither is a check
-- the service layer is trusted to remember.
--
-- No deleted_at. Soft deletion never applies to a join table.
-- ---------------------------------------------------------------------------
CREATE TABLE team_members (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id          uuid        NOT NULL,
    workspace_id     uuid        NOT NULL,
    user_id          uuid        NOT NULL,
    added_by_user_id uuid        REFERENCES users (id),
    joined_at        timestamptz NOT NULL DEFAULT now(),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT team_members_team_user_unique UNIQUE (team_id, user_id),
    CONSTRAINT team_members_team_in_workspace_fkey
        FOREIGN KEY (team_id, workspace_id) REFERENCES teams (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT team_members_user_in_workspace_fkey
        FOREIGN KEY (workspace_id, user_id) REFERENCES workspace_members (workspace_id, user_id)
);

CREATE INDEX team_members_team_id_idx ON team_members (team_id);
CREATE INDEX team_members_user_id_idx ON team_members (user_id);
CREATE INDEX team_members_workspace_id_idx ON team_members (workspace_id);
CREATE INDEX team_members_added_by_user_id_idx ON team_members (added_by_user_id);

-- ---------------------------------------------------------------------------
-- the permissions this phase implements
--
-- team:manage_any is the workspace-wide grant described in architecture.md. A
-- team lead holds team:update and team:manage_members but not this one, so the
-- scope layer narrows them to the teams they actually lead. An administrator
-- holds it and reaches every team in the workspace.
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, resource, action, description) VALUES
    ('workspace:archive',   'workspace', 'archive',        'Archive or restore a workspace'),

    ('team:read',           'team',      'read',           'View teams and their members'),
    ('team:create',         'team',      'create',         'Create a team'),
    ('team:update',         'team',      'update',         'Edit a team, including archiving it'),
    ('team:delete',         'team',      'delete',         'Remove a team'),
    ('team:manage_members', 'team',      'manage_members', 'Add or remove team members and assign the lead'),
    ('team:manage_any',     'team',      'manage_any',     'Act on any team in the workspace, not only teams led');

-- The obligation from V3, discharged for the seven codes above.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug = 'SUPER_ADMIN'
  AND r.scope = 'PLATFORM'
  AND p.code IN (
      'workspace:archive',
      'team:read', 'team:create', 'team:update', 'team:delete',
      'team:manage_members', 'team:manage_any');

-- ---------------------------------------------------------------------------
-- backfill for workspaces that already exist
--
-- Workspace roles are written in code when a workspace is created, so a
-- workspace created before this migration holds only the phase-two grants. New
-- workspaces get the same sets from SystemRole; these two lists must agree, and
-- WorkspaceRoleGrantsIT holds them together.
--
-- A database with no workspaces yet, which is every test run, applies this as a
-- no-op.
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = ANY (
    CASE r.slug
        WHEN 'ADMIN' THEN ARRAY[
            'workspace:archive',
            'team:read', 'team:create', 'team:update', 'team:delete',
            'team:manage_members', 'team:manage_any']
        WHEN 'TEAM_LEAD' THEN ARRAY[
            'team:read', 'team:update', 'team:manage_members']
        WHEN 'EMPLOYEE' THEN ARRAY[
            'team:read']
        ELSE ARRAY[]::text[]
    END)
WHERE r.scope = 'WORKSPACE'
ON CONFLICT (role_id, permission_id) DO NOTHING;
