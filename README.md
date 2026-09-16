# Internal Task and Project Management Platform

Production-grade internal platform for managing teams, projects, tasks,
workflows, and reporting. Requirements live in
[`docs/project-requirements.pdf`](docs/project-requirements.pdf), which is the
source of truth for scope.

**Current state: deliverable.** The shared infrastructure is in place, and so
are accounts, authentication, role-based authorization scoped to a workspace,
workspace settings and lifecycle, teams, projects, tasks with their subtasks and
dependencies, comments, mentions, attachments, the audit trail, notifications
with their deadline scan, dashboards and reports, the admin panel, the hardening
phase, and now delivery: images, a production stack, a gated deployment
pipeline, a backup and recovery policy, and an end-to-end suite that walks one
working day through the API. Every phase of the build order is built. See
[Project status](#project-status) and [`docs/deployment.md`](docs/deployment.md).

---

## Stack

| Layer      | Choice                                  |
| ---------- | --------------------------------------- |
| Language   | Java 17                                 |
| Framework  | Spring Boot 4.1                         |
| Database   | PostgreSQL 16                           |
| Migrations | Flyway, the only source of schema truth |
| API docs   | springdoc OpenAPI, generated from code  |
| Tests      | JUnit 5, Testcontainers                 |
| Build      | Maven, via the bundled wrapper          |

Architecture is a modular monolith. See
[`docs/architecture.md`](docs/architecture.md).

---

## Running it from scratch

You need JDK 17 or newer, Docker, and Git. Nothing else has to be installed:
Maven comes with the repository.

```bash
git clone <repository-url>
cd task-management-platform

cp .env.example .env          # local defaults, safe to use as-is
docker compose up -d          # starts PostgreSQL on port 5432

./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The example file creates a platform administrator on first start, from
`SUPER_ADMIN_EMAIL` and `SUPER_ADMIN_PASSWORD`. Change the password before using
this anywhere but your own machine. Set both variables or neither: half of the
pair fails startup rather than leaving the platform with nobody able to
administer it.

On Windows use `mvnw.cmd` in place of `./mvnw`.

The application starts on port 8080 and applies its migrations on the way up.
Check it is alive:

```bash
curl http://localhost:8080/actuator/health
```

Then open the API console at <http://localhost:8080/swagger-ui.html>.

There is no mail transport yet. In the `dev` profile the verification and reset
links are written to the application log instead, so you can complete either flow
locally by copying the link out of the console. That logging is off everywhere
else, because tokens do not belong in logs.

Uploaded files go to `./var/attachments` in development, which is git-ignored and
safe to delete. No object-storage provider has been chosen, so the application
refuses to start under the `prod` profile rather than writing customer files to the
application server, which the requirements forbid. Set `STORAGE_LOCAL_DIR` to put
them somewhere else.

To stop the database and keep its data, run `docker compose stop`. To remove the
data as well, run `docker compose down -v`.

---

## Configuration

Configuration is read from the environment. No credential is ever committed.
[`.env.example`](.env.example) lists every variable, including placeholders for
the later phases so the full contract is visible in one place.

A profile must be selected explicitly. There is no default, so a missing profile
fails at startup instead of quietly running with development settings.

| Profile | Purpose         | Behaviour                                                          |
| ------- | --------------- | ------------------------------------------------------------------ |
| `dev`   | Local machine   | Defaults match `docker-compose.yml`, verbose logging, API console on |
| `test`  | Automated tests | Datasource supplied by the test container                           |
| `prod`  | Deployed        | Every value required from the environment, JSON logs, console off   |

`JWT_SECRET` is required outside `dev` and has no fallback. It must be at least
32 bytes; a shorter one fails at startup rather than signing tokens with a key
that is too weak for the algorithm.

Set the profile with `SPRING_PROFILES_ACTIVE`, or with
`-Dspring-boot.run.profiles` when using the Maven plugin.

---

## Deploying it

One host running [`deploy/docker-compose.prod.yml`](deploy/docker-compose.prod.yml),
with a managed PostgreSQL instance and an object store beside it. Images come
from the registry, built by CI and tagged with the commit that produced them;
the host builds nothing.

[`docs/deployment.md`](docs/deployment.md) is the whole of it: what has to exist
before the first deployment, the two database roles and why there are two, the
certificate, the secrets, rolling back, and how to rehearse the entire stack on
a laptop. When something is broken rather than being deployed,
[`docs/troubleshooting.md`](docs/troubleshooting.md) is the other half.

---

## Tests

Tests are split by what they need to run.

```bash
./mvnw test      # unit tests only, no Docker required
./mvnw verify    # unit tests, then integration tests against real PostgreSQL
```

Integration tests are named `*IT` and start a PostgreSQL container through
Testcontainers, so migrations and constraints are exercised against the real
engine rather than a substitute. Without Docker they skip rather than fail,
which keeps the build usable on a machine that has no Docker. Continuous
integration checks that Docker is present and fails if these tests were skipped,
so the skip can never hide a break.

`EndToEndJourneyIT` is the one to read first if you want to know what the
product does. It signs in, creates a workspace, invites two people who have no
account yet, redeems both invitations, creates a project, adds a member, raises
a task, assigns it, completes it, and then checks the audit trail, the
notifications and the reports — all over HTTP, using almost no fixtures,
because the fixtures skip the steps a person cannot skip.

See [`docs/testing.md`](docs/testing.md).

---

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push to a
protected or working branch and on every pull request: install, lint, test,
build, and then the three images. Images are built on every run, so a broken
Dockerfile fails the pull request that broke it, and pushed to GHCR only from
`master`, so a fork's pull request can never publish one. A fourth job renders
and validates the production compose file and both nginx configurations, which
catches at review time the mistakes that would otherwise be found on the
production host.

Deployment is a separate workflow,
[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml). It needs a
credential that can reach the production host and an approval gate, and neither
belongs in the workflow that every pull request runs. A merge does not deploy on
its own: somebody releases it. See
[`docs/deployment.md`](docs/deployment.md).

Lint is Spotless. Run `./mvnw spotless:apply` to fix formatting locally before
pushing.

---

## Branching

`master` is production: CI runs on it and on every pull request into it, images
are pushed to the registry from it alone, and it is what the deployment
workflow watches. Work happens on `feat/*`, `feature/*`, `bugfix/*` or
`hotfix/*` and reaches `master` through a reviewed pull request. Nothing is
pushed straight to `master`.

Merging does not deploy. It makes a release available to be deployed, and a
reviewer releases it.

---

## Project status

The build order is set out in [`docs/architecture.md`](docs/architecture.md).

| Phase | Scope                                     | State       |
| ----- | ----------------------------------------- | ----------- |
| 1     | Foundation: shared infrastructure         | Done        |
| 2     | Identity: users, roles, authentication    | Done        |
| 3     | Workspaces and teams                      | Done        |
| 4     | Projects                                  | Done        |
| 5     | Tasks, subtasks, dependencies, views      | Done        |
| 6     | Comments, mentions, attachments, activity | Done        |
| 7     | Notifications                             | Done        |
| 8     | Dashboards and reports                    | Done        |
| 9     | Admin panel                               | Done        |
| 10    | Hardening                                 | Done        |
| 11    | Delivery: environments, deployment, docs  | Done        |

The React frontend starts alongside phase two.

### What the foundation phase delivers

- PostgreSQL for local development through Docker Compose.
- Environment-driven configuration across three profiles, no hardcoded secrets.
- Flyway wired and proven by test, holding sole ownership of the schema.
- One error body shape for the whole API, with internal detail kept in the logs.
- A correlation id on every request, every log line, and every response.
- Structured JSON logging in production.
- Health, liveness, and readiness endpoints.
- An OpenAPI document generated from the code.
- An integration test harness running against real PostgreSQL.
- A CI pipeline covering lint, test, and build.

### What the identity phase delivers

- Registration, email verification, sign-in and sign-out.
- Forgotten-password recovery, and changing a password from inside a session.
- Short-lived access tokens, with rotating refresh tokens that detect reuse.
- Account activation and deactivation, taking effect on the next request.
- Roles and permissions scoped to a workspace, with a global permission catalog.
- Workspace membership and invitations, including for people with no account yet.
- A session list, so a person can see and end their own sessions.

The filter chain now refuses anything that is not on an explicit public list, and
a test walks every mapped endpoint to prove it.

### What the workspace and teams phase delivers

- Workspace settings: display name, description, time zone, and the role an
  invitation falls back to when it names none.
- Archiving a workspace, which is reversible and freezes every change inside it
  while leaving all of it readable.
- Removing a workspace, which hides it and releases its slug for reuse. Platform
  administration only, and not something a workspace administrator can do to
  their own workspace.
- Teams: create, edit, archive, restore and remove, scoped to one workspace.
- Team membership, and a single team lead who is always one of the team's own
  members.
- Two-layer authorization over teams. A team lead may change the teams they lead;
  an administrator holds the workspace-wide grant and reaches all of them.

Somebody removed from a workspace, or whose account is deleted, is taken out of
its teams and stood down as their lead in the same transaction. The database
refuses the removal otherwise, so this is the step that makes it possible rather
than tidying that could be deferred.

### What the projects phase delivers

- Projects with the fields the requirements name, scoped to one workspace, with a
  short key unique within it.
- The five-status lifecycle, with the legal transitions enforced and a rejected
  one refused rather than silently applied.
- Project membership, and a single owner who is always one of the members.
- Tags, drawn from one workspace catalog shared with tasks when they arrive.
- Filtering by status, priority, team, owner, tag and free text, with paging and
  sorting restricted to an allowlist.
- Visibility that follows the requirements: an employee sees the projects assigned
  to them, an administrator sees all of them.

Progress was stored and returned as zero. Phase five derives it from tasks.

### What the tasks phase delivers

- Tasks with every field the requirements name, inside a project, known by a
  stable `PROJECTKEY-12` handle that is safe to hand out even when several people
  create work at the same moment.
- The four-status board flow, with the legal moves enforced and a rejected one
  refused rather than silently applied.
- Subtasks: a checklist under a task, tracking completion, assignee, status and
  due date.
- Dependencies: one task waiting on another in the same project, refusing itself,
  duplicates, and anything that would make two tasks wait on each other.
- Labels, drawn from the same workspace catalog projects tag themselves from.
- Search and filtering by project, team, assignee, reporter, status, priority, due
  date, created date, label and free text, with paging and sorting restricted to
  an allowlist.
- Visibility that follows the requirements: an employee sees the work of projects
  assigned to them, an administrator sees all of it.
- Project progress, derived from task and subtask completion rather than typed by
  anybody.

Assignment, status changes and deletion each need their own permission, so a team
lead assigns and tracks work without being able to delete it, and an employee runs
their own tasks without being able to hand them to somebody else.

### What the collaboration phase delivers

- Comments on a task, with the author and the moment recorded, and an edit stamp
  separate from the row's own timestamp so a reader knows when the words changed.
- Mentions, written into the text as `@[user:<uuid>]` and read back out by the
  server, so the notifications a comment produces can never disagree with what it
  says. Naming somebody who cannot see the task is refused rather than silently
  dropped.
- Attachments on a task or on a comment, with the type detected from the file's own
  bytes rather than from what the upload claimed, an allowlist that excludes SVG
  because browsers execute it, and downloads that always leave as attachments.
- An audit trail of what people did, written from events the earlier phases were
  already publishing, and append only: a database trigger refuses to change or
  remove a row that has been written.

Anybody who can see a task may comment on it and attach to it. That is deliberate
and is not the rule that governs editing a task: a discussion only the assignee may
join is not a discussion. Removing somebody's comment is possible for an
administrator, or for the owner or team lead of the project it sits in. **Rewriting
it is possible for nobody but its author,** whatever else they hold.

Nobody has to be stood down when they leave a workspace for any of this. A comment,
a file and an audit row all key to the person rather than to their membership, so
they outlive somebody changing team, which is what anybody reading an old thread
would expect.

There is no storage provider yet. Files are written to local disk in development and
in tests, behind a port, and the application **refuses to start in production** with
that arrangement rather than quietly putting customer files on a disk the next
deployment discards. Choosing the provider is the one thing between this and a
deployable build.

### What the notifications phase delivers

- In-app notifications for the six triggers the requirements name, five of them
  built from events the earlier phases were already publishing. Not one line
  changed in the modules that publish them.
- Read and unread state, a badge count, a paged history, and two ways to clear it.
- A daily scan that tells people about an approaching deadline. It is idempotent,
  so a re-run costs nothing, and a due date that moves notifies again.
- One instance runs that scan at a time, decided by a PostgreSQL advisory lock
  taken and released on a single held connection.

**Nobody reads anybody else's notifications**, including the platform
administrator. A notification has one audience, so this module adds no permission
code at all; membership of the workspace and being the named recipient is the
whole rule. Who was told what is an audit question, and the audit trail answers it.

A notification does not outlive the access it implies. Leaving a workspace or a
project removes the rows, and a feed stops naming work its reader can no longer
open even when no membership changed.

### What the dashboards and reports phase delivers

- Three dashboards: your own work, one team's, and the whole workspace. Each is
  assembled in a single read-only transaction, so the panels of one response
  agree with each other.
- Five reports: project completion and progress, task distribution, overdue work,
  workload per person, and productivity trends. Each filters, and the two
  listings page and sort within an allowlist.
- Eight indexes, chosen for the queries this phase actually writes. No new table,
  no new column, and no new permission.

**No report can show you a number you could not already have listed.** Every
figure is computed over the same project read scope a task listing is narrowed
by, and that scope is applied inside the aggregate query rather than to its
result, so no filter can widen it. `project:read_any` widens a report exactly as
it widens a listing, which is why the phase adds no permission of its own.

Every aggregate is computed in SQL by the module that owns the table. No endpoint
loads a collection of rows in order to count it, and no panel resolves a name one
row at a time. Each module publishes a small read-only analytics facade instead,
so the rule that a module never reads another's tables survives a phase that
exists to read across all of them.

"Today" is the workspace's own today. Overdue work, upcoming deadlines and the
buckets of a chart are all computed in `workspaces.timezone`, not in UTC.

**Finished work is never overdue, however late it was.** That definition and five
others are written down in [`docs/database.md`](docs/database.md), because a
dashboard count and the list it links to disagreeing by one row is the worst kind
of defect a reporting feature can ship.

### What the admin panel phase delivers

- **System statistics across every workspace**: accounts by status and how many
  are locked out, workspaces, teams, memberships, projects and tasks by status,
  overdue work, attachment count and bytes, and a trailing window of new
  accounts, sign-ins and audit entries.
- **A cross-workspace project overview and account directory**, each paged,
  filtered and sorted within an allowlist.
- **A role editor.** An administrator can change what a role grants, and the
  change takes effect on every holder's next request.
- **Four account verbs**: edit somebody's profile, clear a sign-in lockout, start
  a password recovery, and send another verification message.
- **Granting and revoking the platform administrator role over HTTP.** Until now
  that was possible only by redeploying.
- **A platform audit trail.** Every administrative action is recorded, including
  the ones that happen outside any workspace, which could not be recorded at all
  before this phase.

**Administering one workspace is not administering the installation.** The admin
panel is the only part of the platform that reads across workspaces, and every
one of its endpoints is gated on a platform role alone. Workspace membership is
never consulted, so an administrator holding every permission in their own
workspace reaches none of it. One test walks every such route with exactly those
grants and asserts it is refused.

**An administrator never learns anybody's password.** There is no endpoint that
sets one. Starting a recovery issues the ordinary single-use token and mails it
to the account's own address, and redeeming it ends every session.

**You cannot remove your own access, and the last platform administrator cannot
be removed at all.** Deactivating or deleting your own account, and revoking your
own platform role, are refused; so is demoting, deactivating or deleting the only
account that holds it. An installation with no platform administrator cannot be
administered until somebody edits the database by hand.

**A release note.** Two permissions that have been seeded since phase two stop
being dead constants here, which changes behaviour without any grant changing.
`role:manage` was already granted to the workspace `Admin` role, so a workspace
administrator can now edit roles. `user:update` is granted to no role, which is
why account administration needs a platform role.

### What the admin panel deliberately does not deliver

Custom workspace roles. The editor changes what the three seeded roles grant;
creating, renaming and deleting a role are still deferred, because each needs a
slug policy, a decision about the members of a removed role, and the default-role
pointer that would dangle.

No writes to the permission catalog, ever. Its rows are written by migrations,
and an endpoint that created one would create a code nothing in the application
checks.

No impersonation, no "sign in as this user", no bulk operations, and no export.
No changing somebody's email address, which is the account's identity and would
need re-verification and a decision about live sessions. No runtime configuration
through the API: environment variables stay the contract.

Nothing here is cached either. These are the platform's only queries with no
workspace predicate, so several are counts over whole tables; the window and page
caps bound one request and nothing bounds the rate of them. That, and the fact
that nothing prunes the audit trail, are recorded under *Still open* in
[`docs/architecture.md`](docs/architecture.md) rather than left to be discovered.

### What the reporting phase deliberately does not deliver

Email notifications, which the requirements call advanced and optional. Nothing
is pushed either: delivery is polling, and server-sent events are a later swap
that needs no change to the response shape. There are no per-person preferences,
no digests, and no retention policy for old notifications.

Nothing in the reporting phase is cached or precomputed. There is no
materialized view, no rollup table and no scheduled aggregation, so every figure
costs its query on every request. Caching belongs with the shared cache in the
hardening phase, and shipping a second copy of the truth now would only mean one
that can go stale.

There is no export. No CSV, no PDF, no scheduled delivery and no email digest:
the requirements ask for none, and an export raises a size policy and an audit
question about who took the company's numbers. There is no report builder, no
saved report and no user-defined metric.

Dashboards are request-response rather than live, like the notification feed
beside them. Platform-wide analytics across workspaces belong to the admin panel
in phase nine; every endpoint here is scoped to one workspace.

Tasks cannot be moved between projects, since the number they are known by belongs
to one. Kanban drag-ordering and full-text search are both deferred: the board
position column and a folded title search exist, and the reordering design and the
search index belong with the hardening work.

There is no mail transport, no rate limiting beyond per-account lockout, and no
cleanup of expired token rows. Each is scheduled work rather than an oversight:
see the identity section of [`docs/architecture.md`](docs/architecture.md).

Uploaded files are not scanned for malware, and removing one leaves its bytes in
storage for a purge that does not exist yet. Both sit with the rest of the upload
security work in the hardening phase, and both are recorded under *Still open* in
[`docs/architecture.md`](docs/architecture.md) rather than left to be discovered.

### What the hardening phase delivers

- Rate limiting in two places: address-keyed limits in the security chain,
  before authentication, and account-keyed limits in the services where the
  address arrives in the request body. Redis backs the counters and nothing
  else — caching is deliberately not switched on, so a resolved permission set
  has no machinery to be cached in.
- Security headers, request completion logging, and container-aware sizing.
- Trigram search indexes, so the substring searches that already existed stop
  being sequential scans. Not one query changed.
- Malware scanning as a provider-agnostic port, scanned before anything is
  stored. Production fails closed: the scanner is probed at startup and the
  application will not start without it.
- Two scheduled purges — expired tokens, and the bytes behind long-deleted
  attachments — both off by default, so a deployment opts into deletion rather
  than discovering it.

### What the delivery phase delivers

- Three images, built by CI and tagged with the commit that produced them, and a
  production stack in [`deploy/`](deploy) that pulls them. The host builds
  nothing and holds no source.
- One origin behind an edge proxy that terminates TLS, because the refresh
  cookie is `SameSite=Strict` and an interface on a second hostname would sign
  people in and then drop them on the next reload.
- A gated deployment: CI on `master` proposes a release, a reviewer releases it,
  and the job ends with a smoke test against the public hostname that uses no
  credential at all. Rolling back is deploying the previous commit's images.
- The privilege half of the append-only audit trail. `V13` puts the runtime
  privileges on a group role that holds `SELECT` and `INSERT` on `activity_logs`
  and not `UPDATE`, `DELETE` or `TRUNCATE`, so the trigger that refuses edits
  can no longer simply be dropped by the role the application connects as.
- A backup and recovery policy with the numbers written down, including the part
  people forget: the bucket has to be restored to the same point as the
  database, or the rows and the files disagree. See
  [`docs/database.md`](docs/database.md#backup-and-recovery).
- The end-to-end suite, and two documents that did not exist:
  [`docs/deployment.md`](docs/deployment.md) and
  [`docs/troubleshooting.md`](docs/troubleshooting.md).

### What the delivery phase deliberately does not deliver

No infrastructure as code. One host, one managed database and one bucket are
created by the commands in [`docs/deployment.md`](docs/deployment.md) and
[`deploy/aws/README.md`](deploy/aws/README.md); inventing a Terraform layout
nobody reviewed would put an unapproved decision in the repository.

No staging environment. The compose file's `rehearsal` profile starts stand-ins
for the database and the object store so the whole stack can be brought up on a
laptop under the `prod` profile, which is the only thing that makes it a
rehearsal. A second host with its own database and bucket would be the honest
version, and that is a cost decision rather than a technical one.

No horizontal scaling that anyone has exercised. The compose file runs one of
everything. Two backends behind the same proxy should work — the application
holds no session state, and the scheduled jobs take an advisory lock precisely so
that two instances do not both run them — but it has not been tried, and saying
so is worth more than a replica count that has not.

No audit retention. `activity_logs` is append-only and nothing prunes it. How
long an audit trail is kept is a policy question, possibly a legal one, and it is
recorded under *Still open* in [`docs/architecture.md`](docs/architecture.md)
rather than answered by whoever happened to be writing the purge.
