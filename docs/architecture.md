# Architecture

Decisions recorded here have been reviewed and approved. Anything still open is
marked **Proposed** and must not be implemented until it is agreed.

The requirements document is `project-requirements.pdf` in this folder. Where a
decision is ours rather than the document's, that is stated.

## Shape

A **modular monolith**: one deployable unit, one database, hard internal
boundaries. The requirements describe a single product, a single team, and one
transactional domain. Nothing in them justifies distributed services, and the
cost of distribution would be paid for no benefit.

The stack is Spring Boot on Java 17, PostgreSQL, and a React frontend in
TypeScript. These are our implementation choices. The requirements document
names no technology.

## Modules

The module list is taken directly from the requirements: `auth`, `users`,
`workspaces`, `teams`, `projects`, `tasks`, `subtasks`, `comments`,
`attachments`, `notifications`, `activity`, `reports`, `admin`.

Each module is one package with three layers inside it.

| Layer               | Responsibility                             | Visibility      |
| ------------------- | ------------------------------------------ | --------------- |
| Controller          | HTTP, request and response shapes           | Package-private |
| Application service | Transaction boundary and business rules     | Public          |
| Repository, entity  | Persistence                                 | Package-private |

**The application service is the only way in.** A module never reaches into
another module's repository or entity. This is the rule that keeps the monolith
modular, and the one worth defending in review.

## Cross-module effects

Auditing and notification are needed by nearly every mutation. Calling them
directly would make them a dependency of everything and would couple the whole
system to two modules.

Instead a module publishes a Spring application event inside its transaction.
The `activity` module writes the audit record and the `notifications` module
writes the notification, both after commit. Neither appears in the publisher's
imports.

## Tenancy

Shared database, with a workspace column on every scoped table. The workspace is
resolved once per request and enforced centrally, not remembered query by query.
Cross-workspace reads are prevented by construction.

The workspace comes from the request path and never from a header, so it can
never be inherited from ambient state. A record belonging to a workspace the
caller is not a member of is reported as missing rather than as forbidden,
because a forbidden response confirms that the identifier exists.

**There is exactly one carve-out**, added in phase nine: the admin panel's
platform-scoped endpoints cross workspaces by design. They are gated on a
platform role alone, never consult workspace membership, and never take a
workspace identifier as an authorization input. See *Admin panel* below, which
states the rule in full. Nothing else in the platform reads across workspaces.

## Authorization

Roles are scoped to the workspace. Each workspace owns its own role rows, seeded
when the workspace is created. The permission catalog is global, because a
permission code names a capability the application implements; only the mapping
from role to permission is per workspace. That mapping is what the admin panel
edits.

`SUPER_ADMIN` is a platform-scope role sitting above workspace authorization. A
check constraint on the role table enforces the split: platform scope requires no
workspace, workspace scope requires one. A workspace role therefore cannot leak
across workspaces.

Granting the platform role is deliberately hard to get wrong.
`PlatformRoleService.assignSuperAdmin` takes a user and nothing else, so no
caller can name a role at all, and the database independently refuses any role
that is not platform-scoped through a key on `(platform_role_id,
platform_role_scope)`. See `database.md`.

`SUPER_ADMIN` holds no membership row in any workspace and does not need one, so
platform administration does not require joining every workspace it has to
repair. Its reach is nonetheless granted the ordinary way: the seed migration
writes an explicit `role_permissions` row for every permission in the catalog,
and resolution runs the same role-to-permission query it runs for everyone else.

There is deliberately no bypass branch for `SUPER_ADMIN` anywhere in the
authorization path. A privileged shortcut is a second implementation of
authorization that no test of the first one covers, and it is exactly the branch
an attacker wants to reach. The cost of avoiding it is a standing obligation:
**every future migration that adds a permission must map it to `SUPER_ADMIN` in
the same migration.** A permission that is added without that mapping is one the
platform administrator silently does not hold.

Every action it takes is audited. That promise could not be met by phase six,
which added auditing while `activity_logs` still required a workspace on every
row; phase nine made the column nullable and recorded the platform actions it
introduced. See *Admin panel*.

Authorization has **two layers**, and both must pass.

1. **Permission** answers what the caller may do. Checked at the method boundary.
2. **Scope** answers which rows they may do it to. Applied in the query.

An employee holding the status-update permission may still only touch tasks in
projects they belong to. An admin holding the same permission plus the
workspace-wide grant may touch any task in the workspace.

The resolved permission set is read from the database on the requests that need
it and is **never cached**. A membership change, a role change and a deactivation
must take effect at once, and a cache held inside one process is already wrong the
moment a second instance starts. The hardening phase introduced the shared store
this once pointed at, and deliberately did not use it for this: Redis is there for
rate-limit counters, caching is not switched on, and there is no `CacheManager` in
the context for a `@Cacheable` to bind to. The rule is now enforced by the absence
of the machinery rather than by anybody remembering it.

Seeded roles: `SUPER_ADMIN` at platform scope, and `ADMIN`, `TEAM_LEAD`,
`EMPLOYEE` per workspace. Custom workspace roles are a later capability; the
schema supports them but none are created.

Because those per-workspace rows are written in code rather than by a migration,
adding a permission has a second obligation beside the `SUPER_ADMIN` mapping:
**the migration must also backfill the workspace roles that already exist.**
Otherwise a workspace created before the phase is permanently less capable than
one created after it, and nothing fails to say so. `SystemRole` and the backfill
are held together by a test.

## Workspace and teams

Reviewed and approved. Built in phase three.

### Workspace settings and lifecycle

Settings are columns on `workspaces`. There are four of them, they are read
whenever a workspace is rendered, and a one-to-one settings table would buy a
join and nothing else. If the set grows to where that stops being true, splitting
it is a later migration rather than a decision to take in advance.

The slug is fixed once the workspace exists. It appears in links people have
already sent each other, and renaming it would break every one of them silently.
The display name is what changes.

**Archiving and deletion are different things and are kept apart.** Archiving is
reversible and freezes the workspace: everything inside stays readable and
nothing inside may be changed. Deletion is a soft delete, which hides the
workspace and releases its slug. They also sit at different levels: archiving is
`workspace:archive`, held by the workspace administrator, because it is a
decision about work that has finished. Deleting is `workspace:delete`, granted to
no workspace role at all, because removing a workspace is platform
administration.

The freeze is enforced in one place, `WorkspaceAccessGuard`, rather than in each
service. A mutating endpoint asks for permission and activity together, so the
rule applies to modules built later without their having to remember it. It is
checked after the permission, never before, or a 409 would confirm to a stranger
that the identifier names a real workspace.

The default role is what an invitation uses when it names none. It is a setting
rather than a constant so that an administrator who wants everybody to arrive as
a team lead says so once instead of on every invitation.

### Teams

A team belongs to exactly one workspace and has at most one lead, since the
requirements say assign a team lead in the singular. The lead is nullable,
because a team between leads is an ordinary state and refusing to represent it
would mean either inventing a placeholder or refusing to let a lead step down.

Two invariants are enforced by the schema rather than by service checks, using
the same composite-key technique as the role rules above: a team member must
belong to the team's workspace, and so must its lead. See `database.md`.

That has a consequence worth naming. Removing somebody from a workspace is
refused by the database while they still lead or belong to one of its teams, so
the `teams` module has to stand down first. It does that on a
`WorkspaceMemberRemovedEvent` published *before* the membership row is deleted,
listened for inside the publishing transaction. The module that owns the rows
does the cleanup, and `workspaces` does not import `teams` to make it happen.

Leadership is cleared rather than reassigned, because choosing somebody's
replacement is not a decision that code is in a position to make. Assigning a
lead adds them to the team if they are not in it, and removing a member refuses
to strand the lead outside it; those two rules are the same rule seen from either
end.

**Team authorization is where the two-layer rule earns its keep.** A team lead
holds `team:update` and `team:manage_members`, so the permission layer admits
them for every team in the workspace. They do not hold `team:manage_any`, so the
scope layer narrows them to the teams they actually lead. An administrator holds
both and reaches all of them. Creating and deleting a team are the
administrator's alone, so a lead cannot remove the team they lead.

A team belonging to another workspace answers as missing rather than as
forbidden, and the repository is written so that the narrower question is the
only one it can answer: there is no lookup by team identifier alone.

A guard authorizes and returns nothing. It runs in its own read-only transaction,
so an entity handed back from it would reach the service already detached, and
every change made to it would be discarded at the end of the request without an
error. Each service loads the row it is about to change inside the transaction
that changes it. This was a real defect during the build, caught by the tests
rather than by review, and the rule is written down here so it is not rediscovered
the same way.

Team dashboards, workload and task statistics are named under team management in
the requirements and are not built here. They need tasks to exist, so they belong
with the other analytics in phase eight.

## Projects

Reviewed and approved. Built in phase four.

A project belongs to exactly one workspace, has at most one owner, and may belong
to at most one team. Owner and team are both nullable for the same reason the
team lead is: a project between owners is an ordinary state, and refusing to
represent it would mean either inventing a placeholder or refusing to let an owner
leave. Both are pinned to the workspace by the database rather than by a service
check, in the same way as everything else in the schema. See `database.md`.

There is deliberately **no project-level role**. The requirements describe none,
and adding one would be a second authorization model beside the workspace roles,
with its own resolution path that no test of the first one covers.

### Lifecycle

`PLANNING`, `ACTIVE`, `ON_HOLD`, `COMPLETED`, `ARCHIVED`. The requirements print
these as a chain and state no transition rules; the matrix is ours and is
approved:

