package com.company.taskmanagementplatform.attachments;

/**
 * What a scanner concluded about one file.
 *
 * <p>Three outcomes, not two, and the third is the one that matters. "I could not find out" is a
 * different fact from "this file is clean", and collapsing them would mean every scanner outage
 * silently admitted unscanned files — which is precisely the failure mode a scanner is bought to
 * prevent. {@code AttachmentService} answers them differently: an infected file is refused and the
 * uploader is told so, an error refuses the upload as a temporary failure and asks them to retry.
 *
 * @param outcome what was decided
 * @param detail the engine's own description — a signature name for an infection, a reason for an
 *     error. Recorded and logged, never shown to the uploader, and never any part of the file itself
 */
public record ScanVerdict(ScanVerdict.Outcome outcome, String detail) {

    public enum Outcome {
        /** Scanned, and nothing found. */
        CLEAN,
        /** Scanned, and something found. The file is refused and its bytes are never stored. */
        INFECTED,
        /** Not scanned. The engine was unreachable, timed out, or answered something unintelligible. */
        ERROR
    }

    private static final ScanVerdict CLEAN = new ScanVerdict(Outcome.CLEAN, null);

    public static ScanVerdict clean() {
        return CLEAN;
    }

    /** @param signature the engine's name for what it found, truncated by the caller if it is long */
    public static ScanVerdict infected(String signature) {
        return new ScanVerdict(Outcome.INFECTED, signature);
    }

    public static ScanVerdict error(String reason) {
        return new ScanVerdict(Outcome.ERROR, reason);
    }

    public boolean isClean() {
        return outcome == Outcome.CLEAN;
    }

    public boolean isInfected() {
        return outcome == Outcome.INFECTED;
    }
}
