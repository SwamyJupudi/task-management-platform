# Database

PostgreSQL 16. Flyway is the only source of schema truth; Hibernate never
creates or alters anything.

## Migration policy

- Migrations live in `src/main/resources/db/migration`, named `V<n>__<what>.sql`.
- A migration that has been merged is never edited. Corrections go in a new one.
- Each module brings its own migration when it is built. The schema grows with
  the code rather than ahead of it.
- `spring.flyway.clean` is disabled everywhere, including tests.

Applied so far:

| Version | What                                                     |
| ------- | -------------------------------------------------------- |
| `V1`    | Extensions only: `pgcrypto` for UUID generation, `citext` for the case-insensitive unique email column. No tables. |
| `V2`    | Identity tables: `users`, `permissions`, `roles`, `role_permissions`, `workspaces`, `workspace_members`, `workspace_invitations`, `user_tokens`, `refresh_tokens` |
| `V3`    | Seed data: the global permission catalog, the single `SUPER_ADMIN` platform role, and an explicit `role_permissions` row joining that role to every permission in the catalog. Workspace roles are seeded in code when a workspace is created, so they are not migration data |
| `V4`    | Workspace settings and lifecycle columns, the `teams` and `team_members` tables, seven new permissions mapped to `SUPER_ADMIN`, and a backfill giving the new grants to workspace roles that already existed |

A migration that adds a permission carries a second obligation beside the
`SUPER_ADMIN` mapping: **the workspace roles that already exist need the new
grants too.** Those rows were written in code when each workspace was created, so
nothing updates them on its own, and a workspace created before the migration
would otherwise be permanently less capable than one created after it. `V4`
backfills by role slug, and `WorkspaceRoleGrantsIT` holds the backfill and
`SystemRole` together. The backfill is a no-op on a database with no workspaces
yet, which is every test run, so the test asserts the agreement rather than the
statement.

`SUPER_ADMIN` is mapped explicitly rather than short-circuited in code, so that
authorization has one implementation and not two. Every later migration that adds
a permission carries the obligation to map it to `SUPER_ADMIN` in the same file.
A permission added without that row is one the platform administrator does not
hold.

## Conventions

- UUID primary keys, generated with `gen_random_uuid()`.
- `created_at` and `updated_at` on every table unless noted.
- Enumerations are stored as text with a check constraint, not as native enum
  types, so adding a value does not need a type migration.
- Foreign key columns are indexed explicitly. PostgreSQL does not do this for
  you, and every one of them is used in a join.
- **Soft deletion** applies only where restoring matters: `users`, `workspaces`,
  `teams`, `projects`, `tasks`, `subtasks`, `comments`, `attachments`. It is
  never applied to join tables, tokens, notifications, or audit rows. This is our
  reading of the requirement to use soft deletion "where appropriate".
- Indexes on soft-deletable tables are partial, excluding deleted rows.

## Target model

The model below is approved and complete. It is documentation, not a migration
plan: tables are created by the phase that owns them.

### Identity

| Table              | Notes                                                                 |
| ------------------ | --------------------------------------------------------------------- |
| `users`            | Unique `citext` email, password hash, status, `platform_role_id` nullable, lockout counters, `password_changed_at` |
| `permissions`      | Global catalog. `code` is unique and reads `resource:action`           |
| `roles`            | `scope` is `PLATFORM` or `WORKSPACE`, with a check constraint tying scope to the presence of `workspace_id`. Unique on `(workspace_id, slug)`. `is_system` rows cannot be deleted from the admin panel |
| `role_permissions` | Join. The per-workspace mapping the admin panel edits                  |
| `user_tokens`      | Email verification and password reset. Hashed, single use, expiring    |
| `refresh_tokens`   | Hashed, rotating, revocable. One row per issued token, chained by `session_id` |

The authentication design behind the two token tables is now approved. See
*Identity and authentication* in `architecture.md`.

Three constraints in this group carry weight and are worth stating plainly.

**Email uniqueness is partial.** The unique index on `users.email` applies only
where `deleted_at` is null, so removing a person does not reserve their address
forever. The column is `citext`, so two addresses differing only in case cannot
both exist.