| From | May move to |
| --- | --- |
| PLANNING | ACTIVE, ARCHIVED |
| ACTIVE | ON_HOLD, COMPLETED, ARCHIVED |
| ON_HOLD | ACTIVE, ARCHIVED |
| COMPLETED | ACTIVE, ARCHIVED |
| ARCHIVED | PLANNING, ACTIVE, ON_HOLD, COMPLETED |

Two properties are load-bearing. Nothing is a dead end, so archiving stays safe to
use. And nothing skips the middle: a plan that was never worked on was abandoned
rather than finished, and archiving says that honestly. A rejected move answers
409, and so does a move to the status a project already holds, because a silent
no-op would be indistinguishable from a real transition in the activity log.

A project always opens in `PLANNING`. Letting the caller choose would make the
transition rules optional, since any state could be reached by creating a project
already in it. An archived project refuses edits and roster changes and stays
readable, matching the team rule.

### Authorization, and the read scope

Projects are the first place the **read** half of the scope layer appears. The
requirements say an employee views *assigned* projects, so reading is not a yes or
no at the method boundary; it decides which rows come back, and it lives inside
the query joined to the filters with AND, so no filter can widen it.

Three things put a project in reach of somebody without `project:read_any`:
owning it, being on it, and leading the team it belongs to. A project outside that
reach answers **404, not 403**, which is the same reasoning the workspace guard
uses one level up: a forbidden response would confirm the identifier names
something real.

Write scope works exactly as it does for teams. `project:update` and
`project:manage_members` admit a caller for every project in the workspace;
`project:manage_any` is what widens them from the projects they own or lead the
team of to all of them. Creating and deleting a project belong to the
administrator, who is the project manager the requirements describe, so a team
lead cannot delete a project they run.

Belonging to a team grants nothing over that team's projects by itself. Leading
one does.

### Tags

One workspace-scoped `labels` catalog serves projects and tasks both. The
requirements call them tags on a project and labels on a task, but they are the
same thing used twice. Tagging get-or-creates a label by folded name; there is no
label administration API in this phase, and the catalog screen belongs to the
admin panel. A labels list on an edit replaces the whole set, because tags are a
set rather than a sequence of additions.

### Progress

The requirements list Progress as a project field. The rule that derives it needs
tasks, so phase four left the column at zero and phase five implements the
derivation. See *Tasks and subtasks* below.

### Sorting

A listing sorts only by an allowlist of fields. Passing a client's sort straight
through lets a query parameter probe the shape of the entity and order by columns
with no index behind them, and neither failure is visible from the response.

## Tasks and subtasks

Reviewed and approved. Built in phase five.

A task belongs to exactly one project and, through it, to one workspace. It has at
most one assignee and at most one reporter, both nullable and both pinned by the
database rather than by a service check. Moving a task between projects is not
supported: the number it is known by is allocated per project, so a move would
either break a stable identifier or renumber into a foreign sequence.

### Numbering

Tasks are known as `PROJECTKEY-1`, `PROJECTKEY-2`, from the project key phase four
made unique and uppercase per workspace. Only the number is stored; the rendered
form is composed when a task is mapped to a response, so there is no third copy of
the same fact to keep in step.

Allocation is one statement against a `project_task_counters` row: an insert with
`ON CONFLICT DO UPDATE` that increments and returns. Concurrent creators block on
the row and each leaves with a distinct number, so there is no read followed by a
write for two transactions to interleave inside. `SELECT max(task_number) + 1`
would have been exactly that race, and it passes every single-threaded test.

The counter only moves forward. **A deleted task never gives its number back**, so
a project's numbering shows gaps. That is the price of an identifier people put in
links, and it is paid deliberately. The unique on `(project_id, task_number)` is
consequently the one unique in this schema that is not partial.

### Lifecycle

`TODO`, `IN_PROGRESS`, `REVIEW`, `DONE`. The requirements print these under *Task
Views* as the columns of a Kanban board and state no transition rules; the matrix
is ours and is approved:

| From | May move to |
| --- | --- |
| TODO | IN_PROGRESS, DONE |
| IN_PROGRESS | TODO, REVIEW, DONE |
| REVIEW | IN_PROGRESS, DONE |
| DONE | TODO, IN_PROGRESS, REVIEW |

It is deliberately permissive. A strictly linear reading would forbid sending work
back from review, which is the most common real move there is, and would make a
card undraggable leftwards on the board the requirements ask for. Nothing is a
dead end, so finishing a task stays safe to do. Finishing straight from TODO is
allowed, unlike a project moving from planning to completed: a task is smaller,
and a chore that needed no visible work is an ordinary thing to tick off.

What stays refused is moving to or from review without passing through in
progress. Review is a statement about work that exists. A rejected move answers
409, and so does a move to the status a task already holds, because a silent no-op
would be indistinguishable from a real transition in the activity log.

Subtasks share the enum and the matrix. They are the same four columns on the same
board, and a second state machine with the same states would be a second thing to
keep in step.

### Subtasks

A separate table rather than a self-referencing task, because the requirements list
SubTask as its own entity with a narrower field set: completion, assignee, status
and due date. They also name completion and status separately, and those are one
fact, so **completion is `status = DONE`**, recorded with a timestamp the database
holds consistent with the status. Two independent fields would have needed a rule
for what a completed subtask still in review means.

A subtask carries no description. The requirements show subtasks as a checklist of
titles, and a body field would be inventing a requirement.

Authorization is entirely the parent task's: editing the checklist needs
`task:update`, ticking an item off needs `task:change_status`, and there is no
subtask permission family, because a subtask is part of a task rather than a thing
to hold rights over separately.

### Visibility

**A task is visible exactly when its project is.** Nothing else. That composes with
`Workspace → Team → Project → ProjectMember` by reusing it rather than restating
it, which is what keeps the two from drifting apart. There is deliberately no
`task:read_any`: `project:read_any` already widens both at once.

The assignee is a foreign key into `project_members`, so a task cannot be assigned
to somebody outside the project holding it. "Tasks assigned to me" is therefore a
subset of "tasks I can see" by construction rather than by a check, and My Tasks
cannot become a way past the scope rule.

A task in a project the caller cannot reach answers 404, the same as one from
another workspace.

### Write scope

Without `task:manage_any`, a caller may change a task if they are its assignee, its
reporter, the owner of its project, or the lead of that project's team. An employee
therefore edits their own work and the tickets they raised; a lead reaches
everything in the projects they run; an administrator holds the workspace-wide
grant and reaches all of them.

Assignment is its own permission, `task:assign`, which an employee does not hold:
the requirements give them create, update and status changes and describe no
assignment. Deletion is `task:delete`, held by the administrator alone, so a team
lead cannot remove work in a project they run, exactly as they cannot delete the
project.

### Dependencies

The requirements name TaskDependency and say nothing about kinds of dependency, so
this is a single blocking relationship with no type column. Adding BLOCKS, RELATES
and DUPLICATES would mean inventing semantics for each.

Both ends are keyed to the same project, which makes a cross-workspace dependency
unrepresentable and closes an information leak at the same time: a cross-project
dependency would render a blocker's identifier to somebody who cannot see the
project it lives in. Widening this later breaks no existing row.

Self-dependency and duplicate pairs are refused by constraints. Cycles cannot be
stated as a constraint, so before inserting, one recursive query asks whether the
task is already reachable from its proposed blocker. The check and the insert are
taken under a transaction-scoped advisory lock on the project, because without it
two requests could each see a graph with no cycle and together close one.

Nothing prevents a blocked task from being started or finished. The requirements
state no such rule, so the relationship is surfaced and not enforced: the response
carries both directions and a `blocked` filter exists.

### Progress

Derived, not typed. Over a project's live tasks, a task counts one when it is
`DONE`, otherwise the share of its live subtasks that are done, otherwise zero, and
the whole is a percentage rounded down. **`DONE` wins over an unfinished
checklist**, which is the one clarification this phase made to the rule phase four
recorded: a task marked done is done, and averaging it with its leftovers would
report less than the truth.

It is written by one statement inside the transaction that changed the work, so two
people finishing tasks in the same project cannot lose each other's update and no
row has to be locked. That statement lives in `projects`, which owns the column,
and its subquery names the task tables. **This is the one place a module reads
another's tables, and it is deliberate.** The alternative, computing the number in
`tasks` and handing it over, needs a lock to be correct and can still leave a
permanently stale value when the loser of a race writes last.

### Module boundaries

`projects` publishes one facade rather than opening its package. Tasks need four
things from it: the read scope a caller has over projects, the facts needed to
authorize inside one project, the projects belonging to a team, and the trigger
that re-derives progress. Every one of them answers with values rather than
entities, because a guard runs in its own read-only transaction and an entity from
it would arrive detached.

The label catalog moved out of `projects` into its own package when tasks arrived.
It had lived there while projects were its only user, and leaving it would have
meant either `tasks` reaching into another module's table or a second copy of the
folding rules. `TaskAccessGuard` is public for the same kind of reason: `subtasks`
is a module of its own and authorizes through the parent task, and a second copy of
those checks would be worse than one widened class.

Deleting a project soft-deletes its tasks and their subtasks, on the event. Leaving
them live and filtering at read time is worse than it sounds: an administrator's
listing narrows on nothing, so a removed project's work would keep appearing in
their board and calendar with no project to click through to.

## Comments, attachments and activity

Reviewed and approved. Built in phase six.

Three modules, one migration, and one rule running through all of them that is the
opposite of the rule every phase before it followed.

### People columns key to `users`, not to a membership

A task keys its assignee to `project_members` and its reporter to
`workspace_members`, so removing somebody is refused until the tasks module stands
them down. The people columns in this phase key to `users` instead: a comment's
author, an attachment's uploader, a mention's subject, an audit row's actor.

