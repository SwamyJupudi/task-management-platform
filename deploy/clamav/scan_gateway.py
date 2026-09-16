#!/usr/bin/env python3
"""The shim between the application's HTTP scan contract and clamd's INSTREAM protocol.

The application does not name a scanning engine and does not depend on one. It POSTs
bytes to a URL and reads the verdict from the status code, and that contract --
written out in full on HttpMalwareScanner -- is small enough that any engine can be
put behind it. This is the thirty-odd lines that class predicted, in front of ClamAV.

THE CONTRACT, restated here so that this file can be read on its own:

    POST <url>   body = the file, Content-Type: application/octet-stream
        200  clean. The body is ignored.
        422  infected. The FIRST LINE of the body is recorded as the signature.
        any other status, a timeout or a connection failure
             an error. The upload is REFUSED rather than accepted unscanned.

    GET <url>    the reachability probe
        any 2xx, 4xx or 405  something is listening; the application starts
        5xx or a transport failure  unreachable; the application REFUSES TO START

That last line is why the GET below answers 503 when clamd is not responding rather
than 200 for "the gateway is up". Under the prod profile the application probes this
endpoint while its context refreshes and will not start if the answer is a server
error. A gateway that reported itself healthy while the engine behind it was dead
would turn a fail-closed design into a fail-open one, silently, which is the single
worst thing this file could do.

NOTHING ABOUT A FILE IS EVER LOGGED. Not the content, not a filename -- none is sent
-- and not the size. A signature name is the engine's own vocabulary and says what
was found rather than what was in the file, so that is logged and nothing else is.

Standard library only, on purpose: an image with no dependency tree is an image with
no dependency advisories to track, and this process is reachable by anything that can
reach the backplane network.
"""

import logging
import os
import signal
import socket
import struct
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# --- configuration ---------------------------------------------------------
CLAMD_HOST = os.environ.get("CLAMD_HOST", "clamd")
CLAMD_PORT = int(os.environ.get("CLAMD_PORT", "3310"))
LISTEN_PORT = int(os.environ.get("LISTEN_PORT", "8080"))

# The most this process will read into memory for one request. Kept at or above
# the application's ATTACHMENT_MAX_SIZE (10MB by default) and below clamd's own
# StreamMaxLength (25MB by default), so the refusal below is the one that fires.
MAX_SCAN_BYTES = int(os.environ.get("MAX_SCAN_BYTES", str(16 * 1024 * 1024)))

# How long to wait on clamd. The application's MALWARE_SCAN_TIMEOUT is 10s by
# default and is paid inside a user's upload request, so this is deliberately
# under it: the gateway should give up and answer, rather than have the caller
# time out first and be left unable to tell an error from a slow scan.
CLAMD_TIMEOUT = float(os.environ.get("CLAMD_TIMEOUT", "8"))

# clamd sends chunks of this size. 64KB is the size the protocol documentation
# uses and is comfortably under clamd's StreamMaxLength check per chunk.
CHUNK_SIZE = 64 * 1024

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s scan-gateway %(message)s",
    stream=sys.stdout,
)
log = logging.getLogger("scan-gateway")


class ScanError(Exception):
    """clamd could not give a verdict. Always becomes a 503, never a 200."""


def _connect():
    return socket.create_connection((CLAMD_HOST, CLAMD_PORT), timeout=CLAMD_TIMEOUT)


def _read_reply(sock):
    """Read up to clamd's NUL terminator.

    The z-prefixed commands used below make clamd terminate its reply with a NUL
    rather than a newline, which is the unambiguous framing: a signature name may
    contain almost anything, and a newline-framed reply cannot be parsed safely.
    """
    buffer = bytearray()
    while b"\0" not in buffer:
        block = sock.recv(4096)
        if not block:
            break
        buffer.extend(block)
        # A reply is a short status line. Anything longer is clamd misbehaving,
        # and reading it forever would be a way to exhaust this process.
        if len(buffer) > 8192:
            raise ScanError("clamd sent an oversized reply")
    return bytes(buffer).split(b"\0", 1)[0].decode("utf-8", "replace").strip()


def ping():
    """True when clamd answers. Used by the reachability probe and nothing else."""
    try:
        with _connect() as sock:
            sock.sendall(b"zPING\0")
            return _read_reply(sock) == "PONG"
    except (OSError, ScanError) as exc:
        log.warning("clamd did not answer a ping: %s", type(exc).__name__)
        return False


