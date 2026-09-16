# The backend image.
#
# Two stages. The first compiles against a full JDK and a Maven repository; the
# second carries a JRE, the application, and nothing else — no compiler, no
# build cache, no source. The jar built in the first stage is never copied
# whole: it is extracted into its four layers so that a code change rebuilds one
# small layer instead of a sixty-megabyte one.
#
# Tests are NOT run here. CI runs them against a real PostgreSQL through
# Testcontainers before it builds an image at all, and running them inside a
# build would either need a Docker socket mounted into the builder or would
# silently skip the integration half — which is exactly the failure the CI
# workflow already has an explicit guard against.

# ---------------------------------------------------------------------------
# Stage 1: build
# ---------------------------------------------------------------------------
# 17 because pom.xml declares <java.version>17</java.version>. Maven comes from
# the image rather than from ./mvnw: the wrapper jar is git-ignored, so the
# wrapper would download itself on every build for no benefit.
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# The pom alone first, so the dependency download is its own layer and survives
# every change that does not touch the dependency list. This is the single
# biggest saving in the whole build.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

# The four layers, ordered from least to most likely to change. `--launcher`
# also unpacks the loader, so the runtime stage needs no jar on its classpath
# and starts a fraction faster for not having to open one.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# ---------------------------------------------------------------------------
# Stage 2: runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# busybox wget answers the HEALTHCHECK below; alpine already has it, so nothing
# is installed here. Every package added to this stage is a package somebody has
# to watch for advisories.

# A non-root user with no shell and no home. The application writes nothing to
# disk — attachments go to S3 and logs go to stdout — so it owns nothing it can
# write to, which is the point.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H -s /sbin/nologin app

WORKDIR /app

# One COPY per layer, in the order the layer index declares. Each is owned by
# the runtime user but stays read-only in practice: the compose file runs this
# container with a read-only root filesystem.
COPY --from=builder --chown=app:app /build/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/application/ ./

USER app

# The port inside the container, which is fixed on purpose. SERVER_PORT still
# works and a deployment is free to set it, but then the HEALTHCHECK below and
# the compose file's proxy target both have to move with it, so the default is
# the one thing everything else can assume.
EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx, so the heap follows the container
# limit instead of the host's memory. 75 leaves room for metaspace, thread
# stacks, direct buffers and the JVM itself inside the same limit.
#
# ExitOnOutOfMemoryError because a JVM that has run out of heap does not
# recover: it thrashes, serves slowly, and keeps passing a liveness probe that
# only checks whether the port answers. Exiting hands the problem to the
# orchestrator, which knows how to restart something.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport -Djava.security.egd=file:/dev/urandom"

# READINESS, not liveness, and not plain /actuator/health.
#
# `docker compose` gates `depends_on: condition: service_healthy` on this, and
# what a dependent service needs to know is whether this instance is ready to
# take traffic — which is what the readiness group answers. Liveness would go
# healthy the moment the port opened, before Flyway had finished.
#
# start-period covers the slow part of a first boot: the migration, and the
# malware scanner probe that the prod profile performs before the context
# finishes refreshing. Failures during that window do not count.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -q --spider http://127.0.0.1:8080/actuator/health/readiness || exit 1

# Exec form, so java is PID 1 and receives SIGTERM directly. Through a shell it
# would not, the JVM would be killed after the orchestrator's grace period, and
# `server.shutdown=graceful` — which application-prod.properties sets and bounds
# with SHUTDOWN_GRACE_PERIOD — would never once run.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