**A comment has to outlive its author leaving the workspace.** Deleting the words
somebody wrote because they changed team would destroy the discussion the
requirements ask us to keep, and an audit row whose actor could vanish would not be
an audit row. Because `users` rows are only ever soft-deleted, the key holds
forever. The consequence is worth stating plainly: **this is the first phase whose
modules need no cleanup listener on a membership change at all**, and that is the
schema's doing rather than a choice a service makes.

### Comments

Flat and task-scoped. The requirements list add, edit, delete, mentions and comment
activity and describe no replies, so there is no parent column; a thread would bring
ordering and depth rules nothing asked for.

**Editing is author-only, always, including for a holder of `comment:manage_any`.**
An administrator may remove somebody's words; nobody may rewrite them and leave them
attributed to the person who wrote them. That asymmetry is the point of the grant:
it widens deletion and nothing else.

Deletion widens the ordinary way. Without `comment:manage_any` a caller may remove a
comment they wrote, or one on a task in a project they own or lead the team of,
which is the phase-five write scope one level down and is what lets a lead moderate
the projects they run without reaching the whole workspace.

A body is text and never HTML. Escaping belongs to whatever renders it, and storing
markup would make that table the place an injected script lives.

### Mentions

The canonical form in a body is `@[user:<uuid>]`, and **the server parses it. The
client does not send a list.** A supplied list is a second statement of the same
fact, and the two disagree the moment somebody edits the text and not the list,
which produces either a notification for a name no longer in the comment or silence
for one that is. No display name is stored in the body either, so a rename does not
leave stale text in somebody's words.

**A mention of anybody who cannot already see the task is refused**, with 400 and a
message naming them, rather than accepted and dropped. Accepting it would tell the
writer their message was delivered when no notification will ever be sent, and a
silent drop is the kind of failure nobody reports. Reachability is the existing rule
and not a new one: a member of the task's project, its owner, the lead of its team,
or somebody holding `project:read_any`.

An edit rewrites the set wholesale rather than computing a difference, because the
body is the record and the rows are derived from it. Only the people an edit *newly*
names are announced, so fixing a typo does not notify everybody a second time.

### Attachments and the storage port

The requirements say files must be held in cloud or object storage and not on the
application server in production. **No provider has been chosen**, and choosing one
here would have meant an unreviewed decision and a new SDK dependency arriving with
a feature. So this phase ships `FileStore`, a port in the shape `MailSender` already
established, and one implementation that writes to local disk for development and
tests.

`StorageConfig` **fails at startup** rather than falling back: local storage under
the `prod` profile is refused, and so is a provider that has no implementation. A
fallback would work, would pass every test, and would quietly put customer files on
an ephemeral disk that the next deployment discards.

`presignedUrl` is what keeps the eventual swap a substitution. A store that can
issue a signed URL gets one and the download endpoint redirects; one that cannot
answers empty and the endpoint streams. The client sees one address either way. The
local store cannot, so the streaming path is the one under test rather than the
theoretical one.

Uploads are validated in four steps, none of which consults anything the client said
except to phrase an error: size, then the content type **detected from the leading
bytes**, then that type against an allowlist, then the filename. The storage key is
`workspace/<id>/task/<id>/<uuid>` and contains nothing a user supplied, so a hostile
name cannot influence where bytes land; the name is stored in a column, where it is
data rather than an instruction.

**SVG is deliberately off the allowlist**, along with HTML and XML, all three caught
by one rule: anything beginning with a markup delimiter. An SVG is a script-carrying
document that browsers execute. Downloads are also served as attachments with
`nosniff`, so that rule is the second lock rather than the only one. Office
documents are recognised by looking inside the archive for the entry each format
carries, which the JDK's own zip reader does at no cost in dependencies. A CSV is
stored as text, because a CSV and a text file are indistinguishable from their bytes.

A file belongs to a task and optionally to a comment. Both are columns rather than a
polymorphic owner pair, because a polymorphic key cannot be a foreign key and would
move referential integrity into the service layer. Files are uploaded to the task
first and claimed by a comment when it is written, which keeps the upload a plain
multipart request with no JSON part beside it; adoption refuses anything the caller
did not upload, anything on another task, and anything already claimed.

**Soft deleting an attachment does not delete the bytes straight away.** Restoring
is what soft deletion is for, and a restore that brought back a row pointing at
nothing would be no restore. `AttachmentBytePurge`, added in the hardening phase,
reclaims the object once the row has been soft-deleted for longer than
`app.storage.purge.retention` — so that setting is also the window in which a file
deleted by mistake can still be recovered, and the purge is off until a deployment
switches it on. Malware scanning arrived in the same phase and runs before anything
is stored; downloads serve only files a scanner has cleared. See *Production
hardening*.

### Activity and audit

`activity_logs` is **append only**. No update timestamp, no soft delete, no service
method that writes an existing row, and a `BEFORE UPDATE OR DELETE` trigger that
refuses both from any connection.

`database.md` records the eventual enforcement as privileges withheld from the
application role. The application currently runs its migrations under the role it
serves requests with, so a `REVOKE` today would break Flyway on the next deployment.
The trigger is the half that works under one role and is testable; the **separate
migration role and the `REVOKE` are delivery-phase work**, and both together are the
requirement.

**No prose is stored.** The requirements print "Srikanth assigned Task #123 to
Rahul" as an example of what must be recorded, not of what must be stored. A stored
sentence is a copy of the user table that goes stale the day somebody is renamed, so
the row carries structured metadata in `jsonb` and the sentence is composed when
somebody reads it, from the name that person holds then.

Rows are written **after commit, in a new transaction**, from the events phases
three, four and five have been publishing to nobody. Not one of those modules was
edited to be recorded here, which is what the event design in *Cross-module effects*
was for. The trade is real and accepted: an audit write that fails after its
transaction committed loses that record and leaves an ERROR line carrying the
correlation id. Writing inside the caller's transaction would instead make an audit
failure roll back somebody's work, which is the wrong way round for a log that is
not a legal record.

**The write happens on the activity module's own single thread, and that is a
correctness fix rather than a performance one.** A committed transaction has not
released its database connection by the time an after-commit listener runs, so
writing there holds a second one; with more concurrent writers than half the pool,
every one of them holds a connection while waiting for another and the pool
deadlocks until Hikari times out. This was not theoretical. It appeared the moment
this module started listening, as `TaskNumberingConcurrencyIT` — a phase-five test
that creates twelve tasks at once against a pool of ten — began timing out, and it
would have been a production outage under load rather than a slow test.

Handing the row to a bounded, single-threaded executor breaks the cycle: the request
returns its connection, and the write happens a moment later competing with nobody.
One thread keeps the queue FIFO, so rows are written in the order the events
happened, and the moment and the correlation id are read on the request's thread and
carried across rather than sampled on the writing one. The cost is that a row queued
when the process dies is lost, which widens the loss window the design already
accepted rather than opening a new kind of hole.

Two things are deliberately not recorded. Derived project progress, because an audit
trail is a record of what people did and progress is a consequence nobody performed.
And a subtask completion beside the status change that caused it, because they are
one fact and two rows would make a history read like a stutter.

Browsing a whole workspace needs `activity:read`, which only an administrator holds,
because that is administration. One record's history needs nothing beyond being able
to see that record, or the people working on a project could not see its own past. A
task's history includes what happened to its subtasks, comments and files, found
through the task identifier their metadata carries; the requirements print "Anil
added a comment" as an example of exactly what belongs there. That lookup is not
indexed and runs inside `project_id`, which is: an expression index belongs with the
other query tuning in the phase that has the volume to justify it.

### Module boundaries

`comments` calls one public facade on `attachments` to claim files, because that has
to validate and to fail the request when it cannot. `attachments` hears
`CommentDeleted` and takes a comment's files with it, because nothing needs an
answer. The dependency runs both ways and in two different forms, and each form
suits what it carries.

`activity` imports nothing from anywhere but event records. `TaskAccessGuard` gained
one method, `requireContribution`, which both new modules use: **contributing is not
the same as changing.** Anybody who can see a task may comment on it and attach to
it, so the write scope that narrows editing a task to its assignee and reporter is
deliberately absent there. A discussion only the assignee may join is not one.

Comments and files are addressed flat, by their own identifier, so which task they
belong to is not known until they have been loaded. Their edit and delete paths
therefore authorize inside the service rather than in the controller, which is the
one place this phase departs from the platform convention; the alternative is a
lookup, a guard, and then the same lookup again.

## Notifications

The requirements name six triggers. Five of them are things a person did and were
already being published as events by the phases that own them; the sixth is the
passage of time and has no event, because nobody performs it.

| Type | Raised by | Who is told |
| --- | --- | --- |
| `task.assigned` | `TaskAssigned` | The new assignee |
| `task.status_changed` | `TaskStatusChanged` | Assignee and reporter |
| `task.deadline_approaching` | The scan | The assignee |
| `comment.created` | `CommentCreated` | Assignee and reporter of the task |
| `comment.mentioned` | `UserMentioned` | The person named |
| `project.member_added` | `ProjectMemberAdded` | The person added |
| `project.status_changed` | `ProjectStatusChanged` | Every member, plus the owner |

Three rules run through all of them. **The actor is never a recipient**, because
being told what you just did is how a feed becomes noise. **One action is one
notification per person**, so being both assignee and reporter earns one row.
**A mention beats the comment it is in**, since they are the same fact told twice.

Recipients are relationships rather than permissions. Everybody chosen can
already see what the message describes, so a notification cannot widen anybody's
access.

### No permission code

