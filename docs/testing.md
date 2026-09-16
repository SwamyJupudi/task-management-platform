# Testing guide

## Layers

| Layer       | Naming  | Runner   | Needs Docker | What it covers                          |
| ----------- | ------- | -------- | ------------ | --------------------------------------- |
| Unit        | `*Test` | Surefire | No           | Services, business rules, utilities     |
| Integration | `*IT`   | Failsafe | Yes          | Wiring, migrations, security, endpoints  |

`./mvnw test` runs the first. `./mvnw verify` runs both.

The split is deliberate. Unit tests must stay fast enough to run on every save,
so nothing in them may touch a database, a container, or the network.

## Why real PostgreSQL

Integration tests run against a PostgreSQL container rather than an in-memory
database. Migrations, constraints, extensions, and types behave differently on a
substitute, and a test that passes on H2 proves nothing about production. The
container is static and shared across the suite, so it starts once.

`AbstractIntegrationTest` provides the container and the Spring context. Extend
it rather than repeating the annotations.

## Behaviour without Docker

Integration tests skip themselves when Docker is absent, so a developer machine
without it stays usable. That skip is a convenience, never a pass: CI checks
Docker is available and fails the build if any integration test was skipped.

## Coverage expectations by phase

The requirements name four kinds of test. Foundation covers the first two in
their infrastructure form. The rest arrive with the code they describe.

| Kind        | Required by                                                      | Arrives in |
| ----------- | ---------------------------------------------------------------- | ---------- |
| Unit        | Services, business logic, utilities, validation                  | Every phase |
| Integration | Auth, project creation, task creation, assignment, permissions   | Phases 2-9 |
| Concurrency | Refresh rotation, task numbering                                 | Where a race is possible |
| API         | Every major endpoint                                             | Phases 2-9 |
| End to end  | Login, create project, add member, create task, assign, complete | Phase 11   |

No coverage percentage is specified in the requirements document.

## The identity phase

Unit tests cover the pieces that can be reasoned about alone: the password
policy, token generation and hashing, access token issue and validation, refresh
rotation and reuse detection, the credential-check outcomes and their ordering,
lockout, the cookie codec, and permission resolution.

Integration tests cover the rest against real PostgreSQL. `IdentitySchemaIT` is
worth singling out: it writes SQL directly, because the constraints it exercises
exist so that a mistake in the service layer cannot corrupt the data, and testing
them through the service layer would prove the wrong thing.

Two tests guard against the mistakes nobody makes deliberately.

**`ProtectedRouteMatrixIT`** enumerates every endpoint the application maps and
asserts that anything outside an explicit public list refuses an anonymous
caller. Individual tests check the endpoints somebody remembered to write a test
for; this one checks the ones they did not. A controller added in a later phase
joins it automatically, and the only way to leave a route open is to add it to
that list in a review.

**`PermissionCatalogIT`** holds the permission codes in the source together with
the rows a migration seeded. A constant with no row is a permission nobody can
hold; a row with no constant is a grant nothing checks. Both fail silently
otherwise. It also asserts the platform administrator is mapped to every
permission, which is the standing obligation that comes with having no bypass in
the authorization path.

## The workspace and teams phase

Integration tests cover the settings and lifecycle of a workspace, the whole team
lifecycle, and the cleanup that runs when somebody leaves.

`TeamSchemaIT` is the counterpart to `IdentitySchemaIT` and is written the same
way, in SQL. The rules it exercises exist so that a mistake in the service layer
cannot corrupt the data, so driving them through the service layer would prove
the wrong thing. It asserts that a lead or a team member from outside the
workspace is refused, that a team member cannot be recorded against the wrong
workspace, that team names fold on case and space, and that removing a workspace
member who still leads a team is refused outright.

`TeamAuthorizationIT` is the one to keep honest. It exercises the two-layer rule
rather than assuming it: a team lead is admitted for the team they lead and
refused for one they do not, holding exactly the same permission in both cases.
That difference is the whole purpose of `team:manage_any`, and no other test
would notice if it disappeared.

