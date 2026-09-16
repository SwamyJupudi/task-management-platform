# The scan gateway image.
#
# One Python file on the standard library, and nothing else. There is no pip
# install here and no requirements file, which is the point: this process is
# reachable by anything on the backplane network and it handles every byte a
# user uploads, so its dependency tree is the one place in the stack where zero
# is worth the small amount of code it costs.
#
# ClamAV itself is NOT in this image. The official clamav/clamav image runs
# unmodified as a separate service, so that signature updates and engine
# upgrades arrive from upstream rather than from a rebuild of this file.

FROM python:3.12-alpine

# No shell and no home. The process reads a socket and writes to stdout.
RUN addgroup -S -g 10002 scanner && adduser -S -u 10002 -G scanner -H -s /sbin/nologin scanner

COPY scan_gateway.py /app/scan_gateway.py

USER scanner
WORKDIR /app

EXPOSE 8080

# Unbuffered, or the log lines sit in a pipe buffer and `docker logs` shows
# nothing until the process exits -- which during an incident is exactly when
# the lines are wanted.
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1

# Deliberately the same question the application asks at startup: a GET answers
# 200 only while clamd is answering, so an unhealthy container here means the
# scanner is genuinely unavailable rather than that this process died.
HEALTHCHECK --interval=15s --timeout=10s --start-period=15s --retries=3 \
    CMD python3 -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8080/scan', timeout=5).status == 200 else 1)"

ENTRYPOINT ["python3", "/app/scan_gateway.py"]
