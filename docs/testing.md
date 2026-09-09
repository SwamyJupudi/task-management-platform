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
