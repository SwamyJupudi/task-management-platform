-- V12: the malware scan state of each attachment.
--
-- Three columns rather than one boolean, because "safe" and "not safe" are not
-- the only two things that can be true of a file. A scan can be outstanding, and
-- a file can have been refused, and the download guard has to be able to tell
-- those apart from a file that was actually inspected and passed.
--
-- THE LIFECYCLE
--
--   PENDING   Not inspected. NOT downloadable. Two kinds of row are in this
--             state: every attachment that existed before this migration, and
--             anything an asynchronous scanner has accepted but not yet cleared.
--             Today's upload path never writes it -- the scan happens before
--             anything is stored, so a file that cannot be scanned is refused
--             and no row is written at all.
--   SCANNING  Reserved for an asynchronous engine that has the file in hand.
--             NOT downloadable.
--   CLEAN     Inspected and passed. The only state a download is served from,
--             and the only state a successful scanner result can produce.
--   REJECTED  Something was found. NOT downloadable. Written either by an
--             upload that was refused -- which stores nothing, so it produces no
--             row -- or by the rescan finding malware in a file that predates
--             scanning, which is the case that actually creates these rows.
--
-- The guard is written as "CLEAN or refuse", not as "not REJECTED", so a state
-- added later is refused by default rather than served by default. That is the
-- same way round as the security chain, and for the same reason.
--
-- EXISTING ROWS ARE PENDING, NOT CLEAN
--
-- The column defaults to PENDING and there is deliberately NO backfill, so every
-- attachment that already exists becomes PENDING and stops being downloadable
-- until a scanner has actually looked at it.
--
-- The alternative was considered and rejected. Marking them CLEAN would avoid a
-- visible change -- every existing file would keep downloading -- but it would be
-- a lie written into the data: nothing had inspected those files, and the column
-- exists precisely to record whether something had. Worse, it is a lie that gets
-- harder to find over time, because once a row says CLEAN there is nothing left
-- to distinguish it from a row a real engine cleared except a null timestamp
-- nobody is obliged to look at. A platform that scans uploads but serves an
-- unscanned backlog as though it were scanned has the appearance of the control
-- without the control.
--
-- WHAT THIS COSTS, STATED PLAINLY
--
-- Applying this migration to an installation with existing attachments makes all
-- of them unavailable for download until they are rescanned. Their rows, their
-- metadata and their listings are untouched -- the guard is on the bytes, not on
-- the record -- so nothing disappears from a task; the download answers 409 while
-- the file is unscanned. `AttachmentRescan` is what clears the backlog: it reads
-- each PENDING file out of the object store, scans it, and promotes it to CLEAN
-- or marks it REJECTED. It is off by default like the other scheduled jobs, so a
-- deployment upgrading a populated installation has to switch it on, and should
-- expect the backlog to take as long as the scanner needs.
--
-- ONLY A SCAN MAY PRODUCE CLEAN
--
-- There is no path in the application that sets CLEAN without a scanner having
-- returned a clean verdict for those exact bytes: `Attachment.createScanned` is
-- reached only after the upload scan passes, and `Attachment.markScanClean` is
-- reached only from the rescan after a clean verdict. `scanned_at` is set at the
-- same moment in both, so a CLEAN row with a null `scanned_at` is not a thing
-- this application can produce.

ALTER TABLE attachments
    ADD COLUMN scan_status    text        NOT NULL DEFAULT 'PENDING',
    -- The engine's own name for what it found. Never any part of the file.
    ADD COLUMN scan_signature text,
    -- When an engine actually looked. NULL means none ever has, which is the
    -- state every row that predates this migration is in.
    ADD COLUMN scanned_at     timestamptz;

ALTER TABLE attachments
    ADD CONSTRAINT attachments_scan_status_check
        CHECK (scan_status IN ('PENDING', 'SCANNING', 'CLEAN', 'REJECTED')),
    -- A signature is the record of something having been found, so it belongs
    -- only to a rejection. Allowing one on a clean row would make the column
    -- mean two different things depending on the status beside it.
    ADD CONSTRAINT attachments_scan_signature_only_when_rejected_check
        CHECK (scan_signature IS NULL OR scan_status = 'REJECTED'),
    -- CLEAN is a claim that something looked, so it has to carry the moment it
    -- did. This is the constraint that makes "only a successful scan produces
    -- CLEAN" checkable in the database rather than only in the code: a backfill,
    -- a migration or a stray UPDATE that tried to mark rows clean without a
    -- timestamp is refused outright.
    ADD CONSTRAINT attachments_clean_requires_scanned_at_check
        CHECK (scan_status <> 'CLEAN' OR scanned_at IS NOT NULL);

-- What the rescan reads: the files that are not servable yet. Partial, because in
-- a healthy installation almost every row is CLEAN and the index should not carry
-- them. On an installation upgrading with a backlog this index is every row, and
-- it shrinks as the rescan works through them.
CREATE INDEX attachments_scan_pending_idx
    ON attachments (scan_status)
    WHERE scan_status <> 'CLEAN';