Every other module names a permission at its controller. This one names the
recipient instead, and the difference is deliberate: a notification has exactly
one audience, so a `notification:read` grant would be held by everybody and would
gate nothing. The precedent is the absence of `comment:read`.

There is **no administrative read**, at any role including the platform
administrator. Nobody reads somebody else's feed. The administrative question,
who was told what, is the audit trail's, and it already answers it.

Membership is still enforced: the workspace guard answers 404 for a workspace the
caller has nothing to do with, exactly as everywhere else.

### Writing, and what it costs

After commit, in a new transaction, on the module's own single thread, which is
the phase-six pattern copied rather than shared. Sharing one thread with the
audit trail would let a slow recipient lookup delay an audit row, and audit rows
are the ones that must not be lost. A notification queued when the process dies
is lost, which is the same bargain phase six made and a better one here: a lost
message is one somebody did not get, not a hole in a record.

### Access that goes away

A notification must not outlive the access it implies, which is the opposite of
the phase-six rule for comments. Leaving a workspace, leaving a project, or
having an account removed deletes the rows, hard rather than soft: there is
nothing to restore, and somebody who comes back needs the current state of the
work rather than a stale message.

Access can also be lost without any membership changing. A team lead reaches a
project by leading its team, so moving the project to another team takes their
access away and publishes nothing this module could listen for. **The read path
therefore re-checks visibility** and renders a row without its task name and
without a link rather than hiding it. That check is what makes the feed safe;
the cleanup listener is what keeps it tidy.

### The deadline scan

One scheduled job, daily at seven by default, and the only one in the platform.
Daily rather than hourly because an approaching deadline is a once-a-day fact.

It is idempotent by construction: every row carries a dedupe key of the task and
the due date it was sent for, and a partial unique index refuses a second one. A
re-run after a crash writes nothing; a due date that moves produces a new key and
notifies again, which is right, because it is a new deadline.

It pages, since it is the only query in the platform whose size grows with the
whole estate rather than with one workspace.

**One instance at a time, by PostgreSQL advisory lock.** The lock is taken and
released on a single connection held open for the whole run, because a
session-level lock belongs to the connection that took it: releasing through a
second pooled connection would release nothing and report success. Closing the
connection is the backstop. This needs no new dependency, which a
scheduler-locking library would.

Delivery is polling. Server-sent events remain a later swap and the API shape
does not preclude one.

## Dashboards and reports

One module, `reports`, holding both. The requirements' backend module list names
`reports` and does not name `dashboard`; the frontend list names both, because a
dashboard is a screen. A dashboard figure and a report figure are the same query
asked at a different width, so two backend modules would mean two
implementations of "overdue" with nothing holding them together. The controllers
are split by audience, the services by subject, and the definitions live in one
place.

### Facades, not a documented SQL exception

Reports aggregate over tasks, subtasks, projects, teams and memberships, which
puts direct pressure on the rule that a module never reaches into another
module's repository or entity. There is exactly one approved exception today,
the project-progress statement, and it was justified by a correctness argument
about races that does not apply to a read.

**No new exception was taken.** Each owning module publishes a read-only
analytics facade that answers with value records and does its aggregation in its
own SQL: `TaskAnalyticsFacade`, `SubtaskAnalyticsFacade`,
`ProjectAnalyticsFacade`, `TeamAnalyticsFacade`, `WorkspaceSettingsFacade`.
`reports` composes those answers and resolves identifiers to names.

This works without cross-module joins because every grouping the requirements
ask for groups by a column the owning module already holds: tasks by status,
priority, project, assignee and date; projects by status and team; teams by their
own roster. `reports` stitches a project identifier to a key and a user
identifier to a name through the existing bulk lookups, which are map lookups
rather than joins and are already the platform's answer to N+1.

The precedent is exact. `TaskNotificationFacade` is a second, read-only,
non-authorizing facade sitting beside `TaskAccessGuard`, created for this reason
in phase seven. The cost is real and worth naming: five new facades and a dozen
new repository methods, which makes the phase look wide rather than deep.

### Scope is inherited, so no permission was added

Every project-derived figure is computed over `ProjectScope`, the same value the
task listing narrows by, obtained from `ProjectAccessFacade.readableScope`.
`project:read_any` widens a report to the whole workspace exactly as it widens a
task listing.

There is deliberately no `report:read` and no `report:read_any`. A code every
working role would hold gates nothing, and a second read grant beside
`project:read_any` would be a parallel model with its own resolution path that no
test of the first one covers. That is phase five's argument for no
`task:read_any` and phase six's for no `comment:read`, restated.

The scope predicate is built **into** each aggregate query and joined to the
filters with AND. It is never applied to a result afterwards. A predicate applied
after aggregation would be a leak that returns 200 and looks correct, which is
why `ReportVisibilityIT` is the test to keep honest here.

The administrator's dashboard requires `project:read_any`, plus `member:read` and
`team:read` for the two headcounts. A caller without the first is refused rather
than narrowed: a workspace-wide figure computed over one person's projects is a
wrong number rather than a discreet one.

### The team dashboard is not narrowed per viewer

It requires `team:read`, plus either leading that team or holding
`project:read_any`; anybody else gets 404, matching the rule that an unreachable
record is reported as missing. The figures are then over the whole team, whoever
asked.

This is the one deliberate departure from the scope rule in the phase. A team
dashboard narrowed per viewer would hand two people different numbers under the
same heading and label both "team performance". A partial answer is worse than a
refusal here, so the gate is narrower instead of the figures.

### The definitions

The requirements name these figures and define none of them. These are ours, and
`database.md` carries them in full beside the progress rule:

- **open** - any status but `DONE`, not deleted
- **overdue** - open, dated, and that date already past. Finished work is never
  overdue however late it was
- **upcoming** - open, dated, due between today and the lead window inclusive
- **completed** - `DONE`, dated by `completed_at`
- **progress** - the existing derived column, read and never recomputed
- **workload** - per person: open, in progress, overdue, completed in the period,
  and effort summed over open work only

"Today" is computed in the workspace's own timezone, not in UTC. A dashboard that
called work overdue at midnight UTC would be wrong for most of a company for most
of the day. `workspaces.timezone` is free text with only a length check behind
it, so an unreadable value falls back to UTC with a WARN rather than taking a
whole workspace's reporting away; validating the column on write is recorded
under *Still open*.

**Overdue is now expressed in two places**: the `overdue` filter on the task
listing, and the reports' own predicate. If they ever disagree, a count and the
list it links to differ by a row with nothing to say which is right.
`ReportDefinitionsTest` pins the rule and `ReportAccuracyIT` checks the two paths
against each other on real data.

### Trends read `completed_at`, with a stated caveat

`completed_at` is held consistent with status by a check constraint, so it is
exact and cheap to index. It is also rewritten: reopening a finished task clears
it, so that task leaves the historical bucket it was once in.

The alternative source is `activity_logs`, which is append-only and therefore the
true history, but reaching completions in it means filtering `jsonb` metadata
with no index behind it, which `database.md` deliberately defers. The caveat is
written down rather than discovered later.

### Nothing is cached, and nothing is precomputed

No materialized view, no rollup table, no scheduled aggregation, no application
cache. This follows the standing decision that caching is hardening-phase work
behind a shared cache, and it keeps the phase from shipping a second copy of the
truth that can go stale. The cost is that every figure is computed at request
time, bounded only by the window and page caps in `app.reports.*`; the first
evidence that it is too slow will be production, and the answer then is the
shared cache rather than a rollup table added now.

Because nothing is cached, every figure is as current as the transaction that
read it, and the panels of one response are mutually consistent because they
share that transaction. Two separate requests may legitimately disagree if
somebody finished a task between them.

### Nothing is published and nothing is consumed

`reports` is read-only. No event, no listener, no executor, no scheduled job, no
advisory lock, and every service method is `@Transactional(readOnly = true)`.

`ProjectEvents.ProjectProgressChanged` stays **unconsumed**, and that is a
decision rather than an oversight. Its javadoc anticipated that phase eight's
dashboards would want to hear about real changes; with no cache to invalidate
there is nothing for a listener to do. The event stays where it is, useful the
day a cache arrives.

## Admin panel

Reviewed and approved. Built in phase nine.

Four of the eight items the requirements name under *Admin panel* were already
built: the account directory and its lifecycle endpoints, the role listing, the
permission catalog, and team management. This phase built the other four and
turned on two permissions that had been seeded and unchecked since phase two.

### The `admin` module owns no table

The requirements name `admin` in the module list, so the package exists. It holds
no entity and no repository, and that is the design rather than an omission.

Every table an admin panel touches already belongs to a module that enforces
rules over it: `users` owns accounts and is the only package that may hold a
password hash, `workspaces` owns roles and membership, and so on down. A second
module writing those tables would be a second implementation of every rule
protecting them. So `admin` is the platform's second composition module, after
`reports`, and the pattern is one phase old rather than invented here.

That decides where each piece lives, and the rule is worth stating because it
looks untidy from the outside:

- **System statistics and the cross-workspace listings** live in `admin`, because
  nothing else owns them.
- **The role editor** lives in `workspaces`, at
  `/workspaces/{id}/roles/{slug}/permissions`, because that module owns `roles`
  and `role_permissions`.
- **The account verbs** live in `users` and `auth`, at `/users/{id}/...`. The two
  recovery endpoints are in `auth` specifically: it owns the single-use tokens
  and the session revocation, and putting them on `UserController` would make
  `users` import `auth`, which already imports `users`.

"Admin panel" is a screen in the frontend. It is assembled from endpoints that
sit where their rules sit. Routing every administrative call through `/admin`
would produce a module that reads seven other modules' tables, which is the one
rule worth defending in review.

