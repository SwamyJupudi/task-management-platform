-- V2: identity, authorization, and the minimal workspace both require.
--
-- One file, because these nine tables are a single referential unit and a
-- partially applied identity schema is not a useful state to be in.
--
-- Creation order is forced by the foreign keys: users has none, workspaces
-- points at users, roles points at workspaces, and the platform role column on
-- users is added last, once roles exists.

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email                 citext      NOT NULL,
    password_hash         text        NOT NULL,
    first_name            text        NOT NULL,
    last_name             text        NOT NULL,
    status                text        NOT NULL,
    email_verified_at     timestamptz,
    -- Read when deciding whether a token issued before a password change is
    -- still acceptable.
    password_changed_at   timestamptz NOT NULL DEFAULT now(),
    last_login_at         timestamptz,
    -- Lockout lives here rather than in status: a lock is a temporary machine
    -- decision, a deactivation is a durable human one.
    failed_login_attempts integer     NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    platform_role_id      uuid,
    -- Redundant, and load-bearing. A foreign key is not checked when any of its
    -- columns is null, so pointing at roles (id) alone could only prove the row
    -- is a role, not that it is a platform role. Carrying the scope here lets the
    -- key reference roles (id, scope), and the two checks below make the pair
    -- either wholly absent or wholly present and equal to PLATFORM.
    platform_role_scope   text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    deleted_at            timestamptz,

    CONSTRAINT users_status_check
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DEACTIVATED')),
    CONSTRAINT users_email_length_check
        CHECK (length(email) BETWEEN 3 AND 254),
    CONSTRAINT users_failed_login_attempts_check
        CHECK (failed_login_attempts >= 0),
    CONSTRAINT users_verified_when_active_check
        CHECK (status <> 'ACTIVE' OR email_verified_at IS NOT NULL),
    -- Both halves of the platform role, or neither. Without this the foreign key
    -- could be skipped by leaving the scope null.
    CONSTRAINT users_platform_role_pair_check
        CHECK ((platform_role_id IS NULL) = (platform_role_scope IS NULL)),
    -- And the only scope a user may carry is the platform one.
    CONSTRAINT users_platform_role_scope_check
        CHECK (platform_role_scope IS NULL OR platform_role_scope = 'PLATFORM')
);

-- Partial, so removing a person does not reserve their address for ever. The
-- column is citext, so two addresses differing only in case cannot both exist.
CREATE UNIQUE INDEX users_email_unique_idx ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX users_status_idx ON users (status) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- workspaces
--
-- Phase two creates only what roles and memberships need to point at. Settings
-- and the full lifecycle arrive in phase three.
-- ---------------------------------------------------------------------------
CREATE TABLE workspaces (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name               text        NOT NULL,
    slug               citext      NOT NULL,
    status             text        NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id uuid        NOT NULL REFERENCES users (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted_at         timestamptz,

    CONSTRAINT workspaces_status_check
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT workspaces_name_length_check
        CHECK (length(btrim(name)) BETWEEN 1 AND 120),
    CONSTRAINT workspaces_slug_format_check
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE UNIQUE INDEX workspaces_slug_unique_idx ON workspaces (slug) WHERE deleted_at IS NULL;
CREATE INDEX workspaces_created_by_user_id_idx ON workspaces (created_by_user_id);

-- ---------------------------------------------------------------------------
-- roles
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid        REFERENCES workspaces (id) ON DELETE CASCADE,
    slug         text        NOT NULL,
    name         text        NOT NULL,
    scope        text        NOT NULL,
    is_system    boolean     NOT NULL DEFAULT false,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT roles_scope_check CHECK (scope IN ('PLATFORM', 'WORKSPACE')),
    -- Platform scope requires no workspace; workspace scope requires one. This
    -- is what stops a workspace role from leaking across workspaces.
    CONSTRAINT roles_scope_workspace_check CHECK (
        (scope = 'PLATFORM'  AND workspace_id IS NULL)
     OR (scope = 'WORKSPACE' AND workspace_id IS NOT NULL)
    )
);

-- NULLS NOT DISTINCT, because the default treats every null workspace as
-- distinct and would happily allow two platform roles called SUPER_ADMIN.
-- Requires PostgreSQL 15 or later; the project targets 16.
ALTER TABLE roles
    ADD CONSTRAINT roles_workspace_slug_unique UNIQUE NULLS NOT DISTINCT (workspace_id, slug);

-- Redundant on its own, and the target of the composite foreign keys below.
-- It is what lets the database refuse a cross-workspace role assignment.
ALTER TABLE roles
    ADD CONSTRAINT roles_id_workspace_unique UNIQUE (id, workspace_id);

-- The target of the platform-role key on users, in the same spirit as the one
-- above: it makes "this role is platform-scoped" something the database can be
-- asked to guarantee rather than something a service is trusted to check.
ALTER TABLE roles
    ADD CONSTRAINT roles_id_scope_unique UNIQUE (id, scope);

CREATE INDEX roles_workspace_id_idx ON roles (workspace_id);

-- The platform role key can only be added now that roles exists.
--
-- Referencing (id, scope) rather than (id) is what makes the rule real: a
-- workspace role has scope WORKSPACE and cannot satisfy a key whose second
-- column is constrained to PLATFORM. Assigning one is therefore a write the
-- database refuses, not a check a service is trusted to remember.
ALTER TABLE users
    ADD CONSTRAINT users_platform_role_fkey
        FOREIGN KEY (platform_role_id, platform_role_scope) REFERENCES roles (id, scope);

CREATE INDEX users_platform_role_id_idx ON users (platform_role_id) WHERE platform_role_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- permissions: a global catalog, because a permission code names a capability
-- the application implements. Only the mapping to roles is per workspace.
-- ---------------------------------------------------------------------------
CREATE TABLE permissions (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text        NOT NULL UNIQUE,
    resource    text        NOT NULL,
    action      text        NOT NULL,
    description text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),

    -- The code is the concatenation, so the two halves cannot drift from it.
    CONSTRAINT permissions_code_composition_check CHECK (code = resource || ':' || action)
);