`WorkspaceRoleGrantsIT` holds `SystemRole` together with the rows a workspace
actually receives. The grants exist twice, once in code for new workspaces and
once in a migration's backfill for existing ones, and two workspaces created
either side of a migration have to be able to do the same things.

`MemberRemovalCascadeIT` covers the other side of the schema rules above: the
event-driven cleanup that lets a removal succeed at all.

## The projects phase

`ProjectVisibilityIT` is the one to keep honest, and the reason is worth stating.
Read scope decides which rows a listing returns rather than whether a call is
allowed, so a mistake in it leaks the shape of a workspace instead of failing
loudly. It covers each of the three ways a project comes into reach separately,
the case where none of them holds, and the case where a filter is used to try to
widen the answer.

`ProjectAuthorizationIT` covers the write half: a team lead admitted for a project
they own or whose team they lead, and refused for one that is neither, holding
exactly the same permission in all three cases.

`ProjectSchemaIT` is written in SQL, like its identity and teams counterparts, and
proves the composite keys, the folded partial uniques, the date order and the
progress bound. `ProjectCascadeIT` proves the event-driven cleanup that lets a
workspace member be removed at all, plus the team detachment that nothing refuses
and so could silently be forgotten.

`ProjectStatusTest` walks the whole transition matrix rather than a happy path,
including that every status is reachable and that none is a dead end.

## The tasks phase

Five of these earn their place for reasons worth stating, because each covers a
failure that would otherwise be invisible.

`TaskVisibilityIT` is the one to keep honest, for the same reason
`ProjectVisibilityIT` is: read scope decides which rows a listing returns rather
than whether a call is allowed, so a mistake in it leaks the shape of a workspace
instead of failing loudly. It covers each way a project comes into reach, the case
where none holds, the case where a filter is used to try to widen the answer, and
the one that only exists at this level: that My Tasks is a subset of what is
visible rather than a way past it.

`TaskAuthorizationIT` covers the write half. Every case holds exactly the same
permission and differs only in the caller's relationship to the task, so nothing in
it passes because of a permission difference. That is the whole purpose of
`task:manage_any`, and no other test would notice if it disappeared.

`TaskNumberingConcurrencyIT` is the one a single-threaded suite cannot replace.
Twelve threads create tasks in one project at the same instant and the numbers must
come out as exactly one to twelve. `SELECT max(task_number) + 1` passes every other
test in this repository and fails this one immediately.

`TaskSchemaIT` is written in SQL, like its identity, teams and projects
counterparts, because the constraints exist so that a mistake in the service layer
cannot corrupt the data. It proves the assignee and reporter keys, the number
uniqueness surviving a soft delete, the completion timestamp agreeing with the
status on both tables, and that a dependency cannot leave its project.

`ProjectProgressIT` tests a formula that lives in SQL. There is deliberately no
unit test beside it: a unit test would have to reimplement the rule, and a test of
a reimplementation proves only that two versions agree with each other.

`TaskDependencyIT` covers the cycle rule at three depths, because a single-step
check passes the direct case and fails the transitive one, and a diamond has to
stay allowed.

`TaskCascadeIT` covers the other side of the schema rules: the listeners that let
somebody be removed at all, and the ordering between them. That ordering is the
fragile part, and getting it wrong produces a failure that appears only when the
person being removed happens to have work assigned.

## The dashboards and reports phase

A reporting feature fails differently from everything before it. A mistake does
not throw, does not return the wrong status, and does not show a row that should
have been hidden. It changes a number. The response is 200, the shape is right,
and the figure is plausible. Three tests in this phase exist because of that, and
they are the ones to keep.

`ReportVisibilityIT` is the one to keep honest, playing the role
`ProjectVisibilityIT` and `TaskVisibilityIT` play one level down. Read scope
decides which rows an aggregate counts, so a mistake in it silently discloses the
shape of a workspace: how much work there is, how late it is, and how many people
are carrying it. Every case holds identical permissions and differs only in the
caller's relationship to the work. Three cases exist only at this level: a
`projectId`, a `teamId` and an `assigneeUserId` filter must each narrow a figure
and must never widen one.

