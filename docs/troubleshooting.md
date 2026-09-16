# Troubleshooting

What breaks, what it looks like from the outside, and what to do about it.
Ordered roughly by when in the life of a deployment it happens.
[`deployment.md`](deployment.md) is the other half: this one assumes the thing
was working, or was meant to be.

Every entry names the symptom first, because that is what somebody actually has
when they arrive here.

---

## Finding the failure at all

The backend logs structured JSON on stdout, one object per line, with the
correlation id of the request on every line of its own processing.

```bash
cd /opt/task-management-platform
docker compose -f docker-compose.prod.yml ps                    # health, per service
docker compose -f docker-compose.prod.yml logs --tail=200 backend
docker compose -f docker-compose.prod.yml logs --tail=200 backend | grep <request-id>
```

That id is on every response as `X-Request-Id` and in every error body as
`requestId`. A user who reports "it said something went wrong" is holding the
one string that finds their request, so it is worth asking for it before
anything else.

`docker compose ps` is the first command for a reason: a stack where one
container is unhealthy is a different problem from a stack where all of them are
healthy and the answer is still wrong.

---

## The deployment

### The deploy job failed at `up --wait`

`--wait` blocks until every health check passes and fails when one does not, so
the question is which service. `docker compose ps` on the host names it, and
then:

