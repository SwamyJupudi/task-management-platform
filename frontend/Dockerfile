# The frontend image.
#
# A build stage that produces dist/, and an nginx stage that serves it. Nothing
# from the first stage reaches the second except the built files: no node, no
# node_modules, no source, no source maps.
#
# VITE_API_BASE_URL is baked into the bundle at build time — that is how Vite
# works and it cannot be changed at runtime — so it is fixed here at the
# relative /api/v1 rather than exposed as a build argument. That is deliberate
# and it is the same decision frontend/.env.example documents: a relative path
# makes one image correct in every environment, and it keeps the interface and
# the API on one origin, which the SameSite=Strict refresh cookie requires in
# order to be sent at all. An absolute URL here would produce an image that only
# works in the environment it was built for, and would break session refresh.

# ---------------------------------------------------------------------------
# Stage 1: build
# ---------------------------------------------------------------------------
# 22 because package.json's toolchain declares engines "^20.19.0 || >=22.12.0",
# and the CI workflow builds on the same line.
FROM node:22-alpine AS builder

WORKDIR /build

# The lockfile alone first, so `npm ci` is its own cacheable layer.
COPY package.json package-lock.json ./
# `ci` rather than `install`: it installs exactly what the lockfile pins and
# fails if the two have drifted, which is what a reproducible image wants.
RUN npm ci

COPY . .

# `npm run build` runs `tsc -b` before vite, so a type error fails the image
# build rather than shipping. vite.config.ts already drops source maps when the
# mode is production.
ENV VITE_API_BASE_URL=/api/v1
RUN npm run build

# ---------------------------------------------------------------------------
# Stage 2: runtime
# ---------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# The whole configuration, not a fragment dropped into conf.d. It moves the pid
# file and every temporary path under /tmp so the master process can run
# unprivileged; see the comments in the file itself.
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=builder /build/dist /usr/share/nginx/html

# The image ships an `nginx` user at uid 101. It owns the served files and the
# configuration read-only, and writes nothing: logs go to stdout and stderr, and
# the scratch paths are on the tmpfs the compose file mounts at /tmp.
RUN chown -R nginx:nginx /usr/share/nginx/html

USER nginx

# Unprivileged, so not 80. The edge proxy is what the outside world reaches.
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1

# Exec form for the same reason as the backend: nginx should receive SIGTERM
# itself and shut its workers down, rather than being killed through a shell.
CMD ["nginx", "-g", "daemon off;"]