### Platform scope is the carve-out to tenancy, and it is narrow

*Tenancy* above says cross-workspace reads are prevented by construction and that
the workspace comes from the request path. That holds everywhere except under
`/admin`, which crosses workspaces by design and is the only thing in the
platform that does.

The carve-out is four rules, and a reviewer has exactly one thing to check per
endpoint:

1. Every `/admin` route is gated by `@perm.onPlatform` and nothing else. That
   resolver reads only the caller's platform role and never consults workspace
   membership, so administering one workspace reaches none of it however wide
   those grants are.
2. No `/admin` route takes a workspace identifier as an authorization input.
   Where one appears it is a **filter** over an already-authorized platform read,
   never a scope that widens anything.
3. Nothing under `/admin` uses `WorkspaceAccessGuard`, whose 404-for-invisible
   rule makes no sense for a caller who can see everything.
4. These routes answer **403** rather than 404, unlike every workspace-scoped
   one. That rule exists because "this workspace exists" is itself information;
   these routes name no resource, so there is nothing to conceal and a 404 would
   only make the API harder to use for the people entitled to it. The exception
   is `/users/{userId}`, where a missing account is still 404.

In one line: an admin endpoint is platform-scoped or it is not an admin endpoint.
`AdminIsolationIT` walks every one of them with the widest possible workspace
grants and asserts 403, then with a platform role and asserts 200.

### Two permissions, and why each is its own

Phases seven and eight both added none, on the argument that a grant every
working role holds gates nothing and a second grant beside an existing one is a
parallel model. Applied here that produces two rather than zero.

- **`admin:read_system`** — reading across the installation. Nothing named this
  before. It is not `workspace:read` under a second name, which at platform scope
  means "list the workspaces" where this means "count what is inside all of
  them"; and it is not `project:read_any`, which is workspace-scoped and which
  three seeded roles can hold.
- **`platform_role:assign`** — granting and revoking `SUPER_ADMIN` over HTTP,
  which until this phase was possible only by redeploying. Deliberately not part
  of `user:update`, so a future custom platform role can be given account
  administration without also being given the ability to mint its own peers.

Both are mapped to `SUPER_ADMIN` in `V10`, as every migration adding a permission
must. **Neither is backfilled onto any workspace role**, which is a third answer
to that obligation beside "backfill by slug" and "no permission at all". The
precedent already existed: `workspace:delete` has sat in the catalog since `V3`
mapped to the platform role and granted to no workspace role, because removing a
workspace is platform administration. `SystemRole` is unchanged.

**Two seeded permissions stop being dead constants in this phase**, and that is a
behaviour change rather than a new feature. `role:manage` has been granted to
`ADMIN` since phase two with nothing checking it, so a workspace administrator
can now edit roles without any grant having changed. `user:update` is granted to
no role at all, which is why account administration is reachable through a
platform role alone.

### The audit promise falls due here

*Authorization* above says of `SUPER_ADMIN` that "every action it takes is
audited from the phase that adds auditing". Phase six added auditing and could
not meet that for platform actions: `activity_logs.workspace_id` was `NOT NULL`
and no platform action happens inside a workspace. Nothing noticed, because no
platform action had an endpoint. This phase gives it several.

`V10` makes that column nullable and widens the entity-type check to add `USER`
and `ROLE`. **One audit trail, not two.** A separate `admin_audit_logs` table
would mean two answers to "what happened to this account", a second append-only
trigger, and a permanent question about which to believe.

A platform row carries a null workspace. The invariant that makes this safe is
that every workspace-scoped query filters on that column, so platform rows are
invisible to a workspace's history without one line changing, and the platform
browse asks for `IS NULL`. Both directions are asserted, because either leak is a
leak. A role edit is the one administrative row that *does* name a workspace,
because a role belongs to one, so it lands in that workspace's own history, which
is where somebody wondering why their permissions changed would look.

The append-only trigger is untouched and unaffected: it is a row trigger on
`UPDATE` and `DELETE`, so the DDL does not fire it. `AdminSchemaIT` proves that
rather than assuming it.

### The role editor replaces the whole set

`PUT`, with the complete list of codes the role should hold afterwards. The
platform made this choice once already for labels, and for the same reason: a
delta needs the client to know the current state in order to compute it, and two
administrators editing one role would silently merge into a set neither chose.

An unknown code fails the whole request and writes nothing. An empty list is
legal and means the role grants nothing, which is different from omitting the
field. The audit row carries the **difference** rather than the result, because
"what changed" is the question an audit trail answers.

A platform role cannot be edited through any workspace path, and that is the
schema's doing rather than a check: roles are looked up by `(workspaceId, slug)`,
and a platform role has a null workspace and matches no such pair.

**An edit cannot lock a workspace out of its own administration.** The service
refuses an edit that would leave the caller's own role in that workspace without
`role:manage`. The rule is deliberately narrow: it does not attempt the global
"some role somewhere must retain it", which would need a holder count on every
edit and would refuse legitimate changes to a role nobody holds. `SUPER_ADMIN` is
unaffected because it holds no workspace role, which is correct — it is precisely
the repair path the rule exists to avoid needing.

### An administrator never learns somebody's password

There is no endpoint that sets another person's password and there will not be
one. It would put a raw credential in an administrator's request body and in
their memory, and it would mean somebody knowing a password that opens an account
that is not theirs.

Instead an administrator **starts a recovery**: the ordinary single-use token is
issued and mailed to the account's own address, and redeeming it revokes every
session as any reset does. This also keeps the rule the identity phase built its
module shape around, that `auth` never sees a password hash.

The administrative entries answer 404 for an account that does not exist, where
their public counterparts deliberately answer identically either way. The public
ones are reachable without signing in and must not become a way to test
addresses; these are reached only by a caller who can already list every account.

### Four refusals, because an admin panel is where people break their own access

Enforced in the owning service rather than in a controller, so they hold however
the method is reached, and each answers 409 rather than 403: the caller is
entitled to do this in general and is refused because of the state of the world.

- You may not deactivate your own account.
- You may not delete your own account.
- You may not revoke your own platform role.
- The last holder of `SUPER_ADMIN` may not be demoted, deactivated or deleted.

The fourth matters most and nothing guarded it before. `SuperAdminBootstrap`
creates an administrator only when none exists and explicitly never resurrects a
deleted one, so an installation that loses its last one cannot be administered
until somebody edits the database by hand.

### The statistics, and what is deliberately not in them

The requirements name "system statistics" and define nothing, so the figures are
ours and `database.md` carries them in full beside the report definitions:
accounts by status and how many are locked, workspaces by status, teams,
memberships, projects and tasks by status, overdue work, attachment count and
bytes, and a trailing window of new accounts, sign-ins and audit rows.

Two things about them are worth stating.

**The overdue count is measured in UTC**, unlike every other date comparison in
the platform. Every workspace-scoped figure uses that workspace's own today; this
one spans workspaces in different zones and there is no single today to use. It
is said on the response rather than left to be inferred.

**Nothing operational is included** — no uptime, no memory, no pool figures, no
request rates, no error counts. Those belong to Actuator and the log platform,
they are not database questions, and answering some of them here would be a
second and worse monitoring surface.

Nothing is cached or precomputed, following the standing decision. These are the
platform's first queries with no tenant predicate, so that costs more here than
it did in phase eight; the bound is that the statistics are a fixed set of
counting queries with a capped window and that both listings page. The cost is
recorded under *Still open*.

### Nothing is published by this module and nothing is consumed

`admin` has no listener, no executor, no scheduled job and no cache, and every
method on its service is `@Transactional(readOnly = true)`. The writes the admin
panel offers are performed by the modules that own the rows, and those publish
their own events, which `activity` turns into audit rows exactly as it has since
phase six.

## Identity and authentication

Reviewed and approved. Built in phase two. Where the requirements document is
silent and the choice is ours, that is stated.

### Account lifecycle

Registration creates a person, not a workspace. A new account is
`PENDING_VERIFICATION` until the emailed token is redeemed, then `ACTIVE`. An
administrator may move it to `DEACTIVATED` and back. Soft deletion is a third
thing again, and means removal rather than suspension.

Workspace access is never implied by registering. It comes from an invitation, or
from a membership row an administrator creates. Only `SUPER_ADMIN` creates
workspaces.

The automatic lockout that follows repeated failed logins lives in two columns on
the user, not in the status column. A lock is a temporary decision made by the
machine and a deactivation is a durable one made by a person; merging them would
make both harder to read and would let a lock look like a punishment.

### Tokens

| Token              | Form                          | Lifetime   |
| ------------------ | ----------------------------- | ---------- |
| Access             | Signed JWT, identity only     | 15 minutes |
| Refresh            | Opaque random, hashed at rest | 14 days    |
| Email verification | Opaque random, hashed at rest | 24 hours   |
| Password reset     | Opaque random, hashed at rest | 1 hour     |
| Invitation         | Opaque random, hashed at rest | 7 days     |

The access token carries issuer, audience, subject, issue time, expiry and a
token id. It carries no address, no role and no permission. Permissions differ
per workspace and have to revoke at once, so a fifteen-minute copy of them inside
the token would be both large and wrong for up to fifteen minutes.

Rotation is a conditional update rather than a read followed by a write. Two
requests holding the same live token would otherwise both find it usable and both
mint a successor, quietly turning one session into two; the predicate is instead
evaluated under the row lock, so exactly one caller is told it claimed the token.
The loser is treated as reuse, because from inside a request a double submit and a
replayed stolen token are indistinguishable, and only one of those two readings is
safe.

