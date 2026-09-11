package com.company.taskmanagementplatform.notifications;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.tasks.TaskDigest;
import com.company.taskmanagementplatform.tasks.TaskNotificationFacade;

/**
 * Who hears about what.
 *
 * <p>Three rules run through every trigger, and they are here rather than repeated in seven
 * listeners:
 *
 * <ol>
 *   <li><strong>The actor is never a recipient.</strong> Telling somebody they did the thing they
 *       just did is how a feed becomes noise people stop reading.
 *   <li><strong>One action is one notification per person.</strong> Being both the assignee and the
 *       reporter of a task does not earn two copies.
 *   <li><strong>A mention beats the comment it is in.</strong> Somebody named in a comment gets the
 *       mention and not the "new comment" line, which is the same fact told twice.
 * </ol>
 *
 * <p>The rules are static and take plain identifiers, so they can be tested without a database, a
 * container or a Spring context. The instance methods do the looking up and hand the answers
 * straight to them.
 *
 * <p><strong>Recipients are relationships, not permissions.</strong> Nothing here consults a
 * permission, and that is deliberate: everybody chosen is somebody who can already see the thing
 * being described, because they are its assignee, its reporter, a member of its project or the
 * person just added to it. A notification therefore cannot widen anybody's access. What it can do is
 * outlive it, which is what the cleanup listener and the render-time check are for.
 */
@Component
class RecipientResolver {

    private final TaskNotificationFacade tasks;
    private final ProjectAccessFacade projects;

    RecipientResolver(TaskNotificationFacade tasks, ProjectAccessFacade projects) {
        this.tasks = tasks;
        this.projects = projects;
    }

    /**
     * The people with a standing interest in one task: its assignee and its reporter.
     *
     * <p>Not everybody on the project. A busy project would make every status change a message to
     * twenty people, and a notification everybody gets is one nobody reads.
     */
    List<UUID> forTask(UUID workspaceId, UUID taskId, UUID actorUserId, UUID... alsoExcluded) {
        Optional<TaskDigest> task = tasks.find(workspaceId, taskId);
        if (task.isEmpty()) {
            return List.of();
        }

        TaskDigest digest = task.get();
        // Arrays.asList rather than List.of: an unassigned task has a null assignee,
        // and List.of refuses nulls rather than carrying them to the filter below.
        return exclude(
                Arrays.asList(digest.assigneeUserId(), digest.reporterUserId()),
                actorUserId,
                union(alsoExcluded));
    }

    /** Everybody on a project, plus its owner, which is what a project-wide change concerns. */
    List<UUID> forProject(UUID workspaceId, UUID projectId, UUID actorUserId) {
        return exclude(projects.audienceOf(workspaceId, projectId), actorUserId, Set.of());
    }

    /**
     * The three rules, applied.
     *
     * <p>Nulls are dropped rather than rejected: an unassigned task has a null assignee and an
     * absent actor is the platform itself, and both are ordinary rather than exceptional. Order is
     * preserved so that the rows written for one action are written in a predictable order.
     */
    static List<UUID> exclude(Collection<UUID> candidates, UUID actorUserId, Collection<UUID> alreadyTold) {
        Set<UUID> recipients = new LinkedHashSet<>();
        for (UUID candidate : candidates) {
            if (candidate != null && !candidate.equals(actorUserId) && !alreadyTold.contains(candidate)) {
                recipients.add(candidate);
            }
        }
        return List.copyOf(recipients);
    }

    /** One person, run through the same rules, so a single-recipient trigger cannot skip them. */
    static List<UUID> only(UUID candidate, UUID actorUserId) {
        return candidate == null || candidate.equals(actorUserId) ? List.of() : List.of(candidate);
    }

    private static Set<UUID> union(UUID... ids) {
        Set<UUID> all = new LinkedHashSet<>();
        for (UUID id : ids) {
            if (id != null) {
                all.add(id);
            }
        }
        return all;
    }
}
