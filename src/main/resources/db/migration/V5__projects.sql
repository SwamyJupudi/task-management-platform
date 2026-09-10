-- V5: projects, project membership, and the workspace label catalog.
--
-- OBLIGATION, restated from V3 and V4: every permission added here is mapped to
-- SUPER_ADMIN in this same file, and backfilled onto the workspace roles that
-- already exist. There is no bypass branch for the platform administrator, and
-- workspace role rows are written in code when a workspace is created, so a
-- permission added without both statements is one somebody silently lacks.

-- ---------------------------------------------------------------------------
-- labels
--
-- One workspace-scoped catalog, shared by projects and tasks. The requirements
-- say tags on projects and labels on tasks; two near-identical tables would earn
-- nothing, so this is one table and phase five adds task_labels beside the
-- project_labels join below.
-- ---------------------------------------------------------------------------
CREATE TABLE labels (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT labels_name_length_check
        CHECK (length(btrim(name)) BETWEEN 1 AND 40)
);

-- Folded, so a workspace cannot end up with "Backend" and "backend" as two
-- different tags. Not partial: labels are not soft deleted.
CREATE UNIQUE INDEX labels_workspace_name_unique_idx
    ON labels (workspace_id, lower(btrim(name)));
CREATE INDEX labels_workspace_id_idx ON labels (workspace_id);

-- The target of the composite key on project_labels, so a project cannot be
-- tagged with another workspace's label.
ALTER TABLE labels
    ADD CONSTRAINT labels_id_workspace_unique UNIQUE (id, workspace_id);

-- ---------------------------------------------------------------------------
-- projects
--
-- owner_user_id and team_id are both nullable, and both are keyed to this
-- workspace by the database rather than by a service check.
--
-- Nullable for the same reason the team lead is: a project between owners is an
-- ordinary state, and NOT NULL would mean an owner could never leave the
-- workspace. The pair (workspace_id, owner_user_id) keys into workspace_members,
-- so an owner from outside the workspace is a write PostgreSQL refuses, and
-- removing that person is refused until the project stands them down.
--
-- progress is stored and, in this phase, not maintained. The requirements list
-- it as a project field; the rule that derives it needs tasks, which arrive in
-- phase five. It is zero until then rather than null, so no client has to model
-- "unknown" for a value that is about to become derived.
-- ---------------------------------------------------------------------------
CREATE TABLE projects (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id       uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    key                text        NOT NULL,
    name               text        NOT NULL,
    description        text,
    owner_user_id      uuid,
    team_id            uuid,
    status             text        NOT NULL DEFAULT 'PLANNING',
    priority           text        NOT NULL DEFAULT 'MEDIUM',
    start_date         date,
    end_date           date,
    progress           integer     NOT NULL DEFAULT 0,
    created_by_user_id uuid        NOT NULL REFERENCES users (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz,

    CONSTRAINT projects_status_check
        CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT projects_priority_check
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT projects_key_format_check
        CHECK (key ~ '^[A-Z][A-Z0-9]{1,9}$'),
    CONSTRAINT projects_name_length_check
        CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    CONSTRAINT projects_description_length_check
        CHECK (description IS NULL OR length(description) <= 2000),
    -- A project that ends before it starts is a typo, not a plan.
    CONSTRAINT projects_date_order_check
        CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date),
    CONSTRAINT projects_progress_range_check
        CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT projects_owner_is_workspace_member_fkey
        FOREIGN KEY (workspace_id, owner_user_id) REFERENCES workspace_members (workspace_id, user_id),
    CONSTRAINT projects_team_in_workspace_fkey
        FOREIGN KEY (team_id, workspace_id) REFERENCES teams (id, workspace_id)
);

-- Redundant on its own, and the target of the composite keys on the two join
-- tables below, exactly as roles and teams carry theirs.
ALTER TABLE projects
    ADD CONSTRAINT projects_id_workspace_unique UNIQUE (id, workspace_id);

-- Both partial, so a deleted project reserves neither its key nor its name.
CREATE UNIQUE INDEX projects_workspace_key_unique_idx
    ON projects (workspace_id, upper(btrim(key))) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX projects_workspace_name_unique_idx
    ON projects (workspace_id, lower(btrim(name))) WHERE deleted_at IS NULL;

CREATE INDEX projects_workspace_status_idx
    ON projects (workspace_id, status) WHERE deleted_at IS NULL;