`ReportDefinitionsTest` guards against drift. "Overdue" is expressed twice in the
platform, once as the `overdue` filter on the task listing and once as the
predicate behind every figure in this phase. If the two ever disagree, a count
and the list it links to differ by a row and nobody can say which is right.
Nothing else in the suite would notice. The unit test pins the rule; the last
case in `ReportAccuracyIT` asks both paths the same question against real data
and asserts they agree, which is the half that catches a drift in the SQL rather
than in the reading of it.

`ReportScaleIT` catches the defect the facade design exists to prevent. An N+1 in
a report is not a correctness bug: it passes every test that checks numbers, and
it falls over the first time a real workspace opens the page. It asserts the
shape of the work rather than the time it takes, by doubling the data and
asserting the query count does not move. That is stronger than a fixed number and
does not need editing every time a panel is added. A timing assertion would fail
on a slow machine and be deleted within a month.

`ReportSchemaIT` is written in SQL like its identity, teams, projects and tasks
counterparts, because an index is a property of the schema rather than of the
service layer. It asserts that each index `V9` creates exists **with its partial
predicate**, which is the half that is easy to lose: an index missing its
`WHERE deleted_at IS NULL` still answers every query correctly and quietly holds
a row for every task ever deleted. It deliberately does **not** assert query
plans. An `EXPLAIN` assertion reads stronger and is weaker: a planner is right to
choose a sequential scan over fifty rows, so such a test fails on data volume
rather than on a defect.

`ReportTrendsIT` carries the phase's one exception to the rule that fixtures
build data through the services. The task service sets `completed_at` from
`Instant.now()` rather than from an injected clock, so no test can otherwise
produce a task finished last Tuesday, and a trend that cannot be tested over time
cannot be tested at all. That class writes the column directly in SQL. The
alternative was to take a clock into a phase-five service for the sake of a test
here. The exception is confined to that class and to that one column, and it is
what lets the timezone case be asserted: work finished at 23:00 local falls in the
local day, not the UTC one.

The test profile lowers three bounds so the tests that prove those bounds exist
can reach them: `app.reports.max-period-days=40` rather than a year,
`app.reports.max-page-size=25` rather than a hundred, and an explicit
`app.reports.upcoming-lead-days=7`. The page cap stays **above** the endpoints'
own default page size of twenty, or every request that named no size at all would
be refused by the guard meant for the ones that name a large one.

Four existing tests pick this phase up with no edit: `ProtectedRouteMatrixIT`
walks the new routes, `PermissionCatalogIT` stays green because no permission was
added, `OpenApiContractIT` picks up the two new controllers, and
`ApplicationContextIT` picks up the new beans.

## The admin panel phase

`AdminIsolationIT` is the one to keep honest, and its failure would be the widest
in the suite. Every other authorization test here guards one workspace:
`ProjectVisibilityIT` and `TaskVisibilityIT` stop somebody seeing work inside a
workspace they belong to, `ReportVisibilityIT` stops a number computed over it.
This one stops an administrator of one workspace reaching **every workspace in
the installation**, and a mistake would not throw or return the wrong status. It
would return a plausible number computed over the whole company.

It walks **every** platform route rather than a sample, because a route added
later and forgotten here is a route nothing protects, and `ProtectedRouteMatrixIT`
only proves a route refuses an anonymous caller, not that it refuses a signed-in
one who administers somewhere else. Every case holds the widest possible
workspace grants and differs from the passing case only in whether the caller has
a platform role, so nothing in it passes because of a workspace permission
difference.

`PlatformAuditIT` proves the promise this phase discharges. `architecture.md` has
said since phase two that every action the platform administrator takes is
audited; phase six could not meet that, because `activity_logs.workspace_id` was
`NOT NULL` and no platform action happens inside a workspace. Nothing noticed,
because no platform action had an endpoint. Each of the ten new actions is
asserted to produce exactly one row with a null workspace, the right entity type,
the right actor and the request id of the call that caused it — and the last test
in the class asserts the append-only trigger still refuses an `UPDATE`, which is
the half a nullability change could plausibly have broken.

