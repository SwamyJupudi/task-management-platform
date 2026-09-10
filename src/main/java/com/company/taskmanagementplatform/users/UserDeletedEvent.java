package com.company.taskmanagementplatform.users;

import java.util.UUID;

/**
 * Raised when an account is removed, as distinct from merely switched off.
 *
 * <p>The two are deliberately separate events because they call for different consequences. A
 * deactivation ends sessions and nothing else, because the person is expected back. A removal ends
 * sessions and also takes them off every workspace roster, because they are not.
 *
 * <p>Listened for inside the publishing transaction, so a removal that leaves the memberships behind
 * is not a state the system can reach.
 */
public record UserDeletedEvent(UUID userId) {}
