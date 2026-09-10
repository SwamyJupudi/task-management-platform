-- V3: the global permission catalog and the platform administrator role.
--
-- Workspace roles are not seeded here. Each workspace owns its own role rows and
-- they are written when the workspace is created, so they are code, not
-- migration data.
--
-- OBLIGATION FOR EVERY LATER MIGRATION THAT ADDS A PERMISSION:
-- map it to SUPER_ADMIN in the same file. Authorization has one implementation
-- and there is deliberately no bypass branch for the platform administrator, so
-- a permission that is added without that mapping is a permission the platform
-- administrator silently does not hold. The final statement in this file is
-- written so that re-running the pattern is a one-line copy.

INSERT INTO permissions (code, resource, action, description) VALUES
    ('user:read',          'user',       'read',        'View user accounts'),
    ('user:update',        'user',       'update',      'Edit a user account'),
    ('user:activate',      'user',       'activate',    'Reactivate a deactivated account'),
    ('user:deactivate',    'user',       'deactivate',  'Deactivate an account'),
    ('user:delete',        'user',       'delete',      'Remove a user account'),

    ('workspace:read',     'workspace',  'read',        'View a workspace'),
    ('workspace:create',   'workspace',  'create',      'Create a workspace'),
    ('workspace:update',   'workspace',  'update',      'Edit workspace details'),
    ('workspace:delete',   'workspace',  'delete',      'Remove a workspace'),

    ('member:read',        'member',     'read',        'View workspace members'),
    ('member:invite',      'member',     'invite',      'Invite somebody to a workspace'),
    ('member:remove',      'member',     'remove',      'Remove a member from a workspace'),
    ('member:assign_role', 'member',     'assign_role', 'Change the role of a member'),

    ('role:read',          'role',       'read',        'View roles'),
    ('role:manage',        'role',       'manage',      'Edit the permissions of a role'),

    ('permission:read',    'permission', 'read',        'View the permission catalog');

-- The one platform-scoped role. Its workspace is null, which the check
-- constraint on roles requires for PLATFORM scope, and which is also why no
-- workspace member can ever be assigned it.
INSERT INTO roles (workspace_id, slug, name, scope, is_system)
VALUES (NULL, 'SUPER_ADMIN', 'Super Admin', 'PLATFORM', true);

-- Explicit mapping to every permission in the catalog. This is what gives the
-- platform administrator its reach, through the ordinary role-to-permission
-- query rather than through a privileged shortcut.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.slug = 'SUPER_ADMIN' AND r.scope = 'PLATFORM';
