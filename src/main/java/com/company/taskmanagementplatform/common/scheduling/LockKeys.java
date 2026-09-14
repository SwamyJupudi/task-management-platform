package com.company.taskmanagementplatform.common.scheduling;

/**
 * Every advisory lock the application takes, in one place.
 *
 * <p>{@link AdvisoryLock} has said since it was written that a second key belongs beside the first
 * rather than invented at a call site, and this is that place. The reason is narrow and real: the
 * keys share one namespace across the whole database, so two jobs that each chose a plausible-looking
 * constant could collide, and the symptom would be one of them silently never running because the
 * other held its lock. Collisions are impossible to have by accident when every key is visible in one
 * file.
 *
 * <p>The values themselves are arbitrary. They only have to be distinct, and they must not change
 * once deployed: a changed key during a rolling deployment means the old instances and the new ones
 * are competing for different locks, which is to say not competing at all.
 */
public final class LockKeys {

    /** The daily deadline scan. The first lock the platform took, and its value is unchanged. */
    public static final long DEADLINE_SCAN = 8_421_337_001L;

    /** The nightly purge of expired verification, reset, invitation and refresh tokens. */
    public static final long EXPIRED_TOKEN_PURGE = 8_421_337_002L;

    /** The nightly reclamation of stored objects behind long-since-deleted attachments. */
    public static final long ATTACHMENT_BYTE_PURGE = 8_421_337_003L;

    /** The scan of attachments that have never been inspected, which is the backlog {@code V12} created. */
    public static final long ATTACHMENT_RESCAN = 8_421_337_004L;

    private LockKeys() {}
}