**Role slugs use `NULLS NOT DISTINCT`.** A plain unique index on
`(workspace_id, slug)` would treat every null workspace as distinct and would
happily allow two platform roles called `SUPER_ADMIN`. PostgreSQL 15 introduced
`UNIQUE NULLS NOT DISTINCT`, and we are on 16.

**A member's role is tied to their workspace by the database, not by a service
check.** `roles` carries a redundant `UNIQUE (id, workspace_id)`, and
`workspace_members` references it with a composite foreign key on
`(role_id, workspace_id)`. Assigning a role from another workspace is then not a
bug that review has to catch, it is a write the database refuses. A platform role
has a null workspace and so cannot satisfy that key at all, which means no
workspace member can ever be given `SUPER_ADMIN`. That is the desired rule and it
costs one extra index.

**A platform role can only be a platform role.** `users` carries
`platform_role_scope` beside `platform_role_id`, and the key references
`roles (id, scope)`. The redundant column is what makes the rule enforceable: a
foreign key is not checked when any of its columns is null, so a key on
`roles (id)` alone could prove the row was a role but never that it was
platform-scoped. Two checks close the remaining gaps, one requiring the pair to
be wholly present or wholly absent, the other pinning the scope to `PLATFORM`.
Assigning a workspace role is therefore a write PostgreSQL refuses rather than a
rule a service is trusted to remember. The application reinforces it from the
other side: `PlatformRoleService.assignSuperAdmin` takes no role identifier, so
naming the wrong role is not expressible in ordinary code.

**A team's people are pinned to its workspace by the database.** Both `teams` and
`team_members` carry `workspace_id` beside the user, and the pair is a foreign key
into `workspace_members (workspace_id, user_id)`. So a lead who does not belong to
the workspace, and a team member who does not, are writes PostgreSQL refuses.
`team_members` keys its team the same way, into `teams (id, workspace_id)`, which
is why `teams` carries the redundant `UNIQUE (id, workspace_id)` that `roles`
carries for the same reason.

This has a consequence worth stating, because it is the opposite of the usual
one: **removing somebody from a workspace is refused while they lead or belong to
one of its teams.** The `teams` module clears that state first, on an event
published before the membership row is deleted. The dependency is enforced rather
than remembered, so a future module that hangs rows off a membership will be told
at once rather than silently leaving orphans.

**The workspace default role is keyed the same way.** `default_role_id` references
`roles (id, workspace_id)`, so one workspace cannot be pointed at another's role,
and a platform role has a null workspace and cannot be named at all.

**Archived means frozen, deleted means gone.** `workspaces.status` and
`archived_at` are held consistent by a check constraint, so the flag and the
timestamp cannot disagree. Archiving is reversible and blocks every write inside
the workspace; soft deletion hides it and releases its slug.

**Membership rows do not outlive the person.** Removing an account deletes its
`workspace_members` rows rather than flagging them, which follows the rule above
that soft deletion never applies to a join table. The alternative, keeping them
and filtering at read time, would make a page of twenty sometimes return
nineteen and would put the same condition into every future query over members.
The record of who belonged to what and when is the audit log's, and arrives in
phase six. A deactivation removes nothing, because the person is expected back.

`refresh_tokens` records the address and user agent of the session that created
it. This is personal data held for one purpose: letting a person see and end
their own sessions. It is never returned by any other endpoint and never
written to a log.

Rows in `user_tokens` and `refresh_tokens` are never deleted in this phase.
Expiry is checked on use, so a stale row grants nothing; it only occupies space.
The scheduled purge is hardening-phase work.

### Workspace and teams