def scan(content):
    """Return (clean, signature).

    clean is True or False; signature is the engine's name for what it found, and
    is only meaningful when clean is False.

    Raises ScanError for anything that is not a verdict, so that the caller cannot
    accidentally treat "I could not tell" as "clean". That distinction is the whole
    point of the contract.
    """
    try:
        with _connect() as sock:
            sock.sendall(b"zINSTREAM\0")

            for start in range(0, len(content), CHUNK_SIZE):
                chunk = content[start : start + CHUNK_SIZE]
                sock.sendall(struct.pack("!I", len(chunk)) + chunk)

            # A zero-length chunk ends the stream. Without it clamd waits.
            sock.sendall(struct.pack("!I", 0))

            reply = _read_reply(sock)
    except OSError as exc:
        raise ScanError("clamd could not be reached: %s" % type(exc).__name__) from exc

    # "stream: OK" | "stream: <Signature> FOUND" | "<something> ERROR"
    if reply.endswith("OK"):
        return True, None
    if reply.endswith("FOUND"):
        body = reply.split(":", 1)[1].strip() if ":" in reply else reply
        signature = body[: -len("FOUND")].strip() or "unnamed"
        return False, signature
    raise ScanError("clamd answered %r" % reply)


class Handler(BaseHTTPRequestHandler):
    # The default identifies the Python version to anybody who asks.
    server_version = "scan-gateway"
    sys_version = ""

    protocol_version = "HTTP/1.1"

    def _respond(self, status, body=b""):
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    def do_GET(self):
        """The reachability probe.

        503 when clamd is silent, and that is load-bearing: the application treats
        a 5xx here as "no scanner deployed" and refuses to start. See the module
        docstring.
        """
        if not self._is_scan_path():
            self._respond(404, b"not found\n")
            return

        if ping():
            self._respond(200, b"ok\n")
        else:
            self._respond(503, b"clamd is not answering\n")

    def do_POST(self):
        if not self._is_scan_path():
            self._respond(404, b"not found\n")
            return

        raw_length = self.headers.get("Content-Length")
        if raw_length is None:
            # The application always sends one. A body without a length would have
            # to be read until the connection closed, which is unbounded.
            self._respond(411, b"a Content-Length is required\n")
            return

        try:
            length = int(raw_length)
        except ValueError:
            self._respond(400, b"the Content-Length is not a number\n")
            return

        if length < 0 or length > MAX_SCAN_BYTES:
            # 413 rather than 422. The file was not found to be infected; it was
            # never inspected, and the application must not record a verdict.
            self._respond(413, b"the body is larger than this gateway will scan\n")
            return

        content = self.rfile.read(length)
        if len(content) != length:
            self._respond(400, b"the body was shorter than its Content-Length\n")
            return

        try:
            clean, signature = scan(content)
        except ScanError as exc:
            # 503, never 200. An upload that could not be scanned is refused by the
            # application rather than accepted unscanned, and that only happens if
            # this answers with an error.
            log.error("scan failed: %s", exc)
            self._respond(503, b"the scan could not be completed\n")
            return

        if clean:
            self._respond(200, b"clean\n")
            return

        log.warning("infected upload refused: signature=%s", signature)
        # The first line of the body is what the application records as the
        # signature, so the signature is the first line and there is nothing
        # before it.
        self._respond(422, (signature + "\n").encode("utf-8"))

    def _is_scan_path(self):
        # The path is whatever the deployment put in MALWARE_SCAN_URL. Both the
        # bare root and /scan are accepted so that a URL with or without the
        # suffix works, and nothing else is, so a stray request to some other path
        # is a 404 rather than a scan.
        return self.path.split("?", 1)[0].rstrip("/") in ("", "/scan")

    def log_message(self, fmt, *args):
        # The default writes to stderr in a format nothing parses. Access logging
        # is off entirely: every request to this service carries a file, and the
        # useful events -- an infection, an error -- are logged above.
        pass


def main():
    server = ThreadingHTTPServer(("0.0.0.0", LISTEN_PORT), Handler)
    server.daemon_threads = True

    def shutdown(signum, _frame):
        log.info("stopping on signal %s", signum)
        # From a signal handler, so it must not block: shutdown() waits for the
        # serve_forever loop, which is this thread.
        server._BaseServer__shutdown_request = True

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)

    log.info(
        "listening on :%d, scanning through clamd at %s:%d, refusing bodies over %d bytes",
        LISTEN_PORT,
        CLAMD_HOST,
        CLAMD_PORT,
        MAX_SCAN_BYTES,
    )
    server.serve_forever()
    server.server_close()


if __name__ == "__main__":
    main()
