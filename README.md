# Internal Task and Project Management Platform

Production-grade internal platform for managing teams, projects, tasks,
workflows, and reporting. Requirements live in
[`docs/project-requirements.pdf`](docs/project-requirements.pdf), which is the
source of truth for scope.

**Current state: projects phase.** The shared infrastructure is in place, and so
are accounts, authentication, role-based authorization scoped to a workspace,
workspace settings and lifecycle, teams, and projects with their membership and
lifecycle. Tasks do not exist yet. See [Project status](#project-status).

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

See [`docs/testing.md`](docs/testing.md).

---

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push to a
protected or working branch and on every pull request: install, lint, test,
build. The deploy stage is not wired yet because no cloud provider has been
chosen; adding one would put an unreviewed decision into the pipeline.

Lint is Spotless. Run `./mvnw spotless:apply` to fix formatting locally before
pushing.

---

## Branching

`main` is production. `develop` is the integration branch. Work happens on
`feature/*`, `bugfix/*`, or `hotfix/*` and reaches `develop` through a reviewed
pull request. Nothing is pushed straight to `main`.

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
| 6     | Comments, attachments, activity and audit | Not started |
| 7     | Notifications                             | Not started |
| 8     | Dashboards and reports                    | Not started |
| 9     | Admin panel                               | Not started |
| 10    | Hardening                                 | Not started |
| 11    | Delivery: environments, deployment, docs  | Not started |

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

### What it deliberately does not deliver

Comments, attachments, notifications and reporting.

Team dashboards, workload and task statistics are listed under team management in
the requirements and are not here. They arrive with the other analytics in phase
eight.

Tasks cannot be moved between projects, since the number they are known by belongs
to one. Kanban drag-ordering and full-text search are both deferred: the board
position column and a folded title search exist, and the reordering design and the
search index belong with the hardening work.

There is no mail transport, no rate limiting beyond per-account lockout, and no
cleanup of expired token rows. Each is scheduled work rather than an oversight:
see the identity section of [`docs/architecture.md`](docs/architecture.md).
