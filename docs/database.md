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
| `V5`    | Projects: `projects`, `project_members`, and the shared `labels` catalog with `project_labels`. Seven project permissions, mapped and backfilled the same way |
| `V6`    | Tasks: `tasks`, `project_task_counters`, `subtasks`, `task_labels` and `task_dependencies`. Seven task permissions, mapped and backfilled the same way |
| `V7`    | Collaboration and audit: `comments`, `comment_mentions`, `attachments` and `activity_logs`, plus the trigger that makes the audit table append only. Eight permissions, mapped and backfilled the same way |
| `V8`    | `notifications`, with read state and the partial unique index the deadline scan is made idempotent by. **No permissions**, and so no `SUPER_ADMIN` mapping and no backfill: a notification has one audience, the person named in it, so there is no grant to hold |
| `V9`    | **Indexes only.** Eight of them, for the dashboards and reports. No table, no column, no trigger, no constraint and no seed row: phase eight derives every figure from what `V3` to `V7` already store. **No permissions**, and so no `SUPER_ADMIN` mapping and no backfill, for the second time after `V8` and for a different reason: a report is computed over the caller's project read scope, and `project:read_any` already widens it, so a `report:read_any` beside it would be that grant under a second name |

| `V10`   | **The admin panel.** Two permissions, `admin:read_system` and `platform_role:assign`, mapped to `SUPER_ADMIN` and **deliberately backfilled onto no workspace role**: both name platform administration, and `workspace:delete` has been the precedent for that since `V3`. Makes `activity_logs.workspace_id` **nullable** and widens its entity-type check to add `USER` and `ROLE`, so that actions taken outside any workspace can be audited at all. Two indexes. **No table and no column** |

A migration that adds a permission carries a second obligation beside the
`SUPER_ADMIN` mapping: **the workspace roles that already exist need the new
grants too.** Those rows were written in code when each workspace was created, so
nothing updates them on its own, and a workspace created before the migration
would otherwise be permanently less capable than one created after it. `V4`
backfills by role slug, and `WorkspaceRoleGrantsIT` holds the backfill and
`SystemRole` together. The backfill is a no-op on a database with no workspaces
yet, which is every test run, so the test asserts the agreement rather than the
statement.

That obligation has had **three** distinct answers, and which one applies is a
decision rather than an oversight:

- `V4` to `V7` backfilled by role slug, because the codes named work that
  workspace roles do.
- `V8` and `V9` added no permission at all, so there was nothing to backfill.
- `V10` added two that **no workspace role should ever hold**. Reading across
  every workspace, and granting somebody the platform role, are not things an
  administrator of one workspace has any business doing, and `@perm.onPlatform`
  never consults workspace membership anyway, so a workspace grant would gate
  nothing while looking as though it did. `workspace:delete` has sat in the
  catalog on exactly these terms since `V3`.

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

**A project's people and its team are pinned the same way.** `projects` keys
`(workspace_id, owner_user_id)` into `workspace_members` and `(team_id,
workspace_id)` into `teams`, and `project_members` keys both its project and its
user. So an owner from outside the workspace, a team from another workspace, and
a project member who does not belong are all writes PostgreSQL refuses.

The consequence is the same one teams already carry: **removing somebody from a
workspace is refused while they own or belong to one of its projects**, and the
`projects` module clears that state first on the event published before the
membership row is deleted. A deleted team is the one case nothing refuses, since
a soft delete leaves the row in place, which is exactly why projects are detached
from it deliberately rather than left pointing at a row every read filters out.

**Owner and team are nullable, and that is deliberate.** A project between owners
is an ordinary state. `NOT NULL` would mean an owner could never leave the
workspace, and would force the cleanup to invent a replacement rather than clear
the column and let somebody decide.

**Project progress is derived, as of `V6`.** The column has a check constraint
bounding it to 0-100 and is rewritten by one statement whenever a task or subtask
changes. See the rule at the end of this document.

**A task's people are pinned by two different keys, on purpose.** `tasks` keys
`(project_id, assignee_user_id)` into `project_members` and `(workspace_id,
reporter_user_id)` into `workspace_members`. The assignee is the narrower rule
because it is what makes task visibility coherent: somebody cannot hold work in a
project they are not on, so "tasks assigned to me" is a subset of "tasks I can
see" by construction. The reporter is the wider one because an administrator may
raise a task on a project they are not a member of, and pinning them to the
project would refuse an ordinary write.

