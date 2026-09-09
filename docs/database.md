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
| `users`            | Unique `citext` email, password hash, status, `platform_role_id` nullable |
| `permissions`      | Global catalog. `code` is unique and reads `resource:action`           |
| `roles`            | `scope` is `PLATFORM` or `WORKSPACE`, with a check constraint tying scope to the presence of `workspace_id`. Unique on `(workspace_id, slug)`. `is_system` rows cannot be deleted from the admin panel |
| `role_permissions` | Join. The per-workspace mapping the admin panel edits                  |
| `user_tokens`      | Email verification and password reset. Hashed, single use, expiring    |
| `refresh_tokens`   | Hashed, rotating, revocable                                            |

The two token tables depend on the **proposed** authentication design and are not
settled.

### Workspace and teams

| Table                   | Notes                                                            |
| ----------------------- | ---------------------------------------------------------------- |
| `workspaces`            | Root scope for everything below                                   |
| `workspace_members`     | One role per user per workspace. Unique on `(workspace_id, user_id)`. The role must belong to the same workspace |
| `workspace_invitations` | Hashed token, expiry, status                                      |
| `teams`                 | `lead_user_id` is a single column, since the requirements say assign a team lead in the singular. The lead must be a member |
| `team_members`          | Join. Unique on `(team_id, user_id)`                              |

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