| Unhealthy service | Almost always                                                                       |
| ----------------- | ----------------------------------------------------------------------------------- |
| `clamd`           | A cold signature volume. The first start downloads the whole database — minutes     |
| `scan-gateway`    | `clamd` is not answering yet. It depends on it and reports what it sees              |
| `backend`         | Startup failed. Its log has one line saying which value or dependency was missing    |
| `frontend`        | Rare. The image serves static files and has almost nothing to fail at                |
| `proxy`           | The certificate. See [nginx will not start](#nginx-will-not-start)                   |

A first deployment on a fresh host legitimately takes several minutes to go
healthy, almost all of it ClamAV. The 600-second wait in the deploy job is sized
for that.

### The backend will not start

The application fails fast and says what is wrong. Read the first exception, not
the last one — the stack is a context refresh failure and the useful line is at
the top.

| The log says                                              | What it means                                                                        |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `Could not resolve placeholder …`                          | A required variable is not in `.env`. The name in the message is the variable         |
| `app.security.jwt.secret must be at least 32 bytes … It is 13.` | `JWT_SECRET` is unset, so the placeholder survived as its own literal text       |
| `app.storage.provider is LOCAL, which stores attachments on the application server` | Set `STORAGE_PROVIDER=S3` with a bucket and a region            |
| `app.storage.provider is S3, but … is not set`             | `STORAGE_BUCKET` or `STORAGE_REGION` is missing. The message names which              |
| `app.malware-scan.provider is DISABLED`                    | Disabled reports every file clean without looking at it. `prod` refuses it            |
| `the scanner at … did not answer`                          | The scanner is down. **The application will not start without it**, by design         |
| `Incomplete platform administrator bootstrap configuration` | Half of the `SUPER_ADMIN_*` pair is set. Set both, or neither                         |

The scanner one is worth spelling out, because it is the case where the design
makes an outage worse on purpose: the application probes the scanner while its
context refreshes and refuses to start if it does not answer. A restart during a
scanner outage therefore leaves instances unable to come back. That is what
failing closed costs, it was chosen deliberately, and there is no flag that
relaxes it. Fix the scanner.

### nginx will not start

The `443` server block names the certificate files, so a missing certificate is
a start-up failure rather than a degraded service. On a new host this is the
chicken and egg described in [deployment.md](deployment.md#5-the-certificate):
issue the first certificate standalone, while nothing is bound to port 80.

On an existing host it means the renewal stopped working. Check the host's cron
job, and check that the `certs` volume still has `live/<hostname>/` with
`fullchain.pem`, `privkey.pem` and `chain.pem` — stapling needs the third, and a
renewal that produced only the first two is a configuration that will not load.

### A migration failed

Flyway records a failed migration and refuses to continue until somebody
decides what happened. Look at `flyway_schema_history` for the row with
`success = false`.

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

PostgreSQL runs DDL in a transaction, so a failed migration has rolled back its
own statements. Repairing usually means fixing the migration in a new commit and
deploying forward, then `flyway repair` to clear the failed row. **Do not edit an
applied migration**: the checksum is recorded, and the next deployment will
refuse to start against a file that has changed since it ran.

### Rolling back after a migration has run

A rollback does not undo one. Flyway applies forward only.

Before deploying an earlier image, answer one question: can the previous
application version work against the new schema? Additive changes — a new table,
a new nullable column, a new index — are safe, and almost every migration here
is one of those. A dropped or renamed column, or a new `NOT NULL` without a
default, is not: the old code will fail on the first query that touches it.

If it cannot, the fix is forward. Write the commit that repairs the problem and
deploy it. A rollback that leaves the application unable to read its own database
is a second outage on top of the first.

---

## Sessions and sign-in

### Signing in works, and the next reload signs them out

The refresh cookie is not being sent. It is `SameSite=Strict` and path-scoped to
`/api/v1/auth`, so a browser will not attach it to a request made from another
site — which means the interface and the API must be one origin.

Check, in this order:

1. `APP_SERVER_NAME` matches the hostname in the address bar.
2. `VITE_API_BASE_URL` was `/api/v1` when the image was built. It is baked into
   the bundle and cannot be changed at runtime; `frontend/Dockerfile` fixes it
   for this reason, so this only goes wrong if somebody changed that file.
3. `REFRESH_COOKIE_SECURE` is `true` and the site is genuinely on HTTPS. A
   `Secure` cookie is not stored at all over plain HTTP — and setting it to
   `false` to "fix sign-in" disables the control rather than fixing the TLS.
4. `CORS_ALLOWED_ORIGINS` is the same `https://` origin. In a single-origin
   deployment CORS is nearly vestigial, but a stale development port here will
   still produce confusing failures.

### A verification or password-reset link goes nowhere

`MAIL_LINK_BASE_URL` points at a host that does not serve this application, or
at `http` where the deployment is `https`. It is not derived from the request —
mail is sent from a background thread that has no request to derive it from.

### Everything answers 401 after a deployment

`JWT_SECRET` changed. Access tokens signed with the old key are refused
immediately; refresh tokens are database-backed and survive, so the next refresh
issues a working token and users are not actually signed out. If that is not
what happened, check that the clock on the host is right — a host minutes ahead
of reality issues tokens that are rejected as not yet valid.

---

## Attachments

### A download fails in the browser, and the API looks fine

Two independent things must agree, and each fails with its own message:

| If this is missing                                | The symptom                                        |
| ------------------------------------------------- | -------------------------------------------------- |
| The bucket's CORS rule                            | A CORS error in the browser console on download    |
| `APP_S3_ORIGIN` in the proxy's `connect-src`      | A Content-Security-Policy violation, same moment   |

A download looks like a redirect and behaves like a cross-origin fetch: the
client calls the API with its bearer token, the API answers `302` to a presigned
URL, and `fetch` follows that redirect to the bucket. Both the bucket and the
page's own policy have to permit that second request.
[`deploy/aws/README.md`](../deploy/aws/README.md) has the CORS document;
`APP_S3_ORIGIN` is in `.env`, and it must be the bucket's origin — scheme and
host, no path.

### Uploads answer 409

The file has not been scanned yet, or was not clean. Downloads serve `CLEAN`
only.

On an installation upgraded from before the hardening phase, **every existing
attachment is `PENDING`**, because `V12` recorded them honestly rather than
claiming they had been scanned. They answer `409` until something has actually
looked at them, and the rescan job is the only thing that promotes them: set
`ATTACHMENT_RESCAN_ENABLED=true`, let the backlog clear, and switch it off
again. It reads every unscanned file back out of the bucket, so budget the
egress and the scanner load.

On a new installation there is no backlog and this flag should stay `false`.

### An upload times out

The scan happens inside the upload request. `MALWARE_SCAN_TIMEOUT` is 10s, and
the proxy allows 120s, so a slow scanner produces a slow upload rather than a
truncated one. If uploads are slow across the board, look at `clamd`'s memory:
it loads the whole signature database into memory and the compose file gives it
2G for that reason.

---

## Rate limiting

### Legitimate users are getting 429

The limits are keyed by address, and everybody behind one NAT looks like one
address. The production contract file does not list the `RATE_LIMIT_*` variables,
so a deployment runs on the application's own defaults — 300 requests a minute
per address, 20 against `/auth`, 5 registrations an hour — until it sets them.
`.env.example` documents every one of them and what it is for. Raise the one
that is actually firing rather than `RATE_LIMIT_ENABLED`.

Also check that the proxy is overwriting `X-Forwarded-For` with `$remote_addr`
rather than appending to it. The template does, deliberately: appending leaves
the client's own value first, which lets anybody be rate-limited as a fresh
address on every request — the limiter stops working entirely, which is the
opposite failure and much quieter.

### Redis is down and nothing seems wrong

That is the design. The limiter fails open, with short timeouts and a backoff,
because a counter store being unreachable must not take the application down.
The limits are simply not enforced until it comes back. `app.rate-limit.enabled`
can turn the whole mechanism off for a deployment that limits at its gateway
instead.

---

## Data and the audit trail

### `permission denied for table activity_logs`

Something tried to `UPDATE`, `DELETE` or `TRUNCATE` the audit trail as the
runtime role, and the database refused. That is the control working: `V13`
withholds those privileges from `task_platform_app` so that the append-only
trigger cannot simply be dropped by the application's own role.

If it appears during a **migration**, the migration is running as the runtime
role rather than as the schema owner. Set `SPRING_FLYWAY_USER` and
`SPRING_FLYWAY_PASSWORD`, per [deployment.md](deployment.md#3-the-database).

### A new table is unreadable by the application

The migration that created it ran as a role other than the one `V13` was run as.
`ALTER DEFAULT PRIVILEGES` applies to objects created by the role that executed
it, so every migration must run as the same owner. Grant the missing privileges
once, and then fix the deployment so the next one does not need it:

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON <table> TO task_platform_app;
```

A second append-only table would also need its own `REVOKE`, in its own
migration, beside its own trigger. There is no way to express "append-only" once
for tables that do not exist yet.

### The database is being restored

That is [`database.md`](database.md#backup-and-recovery), which covers the
retention, the procedure, and the part people forget: the bucket has to come
back to the same point as the database, or the rows and the files disagree.

---

## When none of the above fits

Collect these before asking anyone:

- `docker compose -f docker-compose.prod.yml ps`
- the failing request's `requestId`, and the backend log lines carrying it
- the commit SHA that is deployed, which is in `.env` as the image tags
- whether the last deployment succeeded, and whether it applied a migration

The last two together answer the first question anybody will ask, which is
whether this started with a release.
