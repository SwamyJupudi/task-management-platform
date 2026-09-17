# The free-tier demo on Render

One web service, one Postgres instance, nothing else. This is a disposable
demonstration, not a deployment: production is
[`../docker-compose.prod.yml`](../docker-compose.prod.yml) and
[`../../docs/deployment.md`](../../docs/deployment.md), and the two are
different shapes on purpose.

**Read [`application-demo.properties`](../../src/main/resources/application-demo.properties)
before using this for anything real.** Uploads are not scanned, attachments are
discarded on every deploy, and rate limits are not enforced.

---

## What runs

```
Render web service (free, Docker)
├── Spring Boot API              /api/v1/**
└── the built React bundle       everything else, from the same origin
        │
        └── Render Postgres (free)
```

One service, and that is a requirement rather than economy. The refresh cookie
is `SameSite=Strict` and path-scoped to `/api/v1/auth`, so a browser will not
send it to an API on a second hostname: an interface on a Render static site
would sign somebody in and drop them on the next reload, with no error anywhere.
`Dockerfile.demo` builds both halves into one image and `SpaResourceConfig`
serves the bundle, which is what keeps the session working.

---

## Setting it up

1. **Create the service.** Either commit [`render.yaml`](../../render.yaml) and
   use a Blueprint, or create a web service by hand with:

   | Setting | Value |
   | --- | --- |
   | Runtime | Docker |
   | Dockerfile path | `./Dockerfile.demo` |
   | Docker context | `.` |
   | Health check path | `/actuator/health` |
   | Plan | Free |

2. **Point it at the database.** Render shows a connection string like
   `postgres://user:pass@host/db`. Spring cannot use that form. Split it:

   ```
   DB_URL=jdbc:postgresql://HOST:5432/DATABASE?sslmode=require
   DB_USERNAME=user
   DB_PASSWORD=pass
   ```

   Leave `SPRING_FLYWAY_USER` and `SPRING_FLYWAY_PASSWORD` unset. Render gives
   one role, so Flyway uses the datasource and `V13`'s audit-privilege
   separation stays inert — which is supported, and documented in
   `docs/deployment.md` as the single-role case.

3. **Set the origin variables** to this service's own URL, both of them:

   ```
   CORS_ALLOWED_ORIGINS=https://your-service.onrender.com
   MAIL_LINK_BASE_URL=https://your-service.onrender.com
   ```

4. **Set up mail.** The demo sends over a provider's HTTP API, not SMTP — an
   ordinary HTTPS request, so a platform that restricts outbound mail ports
   cannot get in the way, and there is no reputation to build on a shared free
   address.

   Create a free Resend account, verify a sender, and set:

   ```
   MAIL_HTTP_API_KEY=re_xxxxxxxx
   MAIL_FROM=onboarding@resend.dev        # or your verified domain
   MAIL_FROM_NAME=Task Platform
   ```

   Any provider accepting the same body — `from`, `to`, `subject`, `text` — works
   by setting `MAIL_HTTP_URL` as well.

   `MAIL_PROVIDER` does not need setting here: under this profile the key alone
   selects the HTTP transport. Setting `MAIL_PROVIDER=HTTP` asks for it by name
   instead, which works in any profile and makes `MAIL_HTTP_API_KEY` and
   `MAIL_FROM` mandatory — startup fails without them rather than falling back to
   the log.

   **Leave the key unset and the demo still works.** Verification and invitation
   links are written to the Render log with the token intact, and you complete
   either flow by copying one out. That is on in this profile alone, and `prod`
   cannot reach the file that switches it on.

5. **First boot.** Set `SUPER_ADMIN_EMAIL` and `SUPER_ADMIN_PASSWORD`, deploy,
   sign in, change the password, then **remove both variables** and redeploy.
   Left in place they are a standing credential on a public URL.

---

## What to expect from the free tier

- **The service spins down when idle**, and a Spring Boot cold start that also
  runs Flyway takes the better part of a minute. Open the demo a few minutes
  before showing it to anybody.
- **512MB of memory.** `MaxRAMPercentage=75` is set in the image, and the demo
  profile lowers the connection pool to 5 and Tomcat's threads to 20 so that
  neither reserves what a production host would.
- **The filesystem is ephemeral.** Attachments live in `/tmp/attachments` and go
  away on every deploy and every spin-down. The rows describing them survive, so
  a file uploaded before a deploy will fail to download after it.
- **A free Postgres instance expires.** Check the expiry date on yours against
  the date you need the demo for; when it lapses the database is deleted rather
  than stopped.

---

## What is deliberately not here

No Redis, no object storage, no ClamAV, no nginx, no static site — each would be
a paid service or a second free one, and each is either optional or replaced by
a profile default. The production stack has all of them and
`docs/deployment.md` is how that one is deployed.

Nothing in `deploy/` besides this file is used by the demo, and
`.github/workflows/deploy.yml` is not either: Render builds from the repository
on push.
