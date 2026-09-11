-- V7: comments, mentions, attachments, and the append-only activity log.
--
-- OBLIGATION, restated from V3, V4, V5 and V6: every permission added here is
-- mapped to SUPER_ADMIN in this same file, and backfilled onto the workspace
-- roles that already exist. There is no bypass branch for the platform
-- administrator, and workspace role rows are written in code when a workspace is
-- created, so a permission added without both statements is one somebody
-- silently lacks.
--
-- One rule runs through all four tables and is the opposite of the one V6
-- carries. A task keys its assignee to project_members and its reporter to
-- workspace_members, so removing somebody is refused until the tasks module
-- stands them down. The people columns here key to users instead:
--
--   comments.author_user_id
--   attachments.uploader_user_id
--   comment_mentions.mentioned_user_id
--   activity_logs.actor_user_id
--
-- A comment has to outlive its author leaving the workspace. Deleting the words
-- somebody wrote because they changed team would destroy the discussion the
-- requirements ask us to keep, and an audit row whose actor could vanish would
-- not be an audit row. users is soft-deleted and its rows are never removed, so
-- the key holds forever and none of these tables needs a cleanup listener on
-- WorkspaceMemberRemovedEvent. This is the first module in the platform that
-- needs none, and the schema is what makes it true.

-- ---------------------------------------------------------------------------
-- comments
--
-- Task-scoped and flat. There is deliberately no parent_comment_id: the
-- requirements list add, edit, delete, mentions and comment activity, and
-- describe no replies. A thread column would bring its own ordering and depth
-- rules, none of which anything asked for.
--
-- project_id is carried beside task_id so authorization needs no join back to
-- tasks, exactly as subtasks carry theirs, and task_id is keyed twice: once to
-- pin the project and once to pin the workspace.
--
-- edited_at is separate from updated_at on purpose. "Timestamps and user info"
-- is a stated requirement and a reader deserves to know when the words changed;
-- updated_at moves for reasons that are not edits, so it cannot answer that.
-- ---------------------------------------------------------------------------
CREATE TABLE comments (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id   uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id     uuid        NOT NULL,
    task_id        uuid        NOT NULL,
    author_user_id uuid        NOT NULL REFERENCES users (id),
    body           text        NOT NULL,
    edited_at      timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    deleted_at     timestamptz,

    CONSTRAINT comments_body_length_check
        CHECK (length(btrim(body)) BETWEEN 1 AND 5000),
    CONSTRAINT comments_task_in_project_fkey
        FOREIGN KEY (task_id, project_id) REFERENCES tasks (id, project_id) ON DELETE CASCADE,
    CONSTRAINT comments_task_in_workspace_fkey
        FOREIGN KEY (task_id, workspace_id) REFERENCES tasks (id, workspace_id) ON DELETE CASCADE
);

-- Redundant on its own, and the target of the composite key on attachments
-- below, which is what stops a file claiming a comment that lives on a
-- different task. Exactly as roles, teams, projects, labels and tasks each
-- carry theirs.
ALTER TABLE comments
    ADD CONSTRAINT comments_id_task_unique UNIQUE (id, task_id);