-- ---------------------------------------------------------------------------
-- role_permissions: the mapping the admin panel edits
-- ---------------------------------------------------------------------------
CREATE TABLE role_permissions (
    role_id       uuid        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id uuid        NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    created_at    timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX role_permissions_permission_id_idx ON role_permissions (permission_id);

-- ---------------------------------------------------------------------------
-- workspace_members
-- ---------------------------------------------------------------------------
CREATE TABLE workspace_members (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id       uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id            uuid        NOT NULL REFERENCES users (id),
    role_id            uuid        NOT NULL,
    invited_by_user_id uuid        REFERENCES users (id),
    joined_at          timestamptz NOT NULL DEFAULT now(),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT workspace_members_workspace_user_unique UNIQUE (workspace_id, user_id),

    -- The role must belong to this workspace. Enforced by the database rather
    -- than by a service check, so a cross-workspace assignment is a write that
    -- is refused rather than a bug review has to catch. A platform role has a
    -- null workspace and cannot satisfy this key, which is why no workspace
    -- member can ever be given SUPER_ADMIN.
    CONSTRAINT workspace_members_role_in_workspace_fkey
        FOREIGN KEY (role_id, workspace_id) REFERENCES roles (id, workspace_id)
);

CREATE INDEX workspace_members_user_id_idx ON workspace_members (user_id);
CREATE INDEX workspace_members_role_id_idx ON workspace_members (role_id);
CREATE INDEX workspace_members_invited_by_user_id_idx ON workspace_members (invited_by_user_id);

-- ---------------------------------------------------------------------------
-- workspace_invitations
--
-- An invitation is addressed to an email, not to a user, so inviting somebody
-- who has no account yet needs no placeholder row.
-- ---------------------------------------------------------------------------
CREATE TABLE workspace_invitations (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    email               citext      NOT NULL,
    role_id             uuid        NOT NULL,
    token_hash          text        NOT NULL UNIQUE,
    status              text        NOT NULL DEFAULT 'PENDING',
    expires_at          timestamptz NOT NULL,
    invited_by_user_id  uuid        NOT NULL REFERENCES users (id),
    accepted_at         timestamptz,
    accepted_by_user_id uuid        REFERENCES users (id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT workspace_invitations_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT workspace_invitations_accepted_consistency_check
        CHECK ((status = 'ACCEPTED') = (accepted_at IS NOT NULL AND accepted_by_user_id IS NOT NULL)),
    CONSTRAINT workspace_invitations_role_in_workspace_fkey
        FOREIGN KEY (role_id, workspace_id) REFERENCES roles (id, workspace_id)
);

-- At most one live invitation per address per workspace. A superseded one is
-- revoked rather than deleted, so the history survives.
CREATE UNIQUE INDEX workspace_invitations_pending_unique_idx
    ON workspace_invitations (workspace_id, email) WHERE status = 'PENDING';
CREATE INDEX workspace_invitations_workspace_id_idx ON workspace_invitations (workspace_id);
CREATE INDEX workspace_invitations_email_idx ON workspace_invitations (email);
CREATE INDEX workspace_invitations_role_id_idx ON workspace_invitations (role_id);
CREATE INDEX workspace_invitations_invited_by_user_id_idx ON workspace_invitations (invited_by_user_id);
CREATE INDEX workspace_invitations_accepted_by_user_id_idx ON workspace_invitations (accepted_by_user_id);

-- ---------------------------------------------------------------------------
-- user_tokens: email verification and password reset
--
-- Only the hash is stored. The value itself exists in memory and in the message
-- sent to the recipient, and nowhere else.
-- ---------------------------------------------------------------------------
CREATE TABLE user_tokens (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        text        NOT NULL,
    token_hash  text        NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT user_tokens_type_check CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);

CREATE INDEX user_tokens_user_id_type_idx ON user_tokens (user_id, type);

-- ---------------------------------------------------------------------------
-- refresh_tokens
--
-- One row per issued token. Rotation writes a new row and links it to its
-- predecessor, so a replayed token identifies the whole family to revoke.
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Constant across a rotation chain. This is what a session means here.
    session_id     uuid        NOT NULL,
    token_hash     text        NOT NULL UNIQUE,
    issued_at      timestamptz NOT NULL DEFAULT now(),
    expires_at     timestamptz NOT NULL,
    revoked_at     timestamptz,
    revoked_reason text,
    replaced_by_id uuid        REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    -- Held so a person can recognise and end their own sessions. Never returned
    -- by any other endpoint and never written to a log.
    user_agent     text,
    ip_address     text,
    created_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT refresh_tokens_revoked_reason_check CHECK (
        revoked_reason IS NULL OR revoked_reason IN (
            'LOGOUT', 'LOGOUT_ALL', 'ROTATED', 'REUSE_DETECTED',
            'PASSWORD_CHANGED', 'PASSWORD_RESET', 'ACCOUNT_DEACTIVATED', 'SESSION_REVOKED'
        )
    ),
    CONSTRAINT refresh_tokens_revoked_consistency_check
        CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),
    CONSTRAINT refresh_tokens_ip_address_length_check
        CHECK (ip_address IS NULL OR length(ip_address) <= 45)
);

CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_session_id_idx ON refresh_tokens (session_id);
CREATE INDEX refresh_tokens_user_active_idx ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX refresh_tokens_replaced_by_id_idx ON refresh_tokens (replaced_by_id);