Its negative assertion is worth reading. Unlocking an account that was not locked
must write nothing, and proving an absence usually means sleeping and hoping. It
instead performs a *later* auditable action on the same account and waits for
that row: the audit executor is a single FIFO thread by design, so once the later
row exists any earlier one is already there. That makes the assertion
deterministic rather than timing-dependent.

`AdminSchemaIT` is written in SQL like its identity, teams, projects, tasks and
reports counterparts, because a nullable column, a check constraint and a trigger
are properties of the schema rather than of the service layer. It also asserts
that neither new permission reaches any workspace role, which is the third answer
the backfill obligation has had and the one that is easiest to get wrong by
copying the previous migration.

`AdminStatisticsIT` builds **two** workspaces, which no other test base in the
platform does. Every figure the admin panel produces crosses workspaces, so a
fixture confined to one would let a query that accidentally carried a tenant
predicate pass silently: it would simply read low, and a low number is not a
failure. Its assertions are differences across one operation rather than
absolutes, because the container is shared and never truncated, so no test can
know what the installation held before it started.

Two rules could not be integration-tested and are pinned as unit tests instead,
which is worth recording so nobody moves them back. The **last-administrator**
refusal in `UserAccountServiceTest` turns on how many accounts hold the platform
role, and the suite shares one container and one `SUPER_ADMIN` role, so other
tests' administrators are always present and the count is never one. The **role
diff and no-op** rules in `RolePermissionEditTest` are arithmetic over two sets.
That class builds `Permission` rows by reflection: the entity has no public
constructor because its rows are written by migrations, and adding a factory so a
test could call one would put a mutator on a production entity for no production
reason.

Three existing tests pick this phase up: `ProtectedRouteMatrixIT` walks the new
routes and gained a named check for them, `PermissionCatalogIT` enforces both
halves of the two new permissions, and `WorkspaceRoleGrantsIT` is expected to pass
**unchanged** — which is itself the assertion, since `SystemRole` did not move.

The test profile lowers two bounds so the tests that prove those bounds exist can
reach them: `app.admin.max-stats-window-days=40` rather than a year, and
`app.admin.max-page-size=60` rather than a hundred.

**Sixty, not twenty-five**, and the reason is the trap the reports block above
already records, walked into a second time. The cap has to stay above the
*largest* endpoint default, and the admin routes do not share one: the two
listings default to twenty but the audit trail defaults to fifty, matching the
workspace history it mirrors. With the cap at twenty-five, a request naming no
page size at all was refused by the guard meant for requests naming a large one.
`AdminPagingIT.aRequestNamingNoSizeAtAllIsNeverRefused` now asserts that
invariant directly, on all three routes, so the next person to lower the cap is
told immediately rather than by two unrelated-looking failures.

## The delivery phase

Two tests and two checks that are not JUnit at all, because what this phase
builds is only partly code.

`EndToEndJourneyIT` is the suite the coverage table above has promised since it
was written: sign in, create a project, add a member, create a task, assign it,
complete it. It then keeps going far enough to prove the consequences — that the
audit trail recorded the day, that the right people were told, that the reports
moved, and that signing out ends the session.

It is the only class in the suite that **uses almost no fixtures**, and that is
the entire point of it. Every other test builds its world through
`IdentityFixtures` or `TaskFixtures` and exercises one endpoint, which is the
right shape for testing a rule and the wrong one for asking whether the product
works: the fixtures are a second way of creating the same state, and they skip
the steps a person cannot skip. Nothing else here would notice if the invitation
mail stopped carrying a token, if a newly invited account could not sign in, or
if the access token `/auth/login` mints were rejected by the very next request.
So the only fixture it uses is the platform administrator, who exists because
`SuperAdminBootstrap` creates them from the environment and no endpoint can make
the first one. Every account, token and row after that is created over HTTP by
the person the story says creates it, carrying the bearer token that person was
actually issued.

