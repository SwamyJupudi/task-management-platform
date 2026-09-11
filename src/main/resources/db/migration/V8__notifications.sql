-- V8: in-app notifications, their read state, and the deadline scan's idempotency.
--
-- NO PERMISSIONS ARE ADDED HERE, and that is the one thing worth reading twice
-- in this file. Every migration since V3 has carried two obligations with a new
-- permission: map it to SUPER_ADMIN, and backfill it onto the workspace roles
-- that already exist. This one carries neither, because it adds no code.
--
-- A notification has exactly one audience: the person named in
-- recipient_user_id. Membership of the workspace plus being that person is the
-- whole rule, so a notification:read code would be held by everybody and would
-- gate nothing. The precedent is V7, which deliberately created no comment:read
-- and no attachment:read for the same reason. Nobody reads somebody else's feed,
-- including SUPER_ADMIN; the administrative question of who was told what is the
-- audit trail's, and activity_logs already answers it.
--
-- The people columns key to users, following the V7 rule rather than the V6 one:
-- a notification outlives its actor leaving the workspace, and users rows are
-- only ever soft-deleted so the key holds forever. Unlike V7's tables, this one
-- does need cleanup listeners, because a notification is not a record of history
-- and must not outlive the access it implies.

-- ---------------------------------------------------------------------------
-- notifications
--
-- Not soft deleted, as database.md has said since the model was drawn. There is
-- nothing to restore: a notification is a message about something else, and the
-- something else is what carries the history.
--
-- updated_at is present although no other transient table needs one. read_at
-- mutates, and a reader deserves to know when a row last moved.
--
-- actor_user_id is nullable, exactly as activity_logs.actor_user_id is. The
-- deadline scan is performed by the platform rather than by a person, and
-- inventing a system user to satisfy a constraint would put a fictional person
-- in somebody's feed.
--
-- metadata is jsonb and holds no prose. The sentence a recipient reads is
-- composed when the row is read, from the names those identifiers resolve to at
-- that moment, so renaming somebody does not leave a stale message behind. This
-- is the V7 rule applied a second time.
--
-- dedupe_key is what stops the scheduler repeating itself. For a deadline row it
-- is the task id and the due date it was sent for, so a second run writes
-- nothing and a due date that moves notifies again. It is null for every
-- event-driven row, because two people can genuinely be told the same thing
-- twice: a task reassigned away and back is two notifications, not one.
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    recipient_user_id uuid        NOT NULL REFERENCES users (id),
    actor_user_id     uuid        REFERENCES users (id),
    type              text        NOT NULL,
    entity_type       text        NOT NULL,
    entity_id         uuid        NOT NULL,
    project_id        uuid,
    metadata          jsonb,
    dedupe_key        text,
    read_at           timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT notifications_type_check
        CHECK (type IN (
            'task.assigned',
            'task.status_changed',
            'task.deadline_approaching',
            'comment.created',
            'comment.mentioned',
            'project.member_added',
            'project.status_changed')),
    CONSTRAINT notifications_entity_type_check
        CHECK (entity_type IN ('PROJECT', 'TASK', 'COMMENT')),
    CONSTRAINT notifications_dedupe_key_length_check
        CHECK (dedupe_key IS NULL OR length(dedupe_key) BETWEEN 1 AND 200),
    -- Nobody notifies themselves. The rule is enforced in the recipient resolver
    -- and again here, because a listener added later by somebody who has not read
    -- the resolver is exactly how that rule would quietly stop being true.
    CONSTRAINT notifications_actor_is_not_recipient_check
        CHECK (actor_user_id IS NULL OR actor_user_id <> recipient_user_id)
);

-- The unread badge and the history listing, which are the only two reads this
-- table has. Both are always narrowed to one recipient first.
CREATE INDEX notifications_recipient_read_idx ON notifications (recipient_user_id, read_at);
CREATE INDEX notifications_recipient_created_idx ON notifications (recipient_user_id, created_at DESC);

-- Cleanup when somebody loses access, and when a record goes away.
CREATE INDEX notifications_workspace_idx ON notifications (workspace_id);
CREATE INDEX notifications_project_idx ON notifications (project_id);
CREATE INDEX notifications_entity_idx ON notifications (entity_type, entity_id);

-- The scheduler's idempotency, and the reason a re-run costs nothing. Partial,
-- because every event-driven row has a null key and they must not collide with
-- each other.
CREATE UNIQUE INDEX notifications_dedupe_idx
    ON notifications (recipient_user_id, type, dedupe_key)
    WHERE dedupe_key IS NOT NULL;
