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
| Integration | Auth, project creation, task creation, assignment, permissions   | Phases 2-7 |
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
