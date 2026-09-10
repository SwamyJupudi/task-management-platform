package com.company.taskmanagementplatform.projects;

import java.util.UUID;

/**
 * The filters a project listing accepts. Every field is optional; a null one narrows nothing.
 *
 * <p>Filters compose with the caller's visibility rather than replacing it, so narrowing by owner or
 * team can never reveal a project the caller was not entitled to see.
 */
record ProjectFilter(
        ProjectStatus status,
        ProjectPriority priority,
        UUID teamId,
        UUID ownerUserId,
        String text,
        String label) {

    static ProjectFilter none() {
        return new ProjectFilter(null, null, null, null, null, null);
    }
}
