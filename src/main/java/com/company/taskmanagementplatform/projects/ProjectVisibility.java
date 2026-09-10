package com.company.taskmanagementplatform.projects;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * What one caller may see of a workspace's projects.
 *
 * <p>The requirements say an employee views <em>assigned</em> projects, so reading is not a yes or no
 * at the method boundary: it decides which rows come back. Somebody without {@code project:read_any}
 * sees the projects they own, belong to, or whose team they lead, and nothing else.
 *
 * @param unrestricted true when the caller holds {@code project:read_any}
 * @param ledTeamIds the live teams this caller leads in the workspace, empty if none
 */
record ProjectVisibility(UUID userId, boolean unrestricted, Collection<UUID> ledTeamIds) {

    static ProjectVisibility everything(UUID userId) {
        return new ProjectVisibility(userId, true, List.of());
    }

    static ProjectVisibility assignedOnly(UUID userId, Collection<UUID> ledTeamIds) {
        return new ProjectVisibility(userId, false, List.copyOf(ledTeamIds));
    }
}
