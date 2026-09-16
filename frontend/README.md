# Frontend

The React interface for the internal task and project management platform.
It talks to the Spring backend in this same repository and has no server of
its own.

The routing, API client, stores and configuration are in place;
`src/features/auth` is built — sign-in, registration, email verification,
password recovery, session restore and the route guards — and so is the
authenticated application shell: the workspace-scoped route structure, the
sidebar and drawer, the header, the workspace switcher, the account menu, the
notification entry and the breadcrumbs. The remaining feature folders under
`src/features` hold placeholders and are later phases.

## Stack

| Concern       | Choice                                    |
| ------------- | ----------------------------------------- |
| Build         | Vite                                      |
| Language      | TypeScript, `strict`                      |
| UI            | React, Tailwind CSS v4, shadcn/ui (Radix) |
| Server state  | TanStack Query                            |
| Client state  | Zustand                                   |
| Routing       | React Router                              |
| Forms         | React Hook Form, Zod                      |
| Lint / format | oxlint, Prettier                          |

## Running it

The backend must be up first; see the root `README.md`. It listens on 8080
and its CORS configuration already allows `http://localhost:5173`, which is
why the dev server is pinned to that port.

```bash
cp .env.example .env.local   # then edit if the backend is not on :8080
npm install
npm run dev
```

## Scripts

| Command             | What it does                             |
| ------------------- | ---------------------------------------- |
| `npm run dev`       | Dev server on port 5173                  |
| `npm run build`     | Typecheck, then bundle to `dist/`        |
| `npm run preview`   | Serves the built bundle                  |
| `npm run typecheck` | Types only, no output                    |
| `npm run lint`      | oxlint (`lint:fix` to apply fixes)       |
| `npm run format`    | Prettier (`format:check` to verify only) |

## Layout

```
src/
├── app/           Providers and the route table
├── components/
│   ├── common/    Loading, empty and error states, error boundary
│   ├── layout/    Application shell, header, sidebar, navigation
│   └── ui/        shadcn/ui components. Generated; re-run the CLI to change
├── config/        Typed, validated environment
├── features/      One folder per feature, per section 23 of the requirements
│   └── auth/      Session lifecycle, the five auth screens, guarded routing
├── hooks/         Cross-feature hooks: permissions, active workspace, theme
├── lib/           API client and error model, TanStack Query setup
├── pages/         Standalone pages: 403, 404, no workspace, placeholders
├── stores/        Zustand: session and permissions, interface preferences
└── types/         Shared wire types
```

Each feature folder owns its own components, hooks, API calls and types.
Nothing outside a feature imports from inside one; anything two features
need lives in `components/`, `hooks/` or `lib/`.

## Talking to the API

Every request goes through `src/lib/api`. It targets `VITE_API_BASE_URL`,
which includes the backend's `/api/v1` base path, and understands the two
envelopes the backend returns: the `ApiError` body (`code`, `message`,
`requestId`, per-field `errors`) and the `Page` body (`content`, `page`,
`size`, `totalElements`, ...).

Server state belongs in TanStack Query, which supplies the caching,
pagination and loading and error states the requirements ask for. Only the
session, the permission set and interface preferences live in Zustand.

Errors shown to a user always come from `toUserMessage`, which returns the
backend's own user-facing wording for a deliberate error and one generic
line for anything else. Internal detail is never rendered.

## Routing and the shell

Everything a signed-in user reaches lives under `/w/:workspaceSlug`. The
workspace is the tenant boundary the backend enforces on every query, so it
belongs in the URL rather than only in a store: a link is then complete, it
survives a reload and a bookmark, and two tabs can sit in two workspaces
without fighting over one global "current workspace".

`WorkspaceRoute` turns that slug into the session's active workspace, and it is
the only thing that does. The switcher navigates and lets the route follow, so
a pasted link, the back button and the menu all change workspaces the same way.
A slug the session holds no membership for is rendered as not found, matching
the backend, which answers 404 rather than 403 so a stranger cannot confirm a
slug is real.