The refresh token is 256 bits of secure random, stored as a SHA-256 hash. A fast
hash is the right choice here, and a password hash would be the wrong one: the
value is already full entropy, so there is no dictionary to slow down, and a work
factor would be paid on every refresh to buy nothing. Each use rotates the token
and links the replacement to its predecessor. Presenting a token that has already
been consumed revokes the entire family, on the assumption that two parties hold
it and one of them is an attacker.

The single-use tokens for verification, reset and invitation are hashed the same
way, expire, and are consumed on first redemption.

Verification and reset tokens are posted in a request body rather than read from
the URL, because a token in a URL ends up in browser history, in the referrer
header of the next navigation, and in the access log of everything in between.
The invitation preview is the one bounded exception: it takes its token as a
query parameter, because the page has to be reachable directly from a link in a
message by somebody who has no account and so cannot be asked to post anything
first. The exception is narrowed rather than waved through. The endpoint is a
read, it returns only the workspace name, the invited address and whether that
address already has an account, and redeeming the invitation is a separate POST.
The token is never written to a log on that path.

Expired and consumed rows in `user_tokens` and `refresh_tokens` are inert rather
than dangerous — nothing reads them and an expiry check is applied on every use —
but they used to accumulate for ever. `ExpiredTokenPurge`, added in the hardening
phase, removes them in bounded batches once they have been **expired** for longer
than `app.auth.token-purge.retention`. Expiry rather than consumption, and never
revocation: a revoked refresh token must stay readable while it could still be
presented, because presenting one is how a stolen rotation chain is discovered.
The purge is off until a deployment switches it on.

### Transport

The refresh token travels in a cookie that is `HttpOnly`, `Secure`,
`SameSite=Strict`, and scoped to the authentication path. The access token is
never written anywhere the browser keeps; the frontend holds it in memory. This
survives cross-site scripting, which local storage does not.

Transport is isolated behind one component so it can be replaced. If the frontend
is ever served from a different site, `SameSite=Strict` stops working and the
token has to move into the response body. That has to remain a single
substitution rather than a redesign.

Cross-site request forgery protection stays off for the bearer-token API. The
refresh cookie reintroduces the risk on two endpoints only, and `SameSite=Strict`
together with a JSON content type that forces a preflight covers them. If the
transport ever moves to the response body, this reasoning has to be revisited.

### Revocation

There is no cache anywhere in this phase. Account status is read from the
database on the requests that require it, and the permission set is read the same
way. Deactivation, a password change and a role change therefore take effect on
the next request, on every instance, with no invalidation to get wrong.

This trades throughput for correctness knowingly, and it is the decision most
likely to be revisited. When it is, the answer is a shared cache in the hardening
phase.

Logout revokes the refresh token it is given and clears the cookie. The access
token stays valid until it expires, so for at most fifteen minutes. A denylist
would close that window; it is not proposed, because the window is short and the
cost would be a lookup on every request forever.

A password change revokes every other session and issues a fresh pair to the
session that made the change. A password reset revokes every session without
exception, because the person resetting may be recovering from a compromise.

### Password handling

BCrypt at strength 12, behind a delegating encoder, so every stored hash names
its own algorithm and a later move to Argon2id is a rehash on next login rather
than a forced reset for everybody.

The policy is a minimum of eight characters and a maximum of 72 bytes, with no
composition rules. The upper bound is not arbitrary: BCrypt silently ignores
input past 72 bytes, so without it two different passwords could open the same
account. The absence of composition rules is deliberate, since the requirements
document asks for none.

### Failure handling

Wrong credentials return one generic response and always cost one hash
comparison, including for an address that was never registered, so neither the
message nor the timing reveals who holds an account. Account status is disclosed
only once the password is correct. At that point the caller already holds the
credential, so a precise message helps them and tells an attacker nothing new.

Forgotten-password and resend-verification requests are accepted without saying
whether the address exists.

Repeated failures lock the account, counted on the user row. The lock is bounded
and is never extended by further attempts, which matters more than it sounds: an
implementation that re-arms the lock on every failure lets anybody who knows an
address keep its owner locked out indefinitely, turning a defence against
guessing into a way of denying somebody their own account. One run of failures
buys one lock period; causing another means waiting the first one out. Once a
lock expires the count starts from zero, so unrelated failures weeks apart never
accumulate into one.

Escalating lock periods were considered and left out. The requirements ask for no
such policy, and it would need a counter that survives the reset, which is state
earning its keep only once there is evidence that fixed periods are insufficient.

Address-level rate limiting arrived in the hardening phase, where the build order
placed it, and still with no rate-limiting library: the whole mechanism is one
atomic Lua script against Redis. Gateway-level limiting remains a deployment's own
choice and `app.rate-limit.enabled=false` exists for a deployment that prefers to
limit there instead. See *Production hardening* below.

**Registering with an address that already has an account answers 409, and that
is an accepted risk rather than an oversight.** Sign-in, forgotten password and
resend all refuse to confirm whether an address is registered; registration
confirms it. The asymmetry is deliberate, because the two disclosures are not
worth the same. Enumeration at sign-in pairs with password guessing to reach an
account. Enumeration at registration reveals only that a corporate address is
registered, on a platform where, by the decision above, registering grants access
to nothing at all. The cost of closing it would be a signup that cannot tell
somebody their address is already in use.

The condition attached to accepting it: **the hardening phase must rate-limit
registration by address as well as by caller**, since bulk enumeration is the
only form of this that matters. **Both halves shipped**, and the by-address one is
consumed before the existence check rather than after it, which is the only
ordering that closes anything — consumed after, the limit would bound the
successful registrations and leave the disclosure unbounded.

### Module boundaries

`users` owns the user table and the entity. `auth` owns the tokens and the
authentication flows and never sees a password hash: it asks `users` to verify a
credential and is told only the outcome. `workspaces` owns membership,
invitations and the role mapping. A cross-cutting `common.security` package holds
the authentication filter, the principal, the permission expression and the
permission code constants, and is used by every module built after this one.

Authentication failures raised inside a servlet filter never reach the
`@RestControllerAdvice`, so the entry point and the denied handler produce the
same error body themselves. One shape, two producers, and a test that holds them
together.

Mail is a port. Phase two ships the interface and an implementation that logs the
link, which is enough to exercise verification and reset from end to end. A real
transport arrives when the delivery provider is chosen.

### Bootstrapping the first administrator

Nothing can be administered until one `SUPER_ADMIN` exists, and a seeded password
in a migration would be a committed secret. A startup runner reads an address and
a password from the environment and creates the account, hashing the password
with the same encoder the application uses everywhere else.

It does nothing at all when a platform administrator already exists, so a restart
never resurrects or overwrites one. If the configuration is half present, with an
address but no password or the reverse, startup fails and says which value is
missing. Silently continuing would leave an operator believing they had an
administrator when they did not.

## Production hardening

Phase ten. Nothing here is a feature; every part of it is something that has to
be true before the platform faces the internet, and most of it was named as
deferred work by an earlier phase.

### Rate limiting, in two places on purpose

Address-keyed limits are a servlet filter ordered ahead of the security chain.
That position is the point rather than a detail: authentication is not free — a
token is parsed and verified, an account status is read from the database, a
password is compared against bcrypt at strength twelve — and all of it is work an
unauthenticated caller can make the application do. A limit applied after
authentication would bound the replies rather than the work.

Account-keyed limits are **not** in the filter. The address being limited arrives
in the request body, and reading a body in a filter consumes the stream, so every
request in the application would need a caching wrapper — multipart uploads
included — to let the controller read it again. That is a permanent cost on
everything to avoid passing one string to a collaborator. `AccountRateLimitGuard`
is called from the services instead, which already have the address parsed.

Each account check is consumed **before** the work and before the answer that
would disclose anything. For registration that is the whole value of it: this
document accepted that registering with an address that already has an account
answers 409, and therefore reveals that the address is registered, on the stated
condition that the hardening phase limit registration by address as well as by
caller. Both halves now exist, and the account half is consumed before the
existence check, so an attacker gets a handful of answers about an address per
hour rather than as many as they care to ask for.

**The account limits are deliberately looser than the account lockout, and that
looks backwards.** Five wrong passwords lock an account for fifteen minutes, and
that lockout is the platform's answer to password guessing. If the per-account
rate limit were also five, a 429 would arrive before the lockout was ever
reached, and somebody mistyping their own password would be told to come back
later instead of being told their account is locked. So the login limit sits at
twice the lockout threshold: the lockout always speaks first and keeps its
behaviour, and the limit catches only what the lockout cannot — somebody working
through many accounts, or continuing past the point where the lockout has said
what it has to say.

A refusal is the platform's ordinary error body with a 429 and a `Retry-After`.
One message for every limit: a message that distinguished "too many attempts
against this account" from "too many requests from here" would confirm an address
is registered to anybody willing to trip the limit.

### Redis is a counter store, not a cache

Redis holds the rate limiter's counters. A limit has to be the same limit on
every instance or it is not a limit, and a counter is the one piece of state in
this application that is worthless the moment it is a second old, which is what
makes an in-memory store its right home rather than a table.

**Caching is not switched on, and that is the important half of this decision.**
There is no `@EnableCaching` anywhere and no `CacheManager` in the context. The
standing rule is that a resolved permission set must never be cached — a
membership change, a role change and a deactivation all have to take effect on
the next request, and `PermissionResolver` carries that rule in its own contract.
The easiest way to break it would be to leave cache infrastructure lying next to
a resolver method that looks expensive. With none present there is no annotation
to add and nothing for one to bind to, so the rule is enforced by the absence of
the machinery rather than by review.

