# Internal Task and Project Management Platform

Production-grade internal platform for managing teams, projects, tasks,
workflows, and reporting. Requirements live in
[`docs/project-requirements.pdf`](docs/project-requirements.pdf), which is the
source of truth for scope.

**Current state: foundation phase.** The shared infrastructure every feature
will sit on is in place. No feature modules exist yet. There is no
authentication, and no domain tables. See [Project status](#project-status).

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

On Windows use `mvnw.cmd` in place of `./mvnw`.

The application starts on port 8080 and applies its migrations on the way up.
Check it is alive:

```bash
curl http://localhost:8080/actuator/health
```

Then open the API console at <http://localhost:8080/swagger-ui.html>.

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
| 2     | Identity: users, roles, authentication    | Not started |
| 3     | Workspaces and teams                      | Not started |
| 4     | Projects                                  | Not started |
| 5     | Tasks, subtasks, dependencies, views      | Not started |
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

### What it deliberately does not deliver

Authentication, authorization, and every feature module. The security filter
chain currently **permits every request**, because there is no identity model to
check against yet. It is a placeholder and must not be exposed on any reachable
network. Phase two replaces it.

Domain tables are not created either. Each module brings its own migration when
it is built, so the schema grows with the code rather than ahead of it.
