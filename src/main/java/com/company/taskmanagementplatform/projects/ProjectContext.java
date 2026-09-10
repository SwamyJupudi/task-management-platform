package com.company.taskmanagementplatform.projects;

import java.util.UUID;

/**
 * What another module needs to know about one project to authorize something inside it.
 *
 * <p>Deliberately a record of answers rather than the project itself. A guard runs in its own
 * read-only transaction, so an entity handed out of this module would arrive detached and every
 * change made to it would be discarded without an error. That was a real defect during the teams
 * build and the rule is written down in {@code architecture.md}.
 *
 * @param archived the project is frozen: readable, and refusing every change
 * @param readable the caller may see this project, so a failure here is 404 rather than 403
 * @param ownedOrLed the caller owns it or leads its team, which is the write scope one level down
 */
public record ProjectContext(
        UUID projectId,
        UUID workspaceId,
        String key,
        String name,
        boolean archived,
        boolean readable,
        boolean ownedOrLed) {}
