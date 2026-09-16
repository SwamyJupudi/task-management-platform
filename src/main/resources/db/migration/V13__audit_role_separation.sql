-- V13: the privilege half of the append-only audit trail.
--
-- V7 wrote the other half and said what was missing, in terms this migration is
-- the answer to: "the application currently runs its migrations under the same
-- role it serves requests with, so a REVOKE here would break Flyway on the next
-- deployment. The trigger below is the half that works under one role... the
-- separate migration role and the REVOKE are recorded as delivery work." This is
-- the delivery phase, and this is that REVOKE.
--
-- WHY A TRIGGER WAS NOT ENOUGH
--
-- The trigger refuses an UPDATE or a DELETE that reaches the table. It is a good
-- control and it stays. What it cannot do is survive somebody with a connection
-- deciding to remove it: ALTER TABLE ... DISABLE TRIGGER, or DROP TRIGGER, are
-- both available to the role that owns the table, and the application's role
-- owned the table. A privilege the role does not hold cannot be handed back by
-- the role that does not hold it. That is the difference between a rule the
-- application agrees to follow and a rule the database enforces against it.
--
-- WHAT THIS MIGRATION CREATES, AND WHAT IT DELIBERATELY DOES NOT
--
-- It creates one NOLOGIN role, task_platform_app, and puts the runtime privilege
-- set on it. It creates NO LOGIN ROLE and sets NO PASSWORD, because a password
-- in a migration is a password in version control, in every clone of the
-- repository and in the Flyway history table.
--
-- A deployment therefore does two things, once, by hand or in its provisioning:
--
--   CREATE ROLE tmp_app LOGIN PASSWORD '<from the secret store>';
--   GRANT task_platform_app TO tmp_app;
--
-- and then points DB_USERNAME at tmp_app while Flyway runs as the owner through
-- SPRING_FLYWAY_USER and SPRING_FLYWAY_PASSWORD. docs/deployment.md has the full
-- sequence. Until a deployment does that, this migration changes nothing about
-- how anything behaves -- which is what makes it safe to apply everywhere,
-- including to a development database where one superuser does both jobs.
--
-- WHY A GROUP ROLE RATHER THAN NAMING THE APPLICATION'S ROLE
--
-- The privilege set is a property of this schema and belongs in the migration
-- that knows the schema. The identity that holds it is a property of the
-- deployment. Separating them means rotating the application's database
-- credential is CREATE ROLE plus GRANT, with no migration and no downtime, and
-- that a second consumer -- a read-only reporting connection, say -- is a role
-- decision rather than a schema one.
--
-- ON A DEVELOPMENT OR TEST DATABASE THIS IS INERT, AND HONESTLY SO
--
-- Those run as the owning superuser, and a superuser bypasses every grant in
-- PostgreSQL. So the REVOKE below protects nothing there and the trigger from V7
-- remains the only thing standing, exactly as it was. That is not a gap this
-- migration can close: it is what "separate the roles" means, and the separation
-- is done in the deployment. The tests assert the grant state on the role, which
-- is the part this file is responsible for.

-- ---------------------------------------------------------------------------
-- The role
-- ---------------------------------------------------------------------------
-- Guarded rather than plain CREATE ROLE. Roles are cluster-wide, not
-- database-scoped, so a second database in the same cluster -- a staging copy
-- restored beside production, or a developer's scratch database -- would find
-- the role already there and fail the migration on a name clash that means
-- nothing.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'task_platform_app') THEN
        -- NOLOGIN: nothing authenticates as this. It is a bag of privileges that
        -- a login role is granted, and it cannot itself be connected to even if
        -- somebody later gives it a password.
        CREATE ROLE task_platform_app NOLOGIN;
    END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- What the application may do
-- ---------------------------------------------------------------------------
-- Reading the schema, but not adding to it. The application runs with
-- spring.jpa.hibernate.ddl-auto=none and Flyway owns every change, so it has no
-- reason to hold CREATE and every reason not to.
GRANT USAGE ON SCHEMA public TO task_platform_app;

GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA public
    TO task_platform_app;

-- No sequence is in use today: every primary key is a uuid from
-- gen_random_uuid(). Granted anyway so that a later migration introducing one
-- does not produce a runtime failure whose cause is three files away.
GRANT USAGE, SELECT
    ON ALL SEQUENCES IN SCHEMA public
    TO task_platform_app;

-- Tables a later migration creates. ALTER DEFAULT PRIVILEGES applies to objects
-- created by the role running this statement -- the migration role -- which is
-- exactly the role every future migration runs as.
--
-- Without this, V14 would add a table the application cannot read, and the
-- failure would appear at the first request rather than at the deployment.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO task_platform_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO task_platform_app;

-- ---------------------------------------------------------------------------
-- The audit trail: the point of this migration
-- ---------------------------------------------------------------------------
-- The blanket grant above handed out UPDATE and DELETE on every table. This
-- takes them back on the one table where the requirements say they must not
-- exist, and TRUNCATE with them: TRUNCATE is not covered by DELETE, fires no
-- row trigger, and would empty the audit trail in one statement while the V7
-- trigger watched and said nothing.
REVOKE UPDATE, DELETE, TRUNCATE ON activity_logs FROM task_platform_app;

-- Restated rather than left implied. A reader checking what the application may
-- do to the audit trail should find the answer in one place and in the positive.
GRANT SELECT, INSERT ON activity_logs TO task_platform_app;

-- The same treatment for the migration history itself. The application has no
-- business rewriting the record of which migrations ran; Flyway does that, and
-- Flyway runs as the owner. SELECT stays, so an operator connected as the
-- application can still answer "which version is this database on".
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON flyway_schema_history FROM task_platform_app;
GRANT SELECT ON flyway_schema_history TO task_platform_app;

-- A future table is covered by the default privileges above, which grant UPDATE
-- and DELETE. A second append-only table would therefore need its own REVOKE,
-- in its own migration, beside its own trigger. There is no way to express
-- "append-only" once and have it apply to tables that do not exist yet, and a
-- comment that says so is worth more than a clever attempt that half works.
COMMENT ON TABLE activity_logs IS
    'Append only. The V7 trigger refuses UPDATE and DELETE; V13 withholds the privileges from task_platform_app so the trigger cannot simply be removed by the application role.';