The consequence is the familiar one, one level deeper: **removing somebody from a
project is refused while they still hold a task on it**, and removing them from a
workspace is refused while they hold or reported one anywhere in it. The tasks and
subtasks modules clear that state on the events published before the rows are
deleted, and they are ordered ahead of the projects module's own cleanup, which
deletes the very rows those keys point at. Two listeners sharing a precedence
would run in an order Spring does not define, so the tasks pair sit at highest
precedence and `ProjectCleanupListener` a hundred behind them.

**A task number is never given back.** `tasks (project_id, task_number)` is unique
and, alone among the uniques in this schema, is *not* partial on `deleted_at`.
Every other one excludes deleted rows so a name or key becomes free again; this
one must not, because a link to `PROJ-12` has to keep meaning one task. Projects
therefore show gaps in their numbering, which is the price of a stable identifier.

**Numbering is safe under concurrent creation because it is one statement.**
`project_task_counters` is written by an insert with `ON CONFLICT DO UPDATE` that
increments and returns; conflicting creators block on the row and each leaves with
a distinct number. `SELECT max(task_number) + 1` would be a read followed by a
write, which two transactions interleave inside, and which passes every test that
does not run them at once.

**A dependency cannot leave its project.** Both ends of `task_dependencies` key
into `tasks (id, project_id)`, so a cross-project dependency is unrepresentable
and a cross-workspace one is too, since the project pins the workspace. It also
closes an information leak: a dependency reaching into another project would render
a blocker's identifier to somebody who cannot see the project it lives in.

**Completion timestamps cannot disagree with their status.** Both `tasks` and
`subtasks` carry `CHECK ((status = 'DONE') = (completed_at IS NOT NULL))`, the same
technique `workspaces.status` and `archived_at` use. The requirements ask a subtask
to track completion *and* status; they are one fact, and this is what stops them
becoming two.

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
| `projects`          | Status `PLANNING/ACTIVE/ON_HOLD/COMPLETED/ARCHIVED`, priority `LOW/MEDIUM/HIGH/CRITICAL`, unique `key` and unique name per workspace, both folded and partial. `owner_user_id` and `team_id` nullable and both keyed to the workspace. Soft deleted |
| `project_members`   | Join. Unique on `(project_id, user_id)`. Carries `workspace_id`, so both of its rules are foreign keys. No project-level role |
| `labels`            | One workspace-scoped catalog, shared by projects and tasks. The requirements say tags on projects and labels on tasks; two near-identical tables would earn nothing |
| `project_labels`    | Join                                                                  |
| `task_labels`       | Join                                                                  |
| `tasks`             | Status `TODO/IN_PROGRESS/REVIEW/DONE`, priority `LOW/MEDIUM/HIGH/CRITICAL`, per-project sequential `task_number`, `board_position` for board ordering, effort in whole minutes. Soft deleted |
| `project_task_counters` | One row per project holding the next task number. Written only by an upsert that increments and returns, which is what makes concurrent creation safe |
| `subtasks`          | A separate table, not a self-referencing task, because the requirements list SubTask as its own entity with a narrower field set. Shares the task status values; completion is `status = DONE`. Soft deleted |
| `task_dependencies` | Unique pair, with a check that a task cannot block itself and composite keys confining both ends to one project. Cycles are prevented in the service layer |

### Collaboration, notifications, audit

| Table              | Notes                                                                 |
| ------------------ | --------------------------------------------------------------------- |
| `comments`         | Task-scoped and flat: no parent column, because the requirements describe no replies. `edited_at` beside `updated_at`, since a reader deserves to know when the words changed and `updated_at` moves for other reasons. Soft deleted |
| `comment_mentions` | Join, drives mention notifications. The pair is the primary key, so naming somebody twice in one comment is one mention. Rows are derived from the body by the server and rewritten whenever it changes |
| `attachments`      | Records `storage_provider` and `storage_key`, so changing provider is a data migration rather than a schema one. The file itself never lives on the application server. `content_type` is what the application detected from the leading bytes; what the client claimed is not stored. Soft deleted |
| `notifications`    | Recipient, actor (nullable, since the deadline scan is nobody), `type`, entity reference, `project_id`, `jsonb` metadata, `dedupe_key`, `read_at`. Not soft deleted. A check constraint refuses a row whose actor is its own recipient, because nobody is notified of what they just did |
| `activity_logs`    | **Append only.** No update timestamp, no soft delete. A trigger refuses every `UPDATE` and `DELETE`, which is how `V7` enforces the requirement that audit records not be casually editable under the single database role the application currently uses. Withholding the privileges from a separate application role completes it, and is delivery-phase work. `workspace_id` became **nullable** in `V10`: a null means the action happened outside any workspace, which is what account and platform-role administration are |

