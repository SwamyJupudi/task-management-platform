package com.company.taskmanagementplatform.workspaces;

/**
 * Raised when an invitation has been written, carrying the token that was just generated.
 *
 * <p>The raw token travels on the event because it exists nowhere else: the table holds only its
 * hash. It lives in memory until the message is handed to the transport, and is never logged.
 *
 * <p>Listened for after commit, so a mail transport that is slow or down cannot roll back an
 * invitation that was correctly recorded.
 */
record WorkspaceInvitationIssuedEvent(String email, String workspaceName, String rawToken) {}
