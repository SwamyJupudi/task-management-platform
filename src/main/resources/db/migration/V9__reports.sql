-- V9: the indexes the dashboards and reports need. Nothing else.
--
-- NO TABLES AND NO PERMISSIONS ARE ADDED HERE, and both are worth reading
-- twice. This is the second migration since V3 to carry neither of the two
-- standing obligations that come with a new permission: map it to SUPER_ADMIN
-- in this same file, and backfill it onto the workspace roles that already
-- exist. V8 was the first, and the reason there was that a notification has one
-- audience. The reason here is different and is recorded in architecture.md:
--
--   A report is computed over the caller's project read scope, which is the
--   same scope a task listing is narrowed by. project:read_any already widens
--   it to the whole workspace. A report:read_any beside it would be that grant
--   under a second name, with its own resolution path that no test of the first
--   one covers, which is exactly the argument that produced no task:read_any in
--   V6 and no comment:read in V7.
--
-- Phase eight is read-only. It adds no table, no column, no trigger, no
-- constraint and no seed row. Every figure it returns is derived at request
-- time from what phases three to seven already store, so the only thing this
-- file can usefully do is make those derivations cheap.
--
-- Every index below is partial on deleted_at IS NULL where the table is
-- soft-deletable, following the convention database.md states. activity_logs is
-- append-only and never soft-deleted, so its index is not partial.

-- ---------------------------------------------------------------------------
-- tasks
--
-- The existing indexes serve one project (tasks_project_status_idx), one person
-- across the estate (tasks_assignee_status_idx), and the calendar
-- (tasks_workspace_due_date_idx). None of them leads with the workspace and
-- groups by status, which is what every workspace-wide figure in this phase
-- does, so none of them can be used for it.
-- ---------------------------------------------------------------------------

-- Workspace-wide status distribution, and the open-task count that is the same
-- query asked with one predicate.
CREATE INDEX tasks_workspace_status_idx
    ON tasks (workspace_id, status) WHERE deleted_at IS NULL;

-- Employee workload, and the employee dashboard's own counts. The existing
-- tasks_assignee_status_idx has no workspace leading column, so it cannot
-- narrow to one workspace first.
CREATE INDEX tasks_workspace_assignee_status_idx
    ON tasks (workspace_id, assignee_user_id, status) WHERE deleted_at IS NULL;

-- The completed half of the productivity trend, and "completed in period" on
-- the workload report. Doubly partial: only finished tasks carry the column at
-- all, and a check constraint guarantees that, so this index holds one row per
-- completed task rather than one per task.
CREATE INDEX tasks_workspace_completed_at_idx
    ON tasks (workspace_id, completed_at)
    WHERE deleted_at IS NULL AND completed_at IS NOT NULL;

-- The created half of the same trend, which nothing indexes today.
CREATE INDEX tasks_workspace_created_at_idx
    ON tasks (workspace_id, created_at) WHERE deleted_at IS NULL;

-- Overdue and upcoming narrowed to one project, or to the list of projects a
-- caller reaches. tasks_workspace_due_date_idx serves the workspace-wide case
-- and is left alone.
CREATE INDEX tasks_project_due_date_idx
    ON tasks (project_id, due_date) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- subtasks
--
-- One index, for the personal checklist count on the employee dashboard. The
-- existing subtasks_assignee_status_idx has the same missing leading column its
-- task counterpart has.
-- ---------------------------------------------------------------------------
CREATE INDEX subtasks_workspace_assignee_status_idx
    ON subtasks (workspace_id, assignee_user_id, status) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- projects
--
-- Team performance groups a workspace's projects by team and by status.
-- projects_workspace_status_idx from V5 leads correctly but does not carry the
-- team, so the grouping would sort rather than scan the index.
-- ---------------------------------------------------------------------------
CREATE INDEX projects_workspace_team_status_idx
    ON projects (workspace_id, team_id, status) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- activity_logs
--
-- "Recent activity" on the employee dashboard asks for one person's rows,
-- newest first, inside one workspace. activity_logs_workspace_created_idx
-- serves the administrator's whole-workspace browse and has no actor column,
-- so it would read every row in the workspace to find one person's.
--
-- Not partial: activity_logs is append-only and has no deleted_at. The trigger
-- from V7 is what makes that true.
-- ---------------------------------------------------------------------------
CREATE INDEX activity_logs_workspace_actor_created_idx
    ON activity_logs (workspace_id, actor_user_id, created_at DESC);