**The people columns here key to `users`, and that is the opposite of everywhere
else.** A comment's author, an attachment's uploader, a mention's subject and an
audit row's actor all reference `users` rather than a membership table. A comment
has to outlive its author leaving the workspace, and an audit row whose actor could
vanish would not be an audit row. Because `users` rows are only ever soft-deleted,
the key holds forever, and **these are the first tables in the schema that need no
cleanup when somebody leaves a workspace**.

**A comment attachment cannot point at a comment on another task.** `attachments`
keys `(comment_id, task_id)` into `comments (id, task_id)`, which is why `comments`
carries the redundant `UNIQUE (id, task_id)` that `roles`, `teams`, `projects` and
`tasks` each carry for the same reason. Both columns are present rather than a
polymorphic `owner_type`/`owner_id` pair, because a polymorphic key cannot be a
foreign key at all.

**The unique on `attachments.storage_key` is deliberately not partial.** Every other
unique in this schema excludes deleted rows so a name becomes free again; this one
must not, because a soft-deleted attachment still owns its stored object until the
purge reclaims it, and handing the same key to a second file would overwrite bytes
somebody may yet restore.

**`notifications.dedupe_key` is what makes the deadline scan idempotent.** For a
deadline row it is the task and the due date the message was sent for, and the unique
index over `(recipient_user_id, type, dedupe_key)` is partial so that the null key
every event-driven row carries cannot collide. A second scan writes nothing; a due
date that moves produces a new key and notifies again, which is correct, because it
is a new deadline. The alternative, asking the table what it had already sent, races
with its own writing.

**Notifications are the one thing here that is deliberately deleted.** A comment
outlives its author leaving the workspace and an audit row outlives everybody. A
notification is a message saying "come and look at this", so leaving a workspace,
leaving a project or losing an account removes the rows outright.

**A null workspace on an audit row means the platform, and the two listings are
disjoint by construction.** Phase nine is the first thing to record an action
taken outside any workspace: deactivating an account, granting the platform role,
editing somebody's profile. The invariant that makes one nullable column safe is
that **every workspace-scoped query filters on it**, so a platform row can never
appear in a workspace's history, and the platform browse asks for `IS NULL`, so a
workspace row can never appear in that. Both directions are asserted, because
either leak is a leak.

A role edit is the exception that does carry a workspace, because a role belongs
to one. Its row therefore lands in that workspace's own history, which is where
somebody wondering why their permissions changed this morning would look.

**`activity_logs.metadata` is `jsonb` rather than a column per action.** Every action
carries different facts, and a table with a column for each would be mostly nulls and
would need a migration for every new kind of event. No prose is stored: the sentence
a reader sees is composed when the row is read, so renaming somebody does not leave a
stale sentence in the audit trail.

## Planned indexes

Created with the tables that need them, not retrofitted.

| Index                                   | Serves                          |
| --------------------------------------- | ------------------------------- |
| `tasks (project_id, status)`             | Board view                      |
| `tasks (assignee_user_id, status)`       | My Tasks                        |
| `tasks (workspace_id, due_date)`         | Calendar view, overdue reports  |
| `tasks (workspace_id, project_id)`       | The workspace-wide task listing |
| `subtasks (task_id)`                     | One task's checklist            |
| `subtasks (assignee_user_id, status)`    | Personal workload               |
| `task_dependencies (depends_on_task_id)` | What a task blocks              |
| `task_labels (label_id)`                 | Filtering by label              |
| `notifications (recipient_user_id, read_at)` | Unread badge                |
| `notifications (recipient_user_id, created_at desc)` | The history listing |
| `notifications (workspace_id)`           | Cleanup when somebody leaves one |
| `notifications (project_id)`             | Cleanup when somebody leaves a project |
| `notifications (entity_type, entity_id)` | Finding what points at a record |
| `notifications (recipient_user_id, type, dedupe_key)` partial unique | The deadline scan's idempotency |
| `activity_logs (workspace_id, created_at desc)` | Audit browsing           |
| `activity_logs (entity_type, entity_id)` | History for one record          |
| `activity_logs (project_id, created_at desc)` | One project's history, and the narrowing a task's history runs inside |
| `comments (task_id, created_at)`         | One task's thread              |
| `comments (author_user_id)`              | Comment activity by person     |
| `comment_mentions (mentioned_user_id)`   | Who to notify about a mention  |
| `attachments (task_id)`                  | One task's files               |
| `attachments (comment_id)`               | One comment's files            |
| `teams (workspace_id)`                   | The team list of a workspace    |
| `team_members (team_id)`                 | One team's roster               |
| `team_members (user_id)`                 | Cleanup when somebody leaves    |
| `projects (workspace_id, status)`        | The project list of a workspace |
| `projects (owner_user_id)`               | Filter by owner, and cleanup    |
| `projects (team_id)`                     | Filter by team, and detaching   |
| `project_members (project_id)`           | One project's roster            |
| `project_members (user_id)`              | Visibility, and cleanup         |
| `tasks (workspace_id, status)`           | Workspace-wide status distribution, and the open-task count |
| `tasks (workspace_id, assignee_user_id, status)` | Employee workload, and the employee dashboard's own counts |
| `tasks (workspace_id, completed_at)` doubly partial | Completion trends, and "completed in period". Only finished tasks carry the column |
| `tasks (workspace_id, created_at)`      | The created half of the productivity trend |
| `tasks (project_id, due_date)`          | Overdue and upcoming inside one project or one scope list |
| `subtasks (workspace_id, assignee_user_id, status)` | Personal checklist load on the employee dashboard |
| `projects (workspace_id, team_id, status)` | Team performance, which groups a workspace's projects by team |
| `activity_logs (workspace_id, actor_user_id, created_at desc)` | One person's own recent activity. Not partial: `activity_logs` is never soft-deleted |
| `activity_logs (created_at desc)` partial on `workspace_id IS NULL` | The platform audit browse. The workspace index above leads with `workspace_id` and cannot serve an `IS NULL` scan ordered by time; partial on the predicate, this one holds only platform rows |
| `users (locked_until)` partial on `locked_until IS NOT NULL` | The locked-account count and the locked filter on the admin directory. One row per account that has ever been locked and still carries the timestamp |