**Redis unavailable means every request is allowed.** A rate limiter is a
protection, and one that refuses everything when its own store is unreachable has
converted a degraded dependency into a total outage. What is lost during a Redis
outage is the protection, not the service. Two things make that real rather than
nominal: the client timeouts are 250ms, and after a failure the limiter stops
consulting Redis for thirty seconds — without the second, a store that was merely
unreachable would add its timeout to every request in the application and the
fail-open would be honoured while the service was unusable anyway. For the same
reason the Redis health indicator is switched off: a dependency the application is
designed to degrade past must not be able to fail a readiness probe and have a
healthy instance pulled out of the load balancer.

The counters are keyed by a **hash** of an email address, never the address.
Redis keys appear in `MONITOR` output, in slow-log entries and on whatever
dashboard a managed provider offers, and none of those are places this platform
writes a customer's address.

### Request completion logging

One line per finished request: method, path, status, duration. Until it existed
the logs recorded only failures, because the exception handler logs a rejection
and nothing logged a success, which left the ordinary questions unanswerable.

The **query string is deliberately absent.** An invitation is accepted through
`GET /invitations?token=...`, so logging a full request line would write a live
single-use credential into the logs, where it would outlive the token and be
readable by anybody who can read logs. Health probes are logged at DEBUG rather
than INFO: an orchestrator polls them every few seconds and at INFO they would be
the overwhelming majority of the log.

### Scheduled purges, and why they are off by default

Three jobs, in the modules that own the data: expired tokens in `auth`, attachment
objects and the unscanned-attachment backlog in `attachments`. The first two are
nightly purges; the third is the hourly rescan described under *Malware scanning*.
All are **off by default in every profile**,
unlike the deadline scan, and the asymmetry with the deadline scan is deliberate — that sends
a message, these delete data, so a deployment opts into destruction rather than
discovering it has been running. The rescan destroys nothing but is off for a
related reason: it reads every unscanned file out of object storage. All three
must therefore be switched on explicitly in production, and without the two purges
the token tables and the object store grow without bound.

Both purges use thirty-day retention and bounded batches. The first run after
either is enabled has the whole history to work through, and a single unbounded
`DELETE` would hold locks on the busiest tables in the schema for as long as that
took.

Token retention is counted from **expiry**, not from issue, and the predicate is
expiry rather than revocation. A revoked refresh token has to stay readable while
it could still be presented, because presenting one is how a stolen rotation
chain is discovered; a row deleted early would turn a detected replay into a
lookup that finds nothing and merely refuses the caller, losing the signal that
the whole session should be evicted.

The attachment purge deletes **the object before the row, never the other way
round.** A row deleted first leaves bytes in the store that nothing in the schema
can name — unreachable, unattributable, still being paid for. An object deleted
first leaves a row pointing at nothing, which the next run simply deletes, since
every store reports an already-absent object as a success. One order is
self-healing and the other loses data permanently. `FileStore.delete` carries the
matching half of the contract and had to change to do so: both implementations
used to log a failure and return, which reported success to a caller that then
deleted the row. They now throw.

`AdvisoryLock` moved from `notifications` to `common.scheduling` when the second
job needed it, and the lock keys are in `LockKeys` because that class had always
said a second key belonged beside the first rather than invented at a call site —
they share one namespace across the database, and two jobs choosing
plausible-looking constants could collide, with the symptom being one of them
silently never running. `@EnableScheduling` moved to `common.scheduling` for the
same reason: it had sat on `NotificationConfig` while the deadline scan was the
only scheduled job, and that annotation's stated justification stopped being true
once two other modules needed it.

### Malware scanning

`MalwareScanner` is a port, and being a port is the requirement rather than a
design preference: naming a commercial engine here would put a licence, a vendor
and an SDK dependency into the source, and would make the choice unreviewable.
Two implementations ship. One scans nothing, for development and tests, and is
refused at startup under `prod` — the same refusal `StorageConfig` applies to
local disk, for the same reason, because a quiet fallback is how the rule gets
broken. The other POSTs the bytes to a URL.

**The upload lifecycle.** Size, then what the bytes actually are, then whether
that is a type the platform accepts, then the scan, then the name. The scan is
last of the content checks because it is the only one that leaves the process, so
every cheap local refusal happens first and a file the platform would never
accept is never sent anywhere. It runs **before anything is stored**, so an
infected upload leaves no object and no row and nothing for a purge to find.
Clean proceeds; infected answers 400 with a message that says the file was
refused and nothing about what was found — naming the signature would hand an
attacker an oracle for tuning a payload against the engine — and a scan that
could not be completed answers 503 and refuses the upload. That last one is the
fail-closed decision, and accepting the file instead would be the single choice
that makes the whole feature pointless, because anybody who could take the scanner
down could then upload anything.

`attachments.scan_status` records the outcome, and downloads serve only `CLEAN`.
The guard is written as "is it approved" rather than "is it rejected", so a state
added later is refused by default rather than served by default. `PENDING` is where every
attachment that predates this phase starts, and where anything an asynchronous
engine has accepted but not yet cleared would sit; today's upload path never
writes it, because that scans before it stores, so a file which cannot be cleared
never becomes a row. `SCANNING` is reserved for an asynchronous adapter, which the
download guard therefore already refuses — such an adapter is safe by
construction rather than needing this method revisited. `REJECTED` is
for the rejection an operator records after the fact, quarantining a file a later
signature update flagged without deleting the record that it was there. Both
download routes — the streamed one and the presigned redirect — pass through the
same guard, so a file that is not clean can be listed and have its metadata read
and still cannot be fetched.

**`V12` leaves every existing row `PENDING`, and does not backfill them to
`CLEAN`.** This was decided the other way round first and corrected, so the
reasoning is worth keeping. Backfilling to `CLEAN` avoids a visible change —
every existing file keeps downloading — but it writes a falsehood into the column
whose entire purpose is to record whether something inspected a file. Nothing had
inspected them. It is also a falsehood that gets harder to find with time: once a
row says `CLEAN` the only thing distinguishing it from a genuinely cleared row is
a null timestamp nobody is obliged to look at. A platform that scans uploads and
serves an unscanned backlog as though it were scanned has the appearance of the
control without the control.

The database enforces this rather than trusting the code to: a check constraint
requires `scanned_at` on any `CLEAN` row, so a backfill, a later migration or a
stray `UPDATE` that tried to mark files clean without scanning them is refused
outright. There are exactly two paths to `CLEAN` in the application —
`Attachment.createScanned` after an upload scan passes, and
`Attachment.markScanClean` after the rescan gets a clean verdict — and both stamp
the timestamp in the same breath as the status.

**What this costs an existing installation, stated plainly: every attachment
becomes undownloadable until it has been scanned.** Rows, metadata and listings
are untouched, because the guard is on the bytes rather than on the record, so
nothing disappears from a task — the download answers 409 while the file is
unscanned. `AttachmentRescan` is what clears that backlog: it reads each unscanned
file back out of object storage, scans the bytes as they actually are, and either
promotes it to `CLEAN` or marks it `REJECTED`. A file the scanner cannot reach
stays `PENDING`, which is the fail-closed direction — an unreachable scanner must
never be the reason something becomes servable. It is hourly rather than nightly,
because a backlog is something an operator is waiting on, and it is off by
default: it destroys nothing, but it reads every unscanned file out of the store
and sends it to a scanner, which on a large backlog is a lot of egress arriving
the moment a deployment restarts. **A deployment upgrading a populated
installation has to switch it on**, and there is deliberately no shortcut that
marks the backlog clean without looking at it.

#### What a production deployment has to provide

A scanning service reachable over HTTP from the application, satisfying this
contract — small on purpose, because it is the part other people implement:

- `POST` with the file as the body and `application/octet-stream`.
  - `200 OK` — clean. The body is ignored.
  - `422 Unprocessable Content` — infected. The first line of the body, trimmed
    and truncated to 200 characters, is recorded as the signature; an empty body
    is accepted and recorded as unnamed.
  - **Anything else is an error**, including 5xx, a timeout, a connection failure,
    and any 2xx that is not 200. The upload is refused rather than accepted
    unscanned. A shim that answers something plausible but undocumented therefore
    fails closed.
- `GET` answers the startup probe. Anything that is not a 5xx counts as reachable,
  so a shim implementing only `POST` may answer 405.

A status code rather than a JSON verdict because a body would need a schema, this
application would have to parse it, every shim would have to reproduce it, and the
failure mode of getting it slightly wrong would be a file waved through. 422
rather than 400 because the request was well formed; the content is the problem.

No credential is read from configuration, matching object storage: a scanning
endpoint needing authentication should be reached over a network the deployment
controls, or fronted by a shim holding the credential itself.

**What failing closed costs, stated plainly: the application cannot start while
its scanner is down.** The scanner is a hard startup dependency under `prod`, so
it must come up first, and a rolling restart during a scanner outage leaves
instances unable to return until it comes back. There is deliberately no flag to
relax the startup check, because a flag for it would be set once during an
incident and never unset.

### Headers, compression and container sizing

Four headers were added to the four the foundation phase set. One writer produces
the content security policy rather than two registrations, because a browser
enforces the **intersection** of two policy headers rather than choosing between
them, so a strict policy for the API and a looser one for the documentation
console would give the console neither. The API policy is `default-src 'none'`,
which costs nothing: every response but the console is JSON or a file served as
an attachment, and none of it is a document a browser renders.

`Cross-Origin-Resource-Policy: same-origin` was checked rather than assumed. It
refuses cross-origin *no-cors* loads — an `<img>` or `<script>` pointed at this
API from another origin — and does not touch a CORS request. The single-page
application fetches attachment content through its authorized client and turns it
into a blob rather than embedding it, so nothing it does is affected. A client
that ever needs a bare `<img src>` against a download will be refused by this
header, and `same-site` is the value that would allow a sibling origin.

