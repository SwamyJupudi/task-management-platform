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