All partial on non-deleted rows where the table is soft-deletable, except the unique
on `tasks (project_id, task_number)` and the unique on `attachments.storage_key`,
each for the reason given above.

There is deliberately **no index on `activity_logs.metadata`**. A task's history
finds its comments and files through the task identifier in their metadata, and that
lookup runs inside `project_id`, which is indexed, so it reads one project's rows
rather than a workspace's. An expression index belongs with the query tuning in the
phase that has the volume to justify it.

Full text over task title and description is **not** created yet. Phase five folds
the search term and matches the title and the rendered `PROJ-12` form, narrowed
first by the visibility predicate and by paging. The trigram or GIN index, and the
extension it needs, belong with the other query tuning in the hardening phase.
Shipping an index nothing queries would be worse than not shipping it.

## Business rule: report definitions

**Implemented in phase eight.** The requirements name these figures and define
none of them. These definitions are ours, and every query in that phase is
written against them. They sit here rather than in the code alone because two
places already compute "overdue" and a third will be tempted to.

| Term | Definition |
| --- | --- |
| **open** | status is not `DONE`, and the row is not soft-deleted |
| **overdue** | open, `due_date` is set, and `due_date` is strictly before today. Work due *today* is not overdue: the day is not over |
| **upcoming** | open, `due_date` is set, and between today and today plus the lead window, both ends included |
| **completed** | status is `DONE`, dated by `completed_at` |
| **progress** | the derived `projects.progress` column. Phase eight reads it and never recomputes it, so there is one rule for it rather than two |
| **workload** | per person: open, in progress, overdue, completed in the period, plus estimated and actual minutes summed over **open work only** |
| **productivity** | tasks completed per bucket against tasks created per bucket |

**Finished work is never overdue, however late it was.** A list of overdue work
is a list of what somebody has to act on, and a task delivered three days late
last month is not on it. This matches the `overdue` filter on the task listing
exactly, and the two must not drift: if they ever disagree, a dashboard count and
the list it links to differ by a row with nothing to say which is right.

**"Today" is the workspace's own today.** Every date comparison runs in
`workspaces.timezone`, not in UTC, and so does the bucketing of a trend. A
company whose day ends at local midnight would otherwise see Friday evening's
work counted against Saturday.

That column is free text with a length check and nothing asserting it names a
real zone, which is a gap that predates this phase and that this phase is the
first to read. An unreadable value falls back to UTC with a WARN rather than
taking a whole workspace's reporting away. Validating it on write is hardening
work.

**Every percentage and average is computed in integer or `numeric` arithmetic**,
with the denominator guarded, exactly as the progress statement does. No floating
point, so an all-finished figure reads a hundred rather than ninety-nine, and an
empty denominator reads zero rather than null.