CREATE INDEX comments_task_created_idx
    ON comments (task_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX comments_author_user_id_idx
    ON comments (author_user_id) WHERE deleted_at IS NULL;
CREATE INDEX comments_project_id_idx
    ON comments (project_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- comment_mentions
--
-- A join table, so not soft-deleted, per the convention.
--
-- The rows are written from the body by the server. The client does not send a
-- list of mentioned people: a client-supplied list is a second statement of the
-- same fact, and the two disagree the moment somebody edits the text and not
-- the list, which produces either a notification for a name no longer in the
-- comment or silence for one that is.
--
-- ON DELETE CASCADE from the comment is right even though comments are soft
-- deleted: an edit rewrites this set, and a hard delete of a comment could only
-- ever come from removing the task's whole project.
-- ---------------------------------------------------------------------------
CREATE TABLE comment_mentions (
    comment_id        uuid        NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    mentioned_user_id uuid        NOT NULL REFERENCES users (id),
    workspace_id      uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    created_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT comment_mentions_pkey PRIMARY KEY (comment_id, mentioned_user_id)
);

-- Phase seven reads this one to find out who to notify.
CREATE INDEX comment_mentions_mentioned_user_id_idx ON comment_mentions (mentioned_user_id);
CREATE INDEX comment_mentions_workspace_id_idx ON comment_mentions (workspace_id);

-- ---------------------------------------------------------------------------
-- attachments
--
-- A file belongs to a task, and optionally to a comment. Both columns are
-- present rather than a polymorphic owner_type/owner_id pair, because a
-- polymorphic key cannot be a foreign key and would move referential integrity
-- into the service layer, which is not where the rest of this schema puts it.
--
-- content_type is what the application detected from the leading bytes, never
-- what the client claimed. The claim and the file extension are both
-- attacker-controlled and neither is stored.
--
-- storage_provider and storage_key are what make changing provider a data
-- migration rather than a schema one. The key is opaque and never contains the
-- uploaded filename, so a hostile name cannot influence where bytes land.
--
-- The unique on storage_key is deliberately NOT partial. A soft-deleted
-- attachment still owns its object until the purge reclaims it, and handing the
-- same key to a second file would overwrite bytes somebody may yet restore.
-- ---------------------------------------------------------------------------
CREATE TABLE attachments (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id       uuid        NOT NULL,
    task_id          uuid        NOT NULL,
    comment_id       uuid,
    uploader_user_id uuid        NOT NULL REFERENCES users (id),
    filename         text        NOT NULL,
    content_type     text        NOT NULL,
    size_bytes       bigint      NOT NULL,
    checksum_sha256  text        NOT NULL,
    storage_provider text        NOT NULL,
    storage_key      text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz,

    CONSTRAINT attachments_filename_length_check
        CHECK (length(btrim(filename)) BETWEEN 1 AND 255),
    CONSTRAINT attachments_content_type_length_check
        CHECK (length(btrim(content_type)) BETWEEN 1 AND 255),
    CONSTRAINT attachments_size_bytes_check
        CHECK (size_bytes > 0),
    CONSTRAINT attachments_checksum_format_check
        CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT attachments_storage_provider_check
        CHECK (storage_provider IN ('LOCAL', 'S3')),
    CONSTRAINT attachments_storage_key_unique
        UNIQUE (storage_key),
    CONSTRAINT attachments_task_in_project_fkey
        FOREIGN KEY (task_id, project_id) REFERENCES tasks (id, project_id) ON DELETE CASCADE,
    CONSTRAINT attachments_task_in_workspace_fkey
        FOREIGN KEY (task_id, workspace_id) REFERENCES tasks (id, workspace_id) ON DELETE CASCADE,
    -- A comment attachment cannot point at a comment on a different task.
    CONSTRAINT attachments_comment_on_same_task_fkey
        FOREIGN KEY (comment_id, task_id) REFERENCES comments (id, task_id) ON DELETE CASCADE
);

CREATE INDEX attachments_task_id_idx
    ON attachments (task_id) WHERE deleted_at IS NULL;
CREATE INDEX attachments_comment_id_idx
    ON attachments (comment_id) WHERE deleted_at IS NULL;
CREATE INDEX attachments_uploader_user_id_idx
    ON attachments (uploader_user_id) WHERE deleted_at IS NULL;
CREATE INDEX attachments_project_id_idx
    ON attachments (project_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- activity_logs
--
-- APPEND ONLY. No updated_at, no deleted_at, and nothing in the application
-- writes anything but an INSERT.
--
-- The requirements say audit records must not be casually editable or
-- deletable, and database.md records the eventual enforcement: update and
-- delete privileges withheld from the application role. The application
-- currently runs its migrations under the same role it serves requests with, so
-- a REVOKE here would break Flyway on the next deployment. The trigger below is
-- the half that works under one role, refuses the casual edit today, and is
-- testable; the separate migration role and the REVOKE are recorded as delivery
-- work in architecture.md. Both together are the requirement.
--
-- No prose is stored. The requirements print "Srikanth assigned Task #123 to
-- Rahul" as an example of what must be recorded, not of what must be stored.
-- Names change, and a stored sentence would be a stale copy of the user table.
-- The row carries structured metadata and the sentence is composed at read time.
--
-- request_id ties an audit row to the correlation id already on every log line
-- and every error body, so an entry and the logs behind it can be read together.
-- ---------------------------------------------------------------------------
CREATE TABLE activity_logs (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    actor_user_id uuid        REFERENCES users (id),
    action        text        NOT NULL,
    entity_type   text        NOT NULL,
    entity_id     uuid        NOT NULL,
    project_id    uuid,
    metadata      jsonb,
    request_id    text,
    created_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT activity_logs_action_length_check
        CHECK (length(btrim(action)) BETWEEN 1 AND 100),
    CONSTRAINT activity_logs_entity_type_check
        CHECK (entity_type IN ('WORKSPACE', 'TEAM', 'PROJECT', 'TASK', 'SUBTASK', 'COMMENT', 'ATTACHMENT')),
    CONSTRAINT activity_logs_request_id_length_check
        CHECK (request_id IS NULL OR length(request_id) <= 64)
);

CREATE INDEX activity_logs_workspace_created_idx ON activity_logs (workspace_id, created_at DESC);
CREATE INDEX activity_logs_entity_idx ON activity_logs (entity_type, entity_id);
CREATE INDEX activity_logs_project_created_idx ON activity_logs (project_id, created_at DESC);
CREATE INDEX activity_logs_actor_user_id_idx ON activity_logs (actor_user_id);

-- The enforcement, not a convention. A row that has been written cannot be
-- changed or removed by anything holding an ordinary connection, including a
-- mistake in this application.
CREATE OR REPLACE FUNCTION activity_logs_refuse_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'activity_logs is append only: % is not permitted', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER activity_logs_append_only
    BEFORE UPDATE OR DELETE ON activity_logs
    FOR EACH ROW EXECUTE FUNCTION activity_logs_refuse_change();

-- ---------------------------------------------------------------------------
-- permissions
--
-- Eight codes, in the shape the previous three phases established.
--
-- There is deliberately no comment:read and no attachment:read. A comment is
-- visible exactly when its task is, which is the same reasoning that produced no
-- task:read_any in V6, and a second read grant would be a parallel model with
-- its own resolution path that no test of the first one covers. task:read is
-- the gate for reading a thread, listing files and downloading one.
--
-- The scope half, for a caller without the manage_any grant: they may delete a
-- comment they wrote or a file they uploaded, or one on a task in a project they
-- own or lead the team of. That is the V6 write scope one level down, so a lead
-- moderates their own projects without reaching the whole workspace.
--
-- Editing is author-only always, including for a holder of comment:manage_any.
-- An administrator may remove somebody's words; nobody may rewrite them and
-- leave them attributed to their author. manage_any widens deletion and nothing
-- else, and that asymmetry is deliberate.
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, resource, action, description) VALUES
    ('comment:create',        'comment',    'create',     'Comment on a task'),
    ('comment:update',        'comment',    'update',     'Edit a comment you wrote'),
    ('comment:delete',        'comment',    'delete',     'Remove a comment'),
    ('comment:manage_any',    'comment',    'manage_any', 'Remove any comment in the workspace, not only your own or those on projects you run'),
    ('attachment:create',     'attachment', 'create',     'Upload a file to a task'),
    ('attachment:delete',     'attachment', 'delete',     'Remove an attachment'),
    ('attachment:manage_any', 'attachment', 'manage_any', 'Remove any attachment in the workspace, not only your own or those on projects you run'),
    ('activity:read',         'activity',   'read',       'Browse the workspace audit history');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug = 'SUPER_ADMIN'
  AND r.scope = 'PLATFORM'
  AND p.code IN (
      'comment:create', 'comment:update', 'comment:delete', 'comment:manage_any',
      'attachment:create', 'attachment:delete', 'attachment:manage_any', 'activity:read');

-- ---------------------------------------------------------------------------
-- backfill for workspaces that already exist
--
-- New workspaces get these same sets from SystemRole when their roles are
-- seeded. The two lists must agree, and WorkspaceRoleGrantsIT holds them
-- together. A database with no workspaces yet applies this as a no-op.
--
-- The requirements give an employee "comment, upload attachments" plainly, so
-- every working role gets the five ordinary codes. A team lead is not widened
-- beyond them: the write scope already reaches the projects they own or lead,
-- which is the moderation a lead actually needs. Browsing the whole workspace
-- audit is the administrator's, beside the other administrative reads.
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = ANY (
    CASE r.slug
        WHEN 'ADMIN' THEN ARRAY[
            'comment:create', 'comment:update', 'comment:delete', 'comment:manage_any',
            'attachment:create', 'attachment:delete', 'attachment:manage_any', 'activity:read']
        WHEN 'TEAM_LEAD' THEN ARRAY[
            'comment:create', 'comment:update', 'comment:delete',
            'attachment:create', 'attachment:delete']
        WHEN 'EMPLOYEE' THEN ARRAY[
            'comment:create', 'comment:update', 'comment:delete',
            'attachment:create', 'attachment:delete']
        ELSE ARRAY[]::text[]
    END)
WHERE r.scope = 'WORKSPACE'
ON CONFLICT (role_id, permission_id) DO NOTHING;