It is also the only class that is **method-ordered**, and the exception is worth
justifying rather than copying. One story whose steps depend on each other has no
honest independent form: written as thirteen independent tests, each would
rebuild the whole preceding journey and the class would test the setup thirteen
times and the journey once. The cost is that an early failure fails the steps
after it, so the first failure in report order is the real one — which is why
each step is named for the part of the day it covers.

What it does not cover is stated in the class and repeated here, because it is
the half people assume a test called end-to-end includes. MockMvc runs the real
filter chain, the real security, the real controllers and services against real
PostgreSQL. It does not run TLS, the edge proxy, the built frontend bundle or a
browser. That half is the deployment's own smoke test, and splitting them is
deliberate: this one proves the product's behaviour, that one proves the
deployment's plumbing.

`AuditRoleSeparationIT` covers `V13`. The first tests read the grant state, which
is what the migration is directly responsible for; the last one is the one worth
having, because it creates a login role, grants it the group, connects as it, and
checks that an `UPDATE` on `activity_logs` is refused **by PostgreSQL rather than
by the trigger**. A grant that looks right in `pg_catalog` and does not bite is
exactly the failure it guards against.

Note what that class cannot assert, since it says so itself. The application
under test connects as the container's superuser, and a superuser bypasses every
grant, so nothing there proves the *running* application is constrained. That is
done by a deployment pointing `DB_USERNAME` at a member of the group, which is a
step in `deployment.md` rather than a line of code — and a test that pretended
otherwise would be the worst kind, the one that passes because it is asking an
easier question.

The two checks that are not JUnit both run in CI, beside the suite:

- **`deployment-config`** renders the production compose file with throwaway
  values, renders the edge template exactly the way the nginx image's entrypoint
  does, and asks `nginx -t` about the result — for both the edge configuration
  and the frontend image's own. It is cheap, and it catches the class of mistake
  otherwise found at deploy time on the production host: a compose file that does
  not parse, or a typo in a server block.
- **The smoke test in `deploy.yml`** runs against the public hostname after every
  release, from the internet rather than from the host, so it exercises DNS, TLS,
  the edge and the routing. It uses no credential at all, deliberately: an
  unauthenticated call proves the API is routed, the security chain is running
  and the shared error envelope was produced, without a standing production login
  living in a CI secret.

## Mail in tests

There is no mail transport. `RecordingMailSender` captures messages so a test can
read the token out of one, which is how verification, reset and invitation are
driven from end to end with no mail server present. It is registered as the
primary `MailSender` for the whole suite.

## Writing a new integration test

```java
class SomethingIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void doesTheThing() throws Exception {
        mockMvc.perform(get("/api/v1/something")).andExpect(status().isOk());
    }
}
```

Name it `*IT`, extend the base class, and it joins the Failsafe run
automatically.

## Exercising the deadline scan

The scan is the platform's only scheduled job and it is **switched off in the
test profile** (`app.notifications.deadline.enabled=false`). A suite that raced a
background job it did not start would fail on load rather than on a defect, and it
would do so once a day at seven in the morning.

Tests drive it directly instead: `DeadlineScannerIT` autowires `DeadlineScanner`
and calls `run()`, which does the work on the calling thread and returns how many
rows it wrote. Nothing in that class needs `eventually`, unlike the event-driven
notifications, which are written on the module's own thread after commit.

The test profile also sets `app.notifications.deadline.batch-size=2`, so the paging
test needs three tasks rather than five hundred to prove that the scan walks past
its first page.

`DeadlineSchedulerLockIT` holds the advisory lock from outside the application, on
a connection of its own, and asserts that the scanner does nothing at all. That
separation matters: if only the written rows were asserted, the unique dedupe index
would make a completely broken lock look like a working one.

To watch it run locally, set `DEADLINE_SCAN_CRON` to something immediate, for
example `0 * * * * *` for every minute, and give a task a due date inside
`DEADLINE_LEAD_DAYS`.
