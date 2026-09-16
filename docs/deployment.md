# Deployment

How this platform is deployed, by whom, and what has to exist before any of it
works. The artifacts it describes live in [`deploy/`](../deploy), in the two
Dockerfiles at the repository root and under `frontend/`, and in the two GitHub
Actions workflows. This document is the reasoning and the order; those files are
the truth about the values.

Backup and recovery are deliberately not here. They belong to the database and
the bucket rather than to the release process, and they are in
[`database.md`](database.md#backup-and-recovery). When something is broken rather
than being deployed, [`troubleshooting.md`](troubleshooting.md) is the other
half of this document.

---

## The shape of it

One host, running the stack in [`deploy/docker-compose.prod.yml`](../deploy/docker-compose.prod.yml),
with two managed services beside it:

| Piece                       | Where it runs                    | Why                                                                |
| --------------------------- | -------------------------------- | ------------------------------------------------------------------ |
| Edge proxy (nginx)          | The host                         | Terminates TLS, serves one origin, routes `/api/v1` to the backend  |
| Interface (nginx + `dist/`) | The host                         | Static files and the history fallback, nothing else                 |
| Application (Spring Boot)   | The host                         | The API                                                             |
| Redis                       | The host                         | Rate-limit counters only. Not a cache — see `architecture.md`       |
| ClamAV + scan gateway       | The host                         | Uploads are scanned before they are readable                        |
| **PostgreSQL**              | **A managed instance**           | Backups, point-in-time recovery and failover that nobody has to run |
| **Object storage**          | **S3 or an S3-compatible store** | Attachments. The `prod` profile refuses to write them to disk       |

The two in bold are not containers on this host and are not meant to become
them. A database container here would have no automated backup, no
point-in-time recovery, no failover and a volume nobody is watching, which is
the opposite of what this platform commits to. The compose file carries a
`rehearsal` profile with stand-ins for both so the stack can be brought up on a
laptop; it is not production and says so in the file.

The host builds nothing and holds no source. Images are built by CI, pushed to
GHCR tagged with the commit that produced them, and pulled here.

```
/opt/task-management-platform/
  docker-compose.prod.yml          <- copied by the deploy job
  nginx/templates/app.conf.template  <- copied by the deploy job
  .env                             <- written by the deploy job, 0600, root-owned
```

---

## Before the first deployment

Five things have to exist, in this order. None of them is created by the
pipeline, because each involves a credential or a name that a person chooses.

### 1. The host

A Linux host with Docker Engine and the Compose plugin, `rsync`, and ports 80
and 443 reachable from the internet. The deploy job connects over SSH as a user
that can run `docker` and write to `/opt/task-management-platform`.

Nothing else is installed. In particular there is no Java, no Node and no Maven
on the host: if any of those are needed to deploy, something has gone wrong with
the pipeline rather than with the host.

### 2. DNS

An `A` record for the public hostname pointing at the host, resolving before the
certificate is issued. The name goes into `APP_SERVER_NAME`, and it is the only
name the edge answers to — the proxy closes any other connection with `444`
rather than serving a certificate that does not match.

### 3. The database

A managed PostgreSQL 16 instance, reachable from the host, with TLS. Create the
database and then the two roles the application uses. **There are two, and the
separation is the point.**

The application connects as a role that does not own the schema and cannot
change the audit trail; migrations connect as the role that owns it. `V13`
creates the privilege set as a `NOLOGIN` group called `task_platform_app`, and
holds no password of its own — a password in a migration is a password in
version control.

Ordering matters here, because the group role does not exist until the first
migration has run:

```sql
-- As the instance's master user, once.
CREATE DATABASE taskmanagement;

-- The migration role. It owns the schema, so it is the role that runs Flyway
-- and the only role that may alter anything. CREATEROLE is not decoration:
-- V13 issues CREATE ROLE, and a managed instance's non-superuser cannot.
CREATE ROLE tmp_migrator LOGIN CREATEROLE PASSWORD '<from the secret store>';
GRANT CONNECT ON DATABASE taskmanagement TO tmp_migrator;
-- Then, connected to taskmanagement:
ALTER SCHEMA public OWNER TO tmp_migrator;

-- The runtime role. No schema ownership, and no privileges of its own yet.
CREATE ROLE tmp_app LOGIN PASSWORD '<from the secret store>';
GRANT CONNECT ON DATABASE taskmanagement TO tmp_app;
```

Deploy once with `SPRING_FLYWAY_USER=tmp_migrator` set and `DB_USERNAME` **also**
pointed at `tmp_migrator`, so that the first migration run can create the group
role. Then grant it and switch the runtime role over:

```sql
-- task_platform_app now exists, because V13 created it. Run this as
-- tmp_migrator or as the master user: granting membership in a role needs
-- ADMIN OPTION on it, which its creator holds.
GRANT task_platform_app TO tmp_app;
```

Set `DB_USERNAME=tmp_app` and `DB_PASSWORD` to its password, and deploy again.
From then on the running application has the ordinary four privileges on every
table except one: on `activity_logs` it holds `SELECT` and `INSERT` and nothing
more, which is what makes the append-only audit trail a rule the database
enforces rather than one the application agrees to follow.
`AuditRoleSeparationIT` proves the group carries those privileges and that they
bite; only this step makes the running application subject to them.

Three things about this that are easy to get wrong:

- **Every migration must run as the same role.** `V13` uses `ALTER DEFAULT
  PRIVILEGES`, which applies to objects created by the role that ran it. A
  migration run later as a different owner would create tables the application
  cannot read, and the failure would appear at the first request rather than at
  the deployment.
- **A deployment with one role is supported and is not a bug.** Leave
  `SPRING_FLYWAY_USER` and `SPRING_FLYWAY_PASSWORD` unset and Flyway uses the
  datasource. Development and the tests work exactly this way. What is lost is
  precisely the privilege separation above, and nothing else.
- **`CONNECT` is granted to `PUBLIC` by default.** If the instance has had that
  revoked, as some hardening baselines do, both roles need it explicitly, as
  above.

Then set the backup policy on the instance. [`database.md`](database.md#backup-and-recovery)
says what it has to be and why.

### 4. The bucket

[`deploy/aws/README.md`](../deploy/aws/README.md) has the whole of it: the three
permissions the application actually uses, why versioning is not optional, why
the obvious encryption deny statement would break every upload, and why the CORS
rule and the proxy's `connect-src` have to agree. Apply it before the first
deployment; `StorageConfig` refuses to start without `STORAGE_BUCKET` and
`STORAGE_REGION` and names the one that is missing.

Prefer an instance profile or a container role over a key pair. The SDK's
default credential chain finds it with no configuration at all, and there is
deliberately no property for an access key.

### 5. The certificate

The edge proxy's `443` server block names the certificate files, so **nginx will
not start without them**. That is a chicken and egg on a new host: certbot's
webroot mode needs the proxy running to serve the challenge, and the proxy needs
the certificate to run. Break it once, with a standalone issuance while nothing
is bound to port 80:

```bash
docker volume create task-management-platform-prod_certs

docker run --rm -p 80:80 \
    -v task-management-platform-prod_certs:/etc/letsencrypt \
    certbot/certbot certonly --standalone \
    -d app.example.com \
    -m ops@example.com --agree-tos --no-eff-email
```

The volume name is the compose project name — fixed as
`task-management-platform-prod` in the compose file — followed by the volume's
own name. Everything after this is webroot renewal through the running proxy,
which needs no downtime; see [Renewing the certificate](#renewing-the-certificate).

---

## The pipeline

Two workflows, kept apart on purpose.

**[`ci.yml`](../.github/workflows/ci.yml)** runs on every push and pull request:
lint, test and build both halves of the repository, then build all three images.
Images are pushed to GHCR **only from `master`**, so a pull request proves its
Dockerfiles still build without being able to publish anything — which matters,
because a pull request from a fork runs with a read-only token and must stay
that way. A third job renders and validates the compose file, the edge template
and the frontend's nginx configuration, so a typo in any of them fails the pull
request that introduced it rather than the deployment that used it.

**[`deploy.yml`](../.github/workflows/deploy.yml)** runs afterwards, and only
after CI succeeded on `master`. It needs a credential that can reach the
production host and an approval gate, and neither belongs in the workflow that
every pull request runs.

```
merge to master -> CI -> images pushed, tagged with the commit SHA
                      -> Deploy waits for a reviewer
                      -> rsync config, write .env, pull, up --wait, smoke test
```

A merge does not deploy on its own. Somebody releases it.

### What to configure once, in the repository

| Kind                   | Name                 | What it is                                                              |
| ---------------------- | -------------------- | ----------------------------------------------------------------------- |
| Environment            | `production`         | With **required reviewers**. This setting, not the workflow, is the gate |
| Variable               | `APP_SERVER_NAME`    | The public hostname. Used for the environment URL and the smoke test     |
| Secret (environment)   | `DEPLOY_HOST`        | The host the deploy job connects to                                     |
| Secret (environment)   | `DEPLOY_USER`        | The SSH user on it                                                      |
| Secret (environment)   | `DEPLOY_SSH_KEY`     | Its private key. Nothing else should use this key                       |
| Secret (environment)   | `DEPLOY_KNOWN_HOSTS` | The host's public key, from `ssh-keyscan`. Pinned, not trusted on first use |
| Secret (environment)   | `PRODUCTION_ENV`     | **The whole environment file**, in the shape of `.env.production.example` |

`PRODUCTION_ENV` is one secret holding one file rather than fifteen assembled in
the workflow. Assembling it there would put every variable name in the log's
step list and make a missing one a silent empty value; a single file is reviewed
and rotated as one thing. The deploy job appends the three image references to
it, because those are the only values that change from one deployment to the
next.

The secrets belong to the `production` **environment** rather than to the
repository, so they are unreadable until a reviewer releases the job.

### The environment file

[`.env.production.example`](../.env.production.example) is the contract: every
variable, which ones are required, and which differ from what a developer runs.
It contains no values and never will.

Four things in it are worth reading twice before the first deployment:

- `SUPER_ADMIN_EMAIL` and `SUPER_ADMIN_PASSWORD` are read only when the platform
  has no administrator at all. Set them for the first boot, sign in, change the
  password, and then **remove both lines** and redeploy. Left in place they are a
  standing credential in a file that has outlived its purpose.
- `JWT_SECRET` must be at least 32 bytes. Rotating it invalidates every access
  token immediately; refresh tokens are database-backed and survive, so nobody
  is signed out.
- `ATTACHMENT_RESCAN_ENABLED` stays `false` on a new installation. It exists for
  upgrading one that already had attachments before the hardening phase, and it
  reads every unscanned file back out of the bucket.
- `SHUTDOWN_GRACE_PERIOD` must stay below the compose file's
  `stop_grace_period` of 45s, or Docker sends `SIGKILL` first and the graceful
  shutdown never runs.

### The first deployment

With the five prerequisites in place and the secrets set, run the **Deploy**
workflow from the Actions tab with no `image_tag`, and approve it. It will:

1. `mkdir -p /opt/task-management-platform` and rsync `deploy/` into it, with
   `--delete` so a removed file is removed here too, and `.env` excluded because
   the next step writes it.
2. Write `.env` through `umask 077`, so it is never briefly world-readable.
3. `docker login ghcr.io` with the run's own token, `pull`, and
   `up -d --remove-orphans --wait --wait-timeout 600`.
4. `docker logout`, prune untagged layers, and smoke test from the public
   internet.

The first start is the slow one. ClamAV downloads its whole signature database,
which takes minutes on a cold volume; its health check allows six of them. The
backend will not start until the scanner is healthy, because under `prod` it
probes the scanner while its context refreshes and refuses to start without one.
That is failing closed, it is deliberate, and there is no flag to relax it.

### Routine deployments

Merge to `master`, wait for CI, approve the deployment. The smoke test at the
end checks the things that break independently of the application:

- the interface is served, and a deep link falls back to the document, which is
  how a missing history fallback shows up;
- `/actuator/health` reports `UP`;
- `/api/v1/workspaces` refuses an unauthenticated call with `401`, which proves
  the API is routed, the security chain is running and the shared error envelope
  was produced;
- plaintext redirects, and HSTS is present on the document.

No credential is used anywhere in that job, deliberately. An unauthenticated
call proves as much as an authenticated one about whether the deployment works,
without a standing production login living in a CI secret.

### Rolling back

Every image is tagged with the commit that produced it, so rolling back is
deploying the previous commit: run the **Deploy** workflow with `image_tag` set
to that SHA.

**A rollback does not undo a migration.** Flyway applies forward only, and a
schema change that has run stays run. Before rolling back across one, check
whether the previous application version can still work against the new schema.
If it cannot, the fix is forward — a new commit that repairs the problem — and
[`troubleshooting.md`](troubleshooting.md) has the procedure for deciding.

---

## Operating it

### Renewing the certificate

Renewal is webroot through the running proxy, driven from the host's scheduler
rather than from a container in a sleep loop, because a cron job that failed is
easier to notice than a container that quietly stopped trying:

```cron
0 3 * * * cd /opt/task-management-platform && \
  docker compose -f docker-compose.prod.yml --profile certbot run --rm certbot && \
  docker compose -f docker-compose.prod.yml exec -T proxy nginx -s reload
```

The reload is not optional: nginx reads the certificate at start-up and holds
it, so a renewed certificate is not served until the master process is told.

### Looking at it

```bash
cd /opt/task-management-platform
docker compose -f docker-compose.prod.yml ps            # what is running and healthy
docker compose -f docker-compose.prod.yml logs -f backend
```

The backend logs structured JSON on stdout, with the correlation id of the
request on every line. That id is also on every response as `X-Request-Id` and
in the error envelope as `requestId`, so a user reporting a failure can hand
over the one string that finds it.

### Restarting one service

```bash
docker compose -f docker-compose.prod.yml up -d --wait backend
```

The proxy resolves its upstreams per request through Docker's embedded DNS, so
replacing a container does not need the proxy reloaded. That is why the upstream
names are held in variables in the template, and it is worth not undoing.

### Rotating a credential

| Credential        | How                                                                                  |
| ----------------- | ------------------------------------------------------------------------------------ |
| Database password | `ALTER ROLE tmp_app PASSWORD ...`, update `PRODUCTION_ENV`, deploy                    |
| `JWT_SECRET`      | Update `PRODUCTION_ENV`, deploy. Access tokens die immediately; nobody is signed out  |
| `REDIS_PASSWORD`  | Update `PRODUCTION_ENV`, deploy. The limiter fails open for the seconds in between    |
| Runtime DB role   | `CREATE ROLE` + `GRANT task_platform_app`, repoint `DB_USERNAME`. No migration needed |

The last row is the reason the privilege set is on a group role rather than
named directly: rotating the application's database identity is two statements
and a deployment, with no schema change and no downtime.

---

## Rehearsing the whole stack on a laptop

The `rehearsal` profile starts stand-ins for the two managed services, so the
production compose file can be brought up and actually exercised. Services under
a profile are inert until the profile is named, so an ordinary `up -d` in
production cannot start them by accident.

```bash
# Build the three images locally, with the tags deploy/.env names.
docker build -t tmp-backend:test .
docker build -t tmp-frontend:test frontend
docker build -t tmp-scan-gateway:test deploy/clamav

# A throwaway certificate for localhost, into the volume the proxy reads. The
# template staples, so chain.pem has to exist as well as the other two; CI's
# configuration check generates the same three the same way.
docker volume create task-management-platform-prod_certs
docker run --rm -v task-management-platform-prod_certs:/etc/letsencrypt alpine:3 sh -c '
  apk add --no-cache openssl >/dev/null &&
  mkdir -p /etc/letsencrypt/live/localhost &&
  openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj "/CN=localhost" \
      -keyout /etc/letsencrypt/live/localhost/privkey.pem \
      -out /etc/letsencrypt/live/localhost/fullchain.pem &&
  cp /etc/letsencrypt/live/localhost/fullchain.pem /etc/letsencrypt/live/localhost/chain.pem'

cd deploy
docker compose -f docker-compose.prod.yml --profile rehearsal up -d --wait
```

`deploy/.env` holds the rehearsal values and is git-ignored, as every `.env` in
this repository is. It is not a production file and the passwords in it are not
secrets; it exists so that the rehearsal runs under the `prod` profile, which is
the only thing that makes it a rehearsal. Running it under `dev` would prove
nothing about the settings production actually uses — the storage provider, the
scanner being a hard dependency, the JSON logs and the closed API console are
all profile-conditional.

Tear it down with `docker compose -f docker-compose.prod.yml --profile rehearsal down -v`.

---

## What is deliberately not automated

- **Provisioning.** There is no Terraform here. One host, one database and one
  bucket are a morning's work to create and a standing dependency to maintain,
  and inventing an infrastructure-as-code layout nobody reviewed would put an
  unapproved decision in the repository. The `deploy/aws` documents are the
  bucket's configuration, applied by the commands in their README.
- **Scaling.** The compose file runs one of everything. Running two backends
  behind the same proxy works — the application holds no session state, and the
  scheduled jobs take an advisory lock precisely so that two instances do not
  both run them — but it has not been exercised, and saying so is worth more
  than a `deploy.replicas` line that has never been tried.
- **A staging environment.** The `rehearsal` profile is what exists instead. A
  second host with its own database and bucket would be the honest version, and
  it is a cost decision rather than a technical one.
