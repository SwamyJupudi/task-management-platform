-- V6: tasks, subtasks, task dependencies, and the task half of the label catalog.
--
-- OBLIGATION, restated from V3, V4 and V5: every permission added here is mapped
-- to SUPER_ADMIN in this same file, and backfilled onto the workspace roles that
-- already exist. There is no bypass branch for the platform administrator, and
-- workspace role rows are written in code when a workspace is created, so a
-- permission added without both statements is one somebody silently lacks.

-- ---------------------------------------------------------------------------
-- project_task_counters
--
-- The numbering source for PROJECTKEY-1, PROJECTKEY-2, and the reason task
-- creation is safe under concurrency.
--
-- Allocation is one statement: an INSERT with ON CONFLICT DO UPDATE that
-- increments and RETURNs. Concurrent creators conflict on the primary key, block
-- on the row, and each leaves holding a distinct number. There is no read
-- followed by a write for two transactions to interleave inside, which is what
-- SELECT max(task_number) + 1 would have been.
--
-- The row is created on first use rather than seeded with the project, so the
-- projects V5 already created need no backfill and no event listener has to
-- remember to seed one.
--
-- The counter only ever moves forward. A deleted task's number is never handed
-- out again, because a link to PROJ-12 must not later resolve to a different
-- task. Projects will therefore show gaps in their numbering, which is the price
-- of a stable identifier and is paid deliberately.
-- ---------------------------------------------------------------------------
CREATE TABLE project_task_counters (
    project_id   uuid        PRIMARY KEY,
    workspace_id uuid        NOT NULL,
    next_number  integer     NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT project_task_counters_next_number_check
        CHECK (next_number >= 0),
    CONSTRAINT project_task_counters_project_in_workspace_fkey
        FOREIGN KEY (project_id, workspace_id) REFERENCES projects (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX project_task_counters_workspace_id_idx ON project_task_counters (workspace_id);

-- ---------------------------------------------------------------------------
-- tasks
--
-- Two composite keys carry the rules that matter, in the same style as
-- everything else in this schema.
--
-- The assignee keys into project_members, so somebody cannot be given a task in
-- a project they are not on. That is what makes the visibility rule complete:
-- "tasks I can see" and "tasks assigned to me" cannot diverge, because the
-- second is a subset of the first by construction rather than by a service
-- check. It has a consequence, and it is the same one teams and projects already
-- carry: removing somebody from a project is refused while they still hold a
-- task on it, so the tasks module stands them down first.
--
-- The reporter keys into workspace_members instead. An administrator may raise a
-- task on a project they are not a member of, so pinning the reporter to the
-- project would refuse an ordinary write. It is nullable for the reason the
-- project owner is: NOT NULL would mean the reporter could never leave the
-- workspace.
--
-- completed_at is held consistent with the status by a check, the same technique
-- workspaces.status and archived_at use, so the flag and the timestamp cannot
-- disagree.
-- ---------------------------------------------------------------------------
CREATE TABLE tasks (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id       uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id         uuid        NOT NULL,
    task_number        integer     NOT NULL,
    title              text        NOT NULL,
    description        text,
    assignee_user_id   uuid,
    reporter_user_id   uuid,
    status             text        NOT NULL DEFAULT 'TODO',
    priority           text        NOT NULL DEFAULT 'MEDIUM',
    start_date         date,
    due_date           date,
    estimated_minutes  integer,
    actual_minutes     integer,
    board_position     integer     NOT NULL DEFAULT 0,
    completed_at       timestamptz,
    created_by_user_id uuid        NOT NULL REFERENCES users (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz,

    CONSTRAINT tasks_status_check
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'REVIEW', 'DONE')),
    CONSTRAINT tasks_priority_check
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT tasks_task_number_check
        CHECK (task_number > 0),
    CONSTRAINT tasks_title_length_check
        CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    CONSTRAINT tasks_description_length_check
        CHECK (description IS NULL OR length(description) <= 10000),
    -- A task that is due before it starts is a typo, not a plan.
    CONSTRAINT tasks_date_order_check
        CHECK (start_date IS NULL OR due_date IS NULL OR due_date >= start_date),
    CONSTRAINT tasks_estimated_minutes_check
        CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0),
    CONSTRAINT tasks_actual_minutes_check
        CHECK (actual_minutes IS NULL OR actual_minutes >= 0),
    -- The flag and the timestamp say the same thing or the write is refused.
    CONSTRAINT tasks_completed_at_matches_status_check
        CHECK ((status = 'DONE') = (completed_at IS NOT NULL)),
    CONSTRAINT tasks_project_in_workspace_fkey
        FOREIGN KEY (project_id, workspace_id) REFERENCES projects (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT tasks_assignee_is_project_member_fkey
        FOREIGN KEY (project_id, assignee_user_id) REFERENCES project_members (project_id, user_id),
    CONSTRAINT tasks_reporter_is_workspace_member_fkey
        FOREIGN KEY (workspace_id, reporter_user_id) REFERENCES workspace_members (workspace_id, user_id)
);

-- Redundant on their own, and the targets of the composite keys on subtasks,
-- task_labels and task_dependencies below. Exactly as roles, teams, projects and
-- labels each carry theirs.
ALTER TABLE tasks
    ADD CONSTRAINT tasks_id_workspace_unique UNIQUE (id, workspace_id);
ALTER TABLE tasks
    ADD CONSTRAINT tasks_id_project_unique UNIQUE (id, project_id);

-- Deliberately NOT partial. Every other unique in this schema excludes deleted
-- rows so a name or a key becomes free again; this one must not, because a task
-- number is an identifier people put in links and messages.
ALTER TABLE tasks
    ADD CONSTRAINT tasks_project_number_unique UNIQUE (project_id, task_number);

CREATE INDEX tasks_project_status_idx
    ON tasks (project_id, status) WHERE deleted_at IS NULL;
CREATE INDEX tasks_assignee_status_idx
    ON tasks (assignee_user_id, status) WHERE deleted_at IS NULL;
CREATE INDEX tasks_workspace_due_date_idx
    ON tasks (workspace_id, due_date) WHERE deleted_at IS NULL;
CREATE INDEX tasks_workspace_project_idx
    ON tasks (workspace_id, project_id) WHERE deleted_at IS NULL;
CREATE INDEX tasks_reporter_user_id_idx ON tasks (reporter_user_id);
CREATE INDEX tasks_created_by_user_id_idx ON tasks (created_by_user_id);

-- ---------------------------------------------------------------------------
-- subtasks
--
-- A separate table rather than a self-referencing task, because the requirements
-- list SubTask as its own entity with a narrower field set: completion,
-- assignee, status and due date.
--
-- The requirements name completion and status both. Two independent fields would
-- be two representations of one fact that can disagree, so completion is
-- status = DONE, recorded with completed_at and held together by the same check
-- the tasks table uses.
--
-- project_id is carried so the assignee can be keyed to project_members exactly
-- as the parent task's is, and task_id is keyed twice, once to pin the project
-- and once to pin the workspace.
-- ---------------------------------------------------------------------------
CREATE TABLE subtasks (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id       uuid        NOT NULL,
    project_id         uuid        NOT NULL,
    task_id            uuid        NOT NULL,
    title              text        NOT NULL,
    assignee_user_id   uuid,
    status             text        NOT NULL DEFAULT 'TODO',
    due_date           date,
    position           integer     NOT NULL DEFAULT 0,
    completed_at       timestamptz,
    created_by_user_id uuid        NOT NULL REFERENCES users (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz,

    CONSTRAINT subtasks_status_check
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'REVIEW', 'DONE')),
    CONSTRAINT subtasks_title_length_check
        CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    CONSTRAINT subtasks_completed_at_matches_status_check
        CHECK ((status = 'DONE') = (completed_at IS NOT NULL)),
    CONSTRAINT subtasks_task_in_project_fkey
        FOREIGN KEY (task_id, project_id) REFERENCES tasks (id, project_id) ON DELETE CASCADE,
    CONSTRAINT subtasks_task_in_workspace_fkey
        FOREIGN KEY (task_id, workspace_id) REFERENCES tasks (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT subtasks_assignee_is_project_member_fkey
        FOREIGN KEY (project_id, assignee_user_id) REFERENCES project_members (project_id, user_id)
);

CREATE INDEX subtasks_task_id_idx
    ON subtasks (task_id) WHERE deleted_at IS NULL;
CREATE INDEX subtasks_assignee_status_idx
    ON subtasks (assignee_user_id, status) WHERE deleted_at IS NULL;
CREATE INDEX subtasks_workspace_due_date_idx
    ON subtasks (workspace_id, due_date) WHERE deleted_at IS NULL;
CREATE INDEX subtasks_project_id_idx ON subtasks (project_id);
CREATE INDEX subtasks_created_by_user_id_idx ON subtasks (created_by_user_id);

-- ---------------------------------------------------------------------------
-- task_labels
--
-- The other half of the catalog V5 created. One labels table serves projects and
-- tasks both; the requirements call them tags on a project and labels on a task,
-- but they are the same thing used twice.
-- ---------------------------------------------------------------------------
CREATE TABLE task_labels (
    task_id      uuid        NOT NULL,
    label_id     uuid        NOT NULL,
    workspace_id uuid        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (task_id, label_id),
    CONSTRAINT task_labels_task_in_workspace_fkey
        FOREIGN KEY (task_id, workspace_id) REFERENCES tasks (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT task_labels_label_in_workspace_fkey
        FOREIGN KEY (label_id, workspace_id) REFERENCES labels (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX task_labels_label_id_idx ON task_labels (label_id);
CREATE INDEX task_labels_workspace_id_idx ON task_labels (workspace_id);

-- ---------------------------------------------------------------------------
-- task_dependencies
--
-- One row means: task_id is blocked by depends_on_task_id.
--
-- A single blocking relationship, with no type column. The requirements name
-- TaskDependency as an entity and say nothing about kinds of dependency, and
-- inventing BLOCKS, RELATES and DUPLICATES would mean inventing semantics for
-- each of them.
--
-- Both ends are keyed to the same project, which does two things at once. It
-- makes a cross-workspace dependency unrepresentable, since the project pins the
-- workspace. And it closes an information leak: a cross-project dependency would
-- render a blocker's identifier to somebody who cannot see the project it lives
-- in. Widening this later breaks no existing row.
--
-- Self-dependency and duplicates are refused here. Cycles cannot be expressed as
-- a constraint and are refused by the service, which walks the existing edges in
-- one recursive query under an advisory lock on the project.
--
-- A join table, so it is never soft deleted. Soft deleting either endpoint
-- removes the rows touching it, which keeps the cycle walk from traversing tasks
-- that no read can see.
-- ---------------------------------------------------------------------------
CREATE TABLE task_dependencies (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id       uuid        NOT NULL,
    project_id         uuid        NOT NULL,
    task_id            uuid        NOT NULL,
    depends_on_task_id uuid        NOT NULL,
    created_by_user_id uuid        NOT NULL REFERENCES users (id),
    created_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT task_dependencies_not_self_check
        CHECK (task_id <> depends_on_task_id),
    CONSTRAINT task_dependencies_pair_unique
        UNIQUE (task_id, depends_on_task_id),
    CONSTRAINT task_dependencies_task_in_project_fkey
        FOREIGN KEY (task_id, project_id) REFERENCES tasks (id, project_id) ON DELETE CASCADE,
    CONSTRAINT task_dependencies_blocker_in_project_fkey
        FOREIGN KEY (depends_on_task_id, project_id) REFERENCES tasks (id, project_id) ON DELETE CASCADE,
    CONSTRAINT task_dependencies_project_in_workspace_fkey
        FOREIGN KEY (project_id, workspace_id) REFERENCES projects (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX task_dependencies_depends_on_task_id_idx ON task_dependencies (depends_on_task_id);
CREATE INDEX task_dependencies_project_id_idx ON task_dependencies (project_id);
CREATE INDEX task_dependencies_workspace_id_idx ON task_dependencies (workspace_id);

-- ---------------------------------------------------------------------------
-- the permissions this phase implements
--
-- Seven codes, and one of them is a scope grant rather than a capability.
--
-- task:manage_any widens update, assignment, status changes and deletion from
-- "the tasks I am on the hook for" to "every task here", exactly as
-- project:manage_any and team:manage_any do one and two levels up. Without it a
-- caller reaches a task only as its assignee, as its reporter, as the owner of
-- its project, or as the lead of that project's team.
--
-- There is deliberately no task:read_any. Task read scope is project read scope,
-- so project:read_any already widens it, and a second read-scope grant would be
-- a parallel model with its own resolution path that no test of the first one
-- covers.
--
-- There is deliberately no subtask or dependency permission family either. A
-- subtask is part of its task and a dependency is a property of one; both are
-- governed by task:update and task:change_status.
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, resource, action, description) VALUES
    ('task:read',          'task', 'read',          'View tasks'),
    ('task:create',        'task', 'create',        'Raise a task in a project'),
    ('task:update',        'task', 'update',        'Edit a task, its labels, its subtasks and its dependencies'),
    ('task:assign',        'task', 'assign',        'Set or clear the assignee of a task'),
    ('task:change_status', 'task', 'change_status', 'Move a task or subtask through its statuses'),
    ('task:delete',        'task', 'delete',        'Remove a task'),
    ('task:manage_any',    'task', 'manage_any',    'Act on any task in the workspace, not only those assigned, reported, owned or led');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug = 'SUPER_ADMIN'
  AND r.scope = 'PLATFORM'
  AND p.code IN (
      'task:read', 'task:create', 'task:update', 'task:assign',
      'task:change_status', 'task:delete', 'task:manage_any');

-- ---------------------------------------------------------------------------
-- backfill for workspaces that already exist
--
-- New workspaces get these same sets from SystemRole when their roles are
-- seeded. The two lists must agree, and WorkspaceRoleGrantsIT holds them
-- together. A database with no workspaces yet applies this as a no-op.
--
-- A team lead assigns tasks and updates their status, which the requirements say
-- plainly, and does not delete them: removing work is the administrator's, the
-- same way removing a project is. An employee creates and updates the tasks they
-- are permitted and moves them through their statuses, and assigns nothing.
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = ANY (
    CASE r.slug
        WHEN 'ADMIN' THEN ARRAY[
            'task:read', 'task:create', 'task:update', 'task:assign',
            'task:change_status', 'task:delete', 'task:manage_any']
        WHEN 'TEAM_LEAD' THEN ARRAY[
            'task:read', 'task:create', 'task:update', 'task:assign',
            'task:change_status']
        WHEN 'EMPLOYEE' THEN ARRAY[
            'task:read', 'task:create', 'task:update', 'task:change_status']
        ELSE ARRAY[]::text[]
    END)
WHERE r.scope = 'WORKSPACE'
ON CONFLICT (role_id, permission_id) DO NOTHING;