The admin panel sits outside the workspace segment, at `/admin`. Its endpoints
are gated on a platform role and take no workspace, so scoping its URL to one
would misdescribe it.

`AppLayout` is the shell. A rail from `md` upwards and a drawer below it both
render the same `SidebarNav`, so an entry cannot exist in one and not the
other, and both are filtered by the same permission checks. The error boundary
and the Suspense fallback sit inside the shell, so a failed or still-loading
screen keeps its navigation.

The workspace switcher reads the membership list `GET /auth/me` already
returned and makes no request of its own: `GET /workspaces` is gated on the
platform permission `workspace:read`, which an ordinary employee does not hold.

Breadcrumbs are derived from the path, because the router is the component form
rather than a data router and route handles are not available. A screen that
loads a record names its own final crumb through `useSetBreadcrumbTitle`.

## Authentication

`src/features/auth` owns the session. Five screens — sign in, register, verify
email, forgot password, reset password — plus the machinery that keeps a
session alive across reloads.

**The access token is held in memory only**, in the session store, and is never
written to `localStorage` or `sessionStorage`. The long-lived credential is the
refresh token, which the backend sets as an `HttpOnly`, `Secure`,
`SameSite=Strict` cookie scoped to `/api/v1/auth`, so no script can read it and
it is not attached to ordinary requests.

A reload therefore loses the access token, and `SessionGate` is what makes that
invisible: on start-up it spends the cookie once at `POST /auth/refresh` for a
new token, then calls `GET /auth/me`. While that is in flight the session status
is `unknown` and the route guards hold at a spinner rather than deciding.

Renewal happens twice over. `session-manager.ts` schedules a refresh a minute
before the token expires, and the API client retries any 401 once behind a
silent refresh. Both funnel through a single in-flight promise, because the
refresh token rotates and presenting a spent one is treated as theft and ends
every session.

Forms use React Hook Form with Zod schemas in `schemas.ts` that mirror the
Jakarta constraints on the matching Spring records, so the browser refuses
exactly what the server would. When the server rejects something anyway, its
`errors` array is mapped back onto the offending fields and the summary is
shown by `FormError`, which renders the backend's own message and the request
id beneath it.

## Permissions

`usePermissions` checks the same permission codes the API is gated on, so
one catalog drives both. Platform permissions come from `GET /auth/me`;
workspace permissions from `GET /workspaces/{id}/me`.

Route guards live in `src/app/routes/route-guard.tsx` and the navigation is
filtered by the same checks. **This hides controls; it does not secure
anything.** Every check is repeated server-side, and the interface is not
where access is enforced.

## Environment

`.env.example` documents every variable. Copy it to `.env.local`, which is
git-ignored.

Only `VITE_`-prefixed values reach the browser bundle, so nothing secret may
go in any `.env` file here. There are no client secrets: the access token is
held in memory and never in `localStorage`, and the refresh token is an
httpOnly cookie the browser manages.

`src/config/env.ts` reads and validates the environment once at start-up, so
a missing value fails immediately rather than as an `undefined` later.

## Shipping it

`Dockerfile` builds `dist/` and serves it from nginx: no node, no
`node_modules`, no source and no source maps reach the runtime image. It runs
unprivileged on port 8080 with a read-only root filesystem, which is why
`nginx.conf` moves the pid file and every scratch path under `/tmp`.

`VITE_API_BASE_URL` is **baked into the bundle at build time** — that is how
Vite works and it cannot be changed at runtime — so the image fixes it at the
relative `/api/v1` rather than exposing it as a build argument. One image is
therefore correct in every environment, and the interface and the API stay on
one origin, which the `SameSite=Strict` refresh cookie requires in order to be
sent at all. An absolute URL here would produce an image that only works where
it was built and would break session refresh everywhere else.

The image carries no TLS, no API proxy and no content security policy. All
three belong to the edge proxy, because all three depend on the deployment
while this image is built once in CI. See
[`../docs/deployment.md`](../docs/deployment.md).