| Table                   | Notes                                                            |
| ----------------------- | ---------------------------------------------------------------- |
| `workspaces`            | Root scope for everything below. Created in phase two with identity columns only; phase three added `description`, `timezone`, `default_role_id`, and the archive pair. Settings are columns rather than a one-to-one settings table: there are four, they are read whenever a workspace is rendered, and a second table would buy a join and nothing else |
| `workspace_members`     | One role per user per workspace. Unique on `(workspace_id, user_id)`. The role must belong to the same workspace, enforced by the composite foreign key described above |
| `workspace_invitations` | Hashed token, expiry, status. One pending invitation per address per workspace, enforced by a partial unique index |
| `teams`                 | `lead_user_id` is a single column, since the requirements say assign a team lead in the singular, and nullable, because a team between leads is an ordinary state. Status `ACTIVE/ARCHIVED`, soft deleted. Name unique per workspace, folded and partial |
| `team_members`          | Join. Unique on `(team_id, user_id)`. Carries `workspace_id` so both of its rules are foreign keys |

### Projects and tasks

| Table               | Notes                                                                |
| ------------------- | -------------------------------------------------------------------- |
| `projects`          | Status `PLANNING/ACTIVE/ON_HOLD/COMPLETED/ARCHIVED`, priority `LOW/MEDIUM/HIGH/CRITICAL`, unique `key` per workspace |
| `project_members`   | Join                                                                  |
| `labels`            | One workspace-scoped catalog, shared by projects and tasks. The requirements say tags on projects and labels on tasks; two near-identical tables would earn nothing |
| `project_labels`    | Join                                                                  |
| `task_labels`       | Join                                                                  |
| `tasks`             | Status `TODO/IN_PROGRESS/REVIEW/DONE`, per-project sequential `task_number`, `board_position` for board ordering |
| `subtasks`          | A separate table, not a self-referencing task, because the requirements list SubTask as its own entity with a narrower field set |
| `task_dependencies` | Unique pair, with a check that a task cannot block itself. Cycles are prevented in the service layer |

### Collaboration, notifications, audit

| Table              | Notes                                                                 |
| ------------------ | --------------------------------------------------------------------- |
| `comments`         | Task-scoped                                                            |
| `comment_mentions` | Join, drives mention notifications                                     |
| `attachments`      | Records `storage_provider` and `storage_key`, so changing provider is a data migration rather than a schema one. The file itself never lives on the application server |
| `notifications`    | Recipient, type, entity reference, `read_at`. Not soft deleted         |
| `activity_logs`    | **Append only.** No update timestamp, no soft delete. Update and delete privileges are withheld from the application database role, which is how the requirement that audit records not be casually editable is enforced |

## Planned indexes

Created with the tables that need them, not retrofitted.

| Index                                   | Serves                          |
| --------------------------------------- | ------------------------------- |
| `tasks (project_id, status)`             | Board view                      |
| `tasks (assignee_user_id, status)`       | My Tasks                        |
| `tasks (workspace_id, due_date)`         | Calendar view, overdue reports  |
| Full text over task title and description | Global search                  |
| `notifications (recipient_user_id, read_at)` | Unread badge and history    |
| `activity_logs (workspace_id, created_at desc)` | Audit browsing           |
| `activity_logs (entity_type, entity_id)` | History for one record          |
| `teams (workspace_id)`                   | The team list of a workspace    |
| `team_members (team_id)`                 | One team's roster               |
| `team_members (user_id)`                 | Cleanup when somebody leaves    |

All partial on non-deleted rows where the table is soft-deletable.

## Proposed business rule: project progress

**Not implemented. Awaiting approval.**

The requirements list Progress as a project field but do not say who sets it.
Letting a person type it guarantees it will be wrong, so the proposal is to
derive it.

- Progress is the share of the project's non-deleted tasks that are `DONE`,
  expressed as a whole percentage, rounded down.
- A task with subtasks contributes fractionally: its own share is the proportion
  of its non-deleted subtasks that are complete. A task with no subtasks
  contributes zero or one.
- A project with no tasks has a progress of zero, not null.
- Recalculated when a task or subtask changes status, or is created or deleted,
  and stored on the project so reads stay cheap.

Until this is approved the column exists and is not maintained.

## Backup and recovery

Frequency, retention, and the recovery procedure are documented in the delivery
phase, once the hosting provider is chosen. The requirements demand the
documentation but state no target values.