**Trends are dated by `tasks.completed_at`, with a known caveat.** A check
constraint holds that column consistent with status, so it is exact and cheap to
index. It is also rewritten: reopening a finished task clears it, and that task
then leaves the historical bucket it was once counted in. The append-only truth
is in `activity_logs`, but reaching completions there means filtering `jsonb`
metadata with no index behind it, which this document defers with the rest of the
query tuning. Revisit the two together.

## Business rule: system statistics

**Implemented in phase nine.** The requirements name "system statistics" under
the admin panel and define nothing at all. These definitions are ours, they sit
here beside the report definitions for the same reason those do, and every query
in that phase is written against them.

Every figure counts **live rows only**: a soft-deleted account, workspace, team,
project, task or attachment is gone rather than flagged, per the convention above.
None of them carries a workspace predicate, which is what makes them platform
statistics and why `admin:read_system` gates them.

| Figure | Definition |
| --- | --- |
| **accounts** | live accounts, and the split across `PENDING_VERIFICATION`, `ACTIVE` and `DEACTIVATED`, every value present including those nobody holds |
| **locked** | live accounts whose `locked_until` is still in the future. A different fact from `DEACTIVATED`, and usually the one somebody is looking for |
| **workspaces** | live workspaces, split `ACTIVE` and `ARCHIVED`. An archived workspace is frozen, not gone, so its contents are still counted |
| **memberships** | every `workspace_members` row. Larger than the account count whenever anybody belongs to more than one workspace |
| **teams, projects, tasks** | live rows across every workspace, projects and tasks each split by status with every value present |
| **overdue** | the phase eight definition unchanged: open, dated, and that date already past. Finished work is never overdue however late it was |
| **storage** | the count and summed `size_bytes` of live attachments |
| **recent** | accounts created, accounts that signed in, and audit rows written, each within a trailing window capped by `app.admin.max-stats-window-days` |

**The overdue count is measured in UTC, and it is the one figure in the platform
that is not computed in a workspace's own today.** Every workspace-scoped
comparison uses `workspaces.timezone`, as the report definitions require. This
one spans workspaces in different zones and there is no single today to use, so
it says so on the response rather than leaving a reader to infer it. A workspace
that wants its own answer has the dashboard for it.

**The storage figure understates what is actually stored.** A soft-deleted
attachment keeps its object until the byte purge that does not exist yet, so the
store is always at least this large. A figure that quietly included deleted files
would disagree with the listing an administrator can actually see, so the gap is
left visible rather than papered over.

**Nothing operational belongs here**: no uptime, no memory, no connection-pool
figures, no request rates, no error counts. Those are not database questions.

## Business rule: project progress

**Implemented in phase five. The column was added in `V5` and left at zero until
there were tasks to derive it from.**

The requirements list Progress as a project field but do not say who sets it.
Letting a person type it guarantees it will be wrong, so it is derived.

Over the project's non-deleted tasks:

```
contribution(t) = 1                              if t.status = DONE
                = doneSubtasks(t) / subtasks(t)  if t has live subtasks
                = 0                              otherwise

progress = floor(100 * sum(contribution) / count(tasks))   0 if there are no tasks
```

- **`DONE` wins over an unfinished checklist.** This is the one clarification phase
  five made to the rule as first written. A task marked done is done; averaging it
  with its leftovers would report less progress than there actually is. The
  fraction applies only to tasks that are not yet done.
- Only `DONE` counts. `IN_PROGRESS` and `REVIEW` contribute nothing on their own,
  because a percentage that moved when nothing finished would be a guess presented
  as a measurement. Partial credit comes from subtasks, which are the requirements'
  own unit of partial completion.
- Deleted tasks and deleted subtasks leave both sides of their fraction. A project
  whose only task is deleted reads zero, not undefined.
- Rounded down, so 100 means finished. Computed in `numeric`, never floating point,
  so an all-done project cannot read 99.
- Recalculated when a task or subtask is created, deleted, or moved between
  statuses, and stored on the project so reads stay cheap.

**It is written by one statement**, `UPDATE projects SET progress = (aggregate)`,
so two people finishing tasks in the same project cannot lose each other's update
and no row has to be locked. The statement lives in `projects`, which owns the
column, and its subquery names `tasks` and `subtasks`. That is the one place a
module reads another's tables, and it is a deliberate exception: computing the
number in `tasks` and handing it over needs a lock to be correct and can still
leave a permanently stale value when the loser of a race writes last.

`updated_at` on the project is deliberately not touched. Progress is derived, and
bumping the timestamp every time somebody moved a card would make "last edited"
meaningless.

## Backup and recovery

Frequency, retention, and the recovery procedure are documented in the delivery
phase, once the hosting provider is chosen. The requirements demand the
documentation but state no target values.
