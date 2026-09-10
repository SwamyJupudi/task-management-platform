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

Every action it takes is audited from the phase that adds auditing.

Authorization has **two layers**, and both must pass.

1. **Permission** answers what the caller may do. Checked at the method boundary.
2. **Scope** answers which rows they may do it to. Applied in the query.

An employee holding the status-update permission may still only touch tasks in
projects they belong to. An admin holding the same permission plus the
workspace-wide grant may touch any task in the workspace.

The resolved permission set is read from the database on the requests that need
it. Caching it is deliberately deferred. A membership change, a role change and a
deactivation must take effect at once, and a cache held inside one process is
already wrong the moment a second instance starts. When the read cost justifies
it, the answer is a shared cache in the hardening phase, not a local one now.

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
tasks, so the column exists, is written as zero, and is not maintained by this
module. Phase five implements the derivation. Nothing pretends to calculate it in
the meantime, which is the honest version of a field that cannot yet mean
anything.

### Sorting

A listing sorts only by an allowlist of fields. Passing a client's sort straight
through lets a query parameter probe the shape of the entity and order by columns
with no index behind them, and neither failure is visible from the response.

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

Expired and consumed rows in `user_tokens` and `refresh_tokens` are not removed.
Nothing reads them, and an expiry check is applied on every use, so they are
inert rather than dangerous. The scheduled purge that reclaims the space belongs
to the hardening phase, along with the scheduling support it needs.

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

Address-level and gateway-level rate limiting stay in the hardening phase, where
the build order already places them. No rate-limiting library is introduced for
either.

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
only form of this that matters.

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
  tokens, keys, and personal data are never logged.
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
| 5     | Tasks, subtasks, dependencies, and the list, board, calendar queries.   |
| 6     | Comments and mentions, attachments, activity and audit logging.         |
| 7     | Notifications with read state, history, and the deadline scheduler.     |
| 8     | Dashboards, reports, analytics, and the indexes they need.              |
| 9     | Admin panel.                                                            |
| 10    | Hardening: rate limits, headers, upload security, caching, query tuning, expired token purge. |
| 11    | Delivery: environments, deployment, backups, end-to-end suite, docs.    |

Each phase ends with its own tests and documentation, so the production
readiness checklist fills in continuously rather than at the end.

## Still open

Token design was here and is now settled. See *Identity and authentication*
above.

| Item                       | State                                                              |
| -------------------------- | ------------------------------------------------------------------ |
| Storage provider           | **Unspecified** by the requirements. Kept provider-agnostic behind a port. Chosen before attachments. |
| Task dependency semantics  | **Unspecified.** Modeled as a single blocking relationship.        |
| Notification delivery      | **Unspecified.** Polling first, server-sent events as a later swap. |
| Project progress rule      | **Approved.** Column added in `V5`; derivation lands with tasks in phase five. |
| Custom workspace roles     | Deferred. Schema supports them, none are seeded.                    |
