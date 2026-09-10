package com.company.taskmanagementplatform.users;

import java.util.UUID;

/**
 * Raised when an account stops being usable, whether it was switched off or removed.
 *
 * <p>Published rather than calling {@code auth} directly, which is the pattern the architecture sets
 * for every cross-module effect. It is also the only way round the dependency here: {@code auth}
 * depends on {@code users}, so {@code users} cannot depend back.
 *
 * <p>Listeners run inside the publishing transaction, not after it commits. Revoking the sessions has
 * to succeed or fail together with the deactivation itself, because a deactivated account with a live
 * session is worse than one that was never deactivated.
 */
public record UserDeactivatedEvent(UUID userId) {}