CREATE INDEX projects_owner_user_id_idx ON projects (owner_user_id);
CREATE INDEX projects_team_id_idx ON projects (team_id);
CREATE INDEX projects_created_by_user_id_idx ON projects (created_by_user_id);

-- ---------------------------------------------------------------------------
-- project_members
--
-- No project-level role. The requirements describe none, and adding one would
-- be a second authorization model sitting beside the workspace roles.
--
-- workspace_id is carried for the same reason it is on team_members: it turns
-- two rules into foreign keys. The project must belong to this workspace, and
-- the person must be a member of it.
-- ---------------------------------------------------------------------------
CREATE TABLE project_members (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       uuid        NOT NULL,
    workspace_id     uuid        NOT NULL,
    user_id          uuid        NOT NULL,
    added_by_user_id uuid        REFERENCES users (id),
    joined_at        timestamptz NOT NULL DEFAULT now(),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT project_members_project_user_unique UNIQUE (project_id, user_id),
    CONSTRAINT project_members_project_in_workspace_fkey
        FOREIGN KEY (project_id, workspace_id) REFERENCES projects (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT project_members_user_in_workspace_fkey
        FOREIGN KEY (workspace_id, user_id) REFERENCES workspace_members (workspace_id, user_id)
);

CREATE INDEX project_members_project_id_idx ON project_members (project_id);
CREATE INDEX project_members_user_id_idx ON project_members (user_id);
CREATE INDEX project_members_workspace_id_idx ON project_members (workspace_id);
CREATE INDEX project_members_added_by_user_id_idx ON project_members (added_by_user_id);

-- ---------------------------------------------------------------------------
-- project_labels
-- ---------------------------------------------------------------------------
CREATE TABLE project_labels (
    project_id   uuid        NOT NULL,
    label_id     uuid        NOT NULL,
    workspace_id uuid        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (project_id, label_id),
    CONSTRAINT project_labels_project_in_workspace_fkey
        FOREIGN KEY (project_id, workspace_id) REFERENCES projects (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT project_labels_label_in_workspace_fkey
        FOREIGN KEY (label_id, workspace_id) REFERENCES labels (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX project_labels_label_id_idx ON project_labels (label_id);
CREATE INDEX project_labels_workspace_id_idx ON project_labels (workspace_id);

-- ---------------------------------------------------------------------------
-- the permissions this phase implements
--
-- Two of the seven are scope grants rather than capabilities, and they are the
-- pair worth reading carefully.
--
-- project:read_any decides what a list returns. The requirements say an employee
-- views assigned projects, so without this grant visibility is the projects a
-- person belongs to, owns, or whose team they lead. It is the read half of the
-- scope layer, applied in the query.
--
-- project:manage_any widens update and member management from "the projects I
-- own or whose team I lead" to "every project here", exactly as team:manage_any
-- does for teams.
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, resource, action, description) VALUES
    ('project:read',            'project', 'read',            'View projects'),
    ('project:read_any',        'project', 'read_any',        'View every project in the workspace, not only assigned ones'),
    ('project:create',          'project', 'create',          'Create a project'),
    ('project:update',          'project', 'update',          'Edit a project, including its status'),
    ('project:delete',          'project', 'delete',          'Remove a project'),
    ('project:manage_members',  'project', 'manage_members',  'Add or remove project members and assign the owner'),
    ('project:manage_any',      'project', 'manage_any',      'Act on any project in the workspace, not only those owned or led');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug = 'SUPER_ADMIN'
  AND r.scope = 'PLATFORM'
  AND p.code IN (
      'project:read', 'project:read_any', 'project:create', 'project:update',
      'project:delete', 'project:manage_members', 'project:manage_any');

-- ---------------------------------------------------------------------------
-- backfill for workspaces that already exist
--
-- New workspaces get these same sets from SystemRole when their roles are
-- seeded. The two lists must agree, and WorkspaceRoleGrantsIT holds them
-- together. A database with no workspaces yet applies this as a no-op.
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = ANY (
    CASE r.slug
        WHEN 'ADMIN' THEN ARRAY[
            'project:read', 'project:read_any', 'project:create', 'project:update',
            'project:delete', 'project:manage_members', 'project:manage_any']
        WHEN 'TEAM_LEAD' THEN ARRAY[
            'project:read', 'project:update', 'project:manage_members']
        WHEN 'EMPLOYEE' THEN ARRAY[
            'project:read']
        ELSE ARRAY[]::text[]
    END)
WHERE r.scope = 'WORKSPACE'
ON CONFLICT (role_id, permission_id) DO NOTHING;