Compression is on everywhere, with an allowlist of text-shaped types. What it
leaves out is the point: an attachment download streams stored bytes, and images,
PDFs and archives are already compressed, so running them through gzip would
spend CPU to make them very slightly larger.

Tomcat's thread ceiling is set **below** its own default of 200, and the reason is
the connection pool rather than the CPU. A request that touches the database needs
one of `DB_POOL_MAX_SIZE` connections, so threads far in excess of the pool do not
add throughput — they add requests queued inside the application, holding a thread
and a socket, where a shorter queue would have applied backpressure at the front
door instead. Raise it and the pool together or neither.

## Cross-cutting infrastructure

Built in the foundation phase, used by every module afterwards.

- **Migrations.** Flyway owns the schema. Hibernate is set to `none` and may
  never create or alter anything. Each module brings its own migration.
- **Errors.** One `@RestControllerAdvice` maps every exception to one body
  shape. Deliberate errors extend `ApplicationException` and carry a message
  written for the user. Everything else is a defect and returns a generic
  message. Internal detail is logged, never returned.
- **Correlation.** Every request gets an id, which appears in the logging
  context, in the error body, and in the `X-Request-Id` response header. An
  inbound id is accepted only if short and alphanumeric, so it cannot be used to
  forge log lines.
- **Logging.** Structured JSON on stdout in production, four levels. Passwords,
  tokens, keys, and personal data are never logged. Since phase ten every finished
  request also logs one line with its method, path, status and duration — without
  the query string, because an invitation token travels in one.
- **Health.** Actuator health, liveness, and readiness, without internal detail.
- **API document.** Generated by springdoc from the controllers, so it cannot
  drift from the code.

## Frontend

Feature folders as the requirements specify. Server state in TanStack Query,
which supplies caching, pagination, and loading and error states. Session and
permission set in a small store. Route guards check authentication and a
permission code, so the same permission catalog drives both the API and the
interface. The API client is generated from the OpenAPI document.

Starts alongside the identity phase.

## Build order

| Phase | Scope                                                                  |
| ----- | ---------------------------------------------------------------------- |
| 1     | Foundation. Shared infrastructure above. No domain tables, no features. |
| 2     | Identity. User, Role, Permission, authentication, authorization guards, workspace membership and invitations, and the minimal workspace row they require. |
| 3     | Workspace lifecycle and settings, and teams. **Done.**                  |
| 4     | Projects and project membership. **Done.**                              |
| 5     | Tasks, subtasks, dependencies, and the list, board, calendar queries. **Done.** |
| 6     | Comments and mentions, attachments, activity and audit logging. **Done.** |
| 7     | Notifications with read state, history, and the deadline scheduler. **Done.** |
| 8     | Dashboards, reports, analytics, and the indexes they need. **Done.**    |
| 9     | Admin panel. **Done.**                                                  |
| 10    | Hardening: rate limits, headers, request logging, scheduled purges, trigram search indexes, Redis-backed limiting, malware scanning, production configuration. **Done.** |
| 11    | Delivery: environments, deployment, backups, end-to-end suite, docs. **Done.** |

Each phase ends with its own tests and documentation, so the production
readiness checklist fills in continuously rather than at the end.

## Still open

Token design was here and is now settled. See *Identity and authentication*
above.

| Item                       | State                                                              |
| -------------------------- | ------------------------------------------------------------------ |
| Storage provider           | **Settled.** S3, through `S3FileStore`, written against the API rather than against Amazon: `app.storage.endpoint` with path-style access points the same code at MinIO, R2 or Spaces, and the rehearsal profile uses MinIO to prove it. Credentials come from the SDK's default chain and there is deliberately no property for a key, so a deployment attaches a role instead. Phase six's `FileStore` port is unchanged and `LocalFileStore` remains the development and test implementation, which is what the port was for. Delivery supplied the bucket itself — `deploy/aws` has the three permissions the application actually uses, the versioning that makes a mistaken purge recoverable, and the CORS rule a download depends on |
| Task dependency semantics  | **Settled.** A single blocking relationship, confined to one project, with no type column. Built in phase five. |
| Notification delivery      | **Settled.** Polling shipped in phase seven: a paged feed, an unread count, and two ways to mark read. Server-sent events remain a later swap and need no change to the response shape. |
| Project progress rule      | **Implemented** in phase five, with `DONE` winning over an unfinished checklist. |
| Attachment byte purge      | **Built** in phase ten. `AttachmentBytePurge` reclaims the object of any attachment soft-deleted for longer than `app.storage.purge.retention`, thirty days by default, in bounded batches. The object goes before the row, never the other way round, so a failure leaves a row that the next run retries rather than bytes nothing can name. **Off by default**: it destroys the only copy of a file, so a deployment opts in. Its retention is therefore also the window in which a file deleted by mistake can be recovered. |
| Audit role separation      | **Built** in delivery, which is where phase six deferred it to. `V13` puts the runtime privilege set on a `NOLOGIN` group role, `task_platform_app`, holding `SELECT` and `INSERT` on `activity_logs` and not `UPDATE`, `DELETE` or `TRUNCATE` — `TRUNCATE` included because it is not covered by `DELETE`, fires no row trigger, and would empty the trail while the `V7` trigger watched. The trigger stays; what the privileges add is that it can no longer simply be dropped by the role the application connects as. The migration creates no login role and holds no password, so a deployment does `CREATE ROLE` plus `GRANT` once and runs Flyway as the schema owner through `SPRING_FLYWAY_USER`. On a single-role database — development, the tests — it is inert and honestly so, and `AuditRoleSeparationIT` asserts the grants bite against a role holding only them. |
| Virus scanning             | **Built** in phase ten as a provider-agnostic port. `MalwareScanner` has two shipped implementations: one that scans nothing, refused at startup under `prod`, and one that POSTs the bytes to a URL and reads the verdict from the status code. No engine is named or depended on. Scanning happens before anything is stored, so an infected upload leaves no object and no row; a scan that cannot be completed refuses the upload with 503 rather than accepting it. **Production fails closed**: the scanner is probed at startup and the application will not start without it. See *Malware scanning* below for what a deployment has to provide. |
| Blocked tasks              | **Surfaced, not enforced.** The requirements state no rule about starting or finishing blocked work. Revisit only with evidence. |
| Task full-text search      | **Indexed** in phase ten. `V11` enables `pg_trgm` and adds GIN trigram indexes on the six lowered expressions the existing searches already used — task title, project name and key, and the three account-directory columns. Not one query changed: they were correct and unindexable, and now they are correct and indexed. Trigram rather than `tsvector` because these searches match substrings, not words, and a word index would not find "authentication" from "auth". A term shorter than three characters still scans, which is inherent to trigram indexing. |
| Kanban reordering          | Deferred. `board_position` exists, sorts, and is settable; gapless drag ordering is its own design. |
| Custom workspace roles     | **Still deferred.** Phase nine built the editor that changes what the three seeded roles grant; creating, renaming and deleting a role are not built. Doing so needs a slug policy, a decision about the members of a deleted role, and the `default_role_id` pointer that would dangle |
| Report caching             | **Still deferred, and the phase that was supposed to unblock it deliberately did not.** Phase ten introduced Redis, which was the stated prerequisite, and then used it for rate-limit counters only: caching is not switched on, there is no `@EnableCaching` and no `CacheManager`. That is not an omission, it is the safest way to keep the standing decision that a resolved permission set must never be cached — with no cache infrastructure in the context there is no annotation for anybody to add and nothing for one to bind to. What did change is that the rate of these requests is now bounded, which was the concrete risk the caps could not cover. Caching a report remains available to a later phase, and whoever builds it has to keep permissions out of it. |
| Workspace timezone validation | **This row was wrong, and was corrected in phase ten.** Validation has existed since phase three: `WorkspaceLifecycleService.requireKnownZone` resolves the submitted value through `ZoneId.of` and answers 400 for anything the JVM does not recognise, and it covers the only write path there is — creation hardcodes `UTC`. The column is still free text with only a length check, which is deliberate and documented on that method: the zone database changes several times a year and freezing a copy of it into a check constraint would mean a migration every time a country moved its clocks. Two residual details, neither worth code: `ZoneId.of` also accepts offsets such as `GMT+5`, so a stored value is a zone the JVM knows rather than strictly an IANA name; and `WorkspaceSettingsFacade.zoneOf` still falls back to UTC with a WARN, which is the right behaviour for a report rather than a gap. |
| Untenanted aggregate cost  | **Partly addressed** in phase ten, and honestly still open. The admin statistics are still counts over whole tables that no index can usefully narrow, and they are still computed per request. What changed is the second half of the old entry: the rate of those requests is now bounded by the global per-address limit, so one caller can no longer issue them as fast as the application will answer. The account-directory search they sit beside is now trigram-indexed. There is still no dataset here large enough to show what the counts cost. |
| Audit retention            | **Still deferred, and now on its own.** The backup policy it used to sit beside shipped in delivery — `database.md` has the retention, the recovery objectives and the restore procedure — and this did not, because it is a different kind of question. `activity_logs` is append-only and nothing prunes it, and phase nine added platform rows to it. It is now the only purge-shaped item left: the expired-token purge and the attachment byte purge both shipped in phase ten, and `common.scheduling.AdvisoryLock` plus `LockKeys` is the pattern a third one should copy. Deferred rather than built because a retention period for an audit trail is a policy question — possibly a legal one — rather than an engineering one. |
