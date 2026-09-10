package com.company.taskmanagementplatform.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.projects.ProjectContext;
import com.company.taskmanagementplatform.tasks.dto.TaskLinkResponse;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

/**
 * Turns tasks into response bodies, resolving the people, the project and the labels.
 *
 * <p>Every one of those is a per-row lookup written naively, and a page of twenty tasks would then be
 * a hundred queries. The batch method takes the whole page and does a fixed handful regardless of
 * how many rows are on it.
 *
 * <p>The rendered key is composed here rather than stored. A task is {@code PLAT-12} to everybody who
 * talks about it, but the project key and the number are the two facts, and keeping a third copy of
 * their concatenation in the table would be a third thing to keep in step.
 */
@Component
class TaskMapper {

    private final UserAccountService users;
    private final ProjectAccessFacade projects;
    private final TaskLabelRepository taskLabels;
    private final TaskRepository tasks;

    TaskMapper(
            UserAccountService users,
            ProjectAccessFacade projects,
            TaskLabelRepository taskLabels,
            TaskRepository tasks) {
        this.users = users;
        this.projects = projects;
        this.taskLabels = taskLabels;
        this.tasks = tasks;
    }

    TaskResponse toResponse(Task task, UUID viewerUserId) {
        return toResponses(List.of(task), viewerUserId).get(0);
    }

    List<TaskResponse> toResponses(List<Task> page, UUID viewerUserId) {
        if (page.isEmpty()) {
            return List.of();
        }

        List<UUID> taskIds = page.stream().map(Task::getId).toList();

        Map<UUID, UserAccount> people = peopleOn(page);
        Map<UUID, ProjectSummary> projectSummaries = projectsOf(page, viewerUserId);
        Map<UUID, List<String>> labels = labelsByTask(taskIds);
        Map<UUID, List<TaskLinkResponse>> blockedBy = linksFrom(tasks.findBlockersOf(taskIds), projectSummaries, page);
        Map<UUID, List<TaskLinkResponse>> blocking = linksFrom(tasks.findBlockedBy(taskIds), projectSummaries, page);

        return page.stream()
                .map(task -> build(
                        task,
                        projectSummaries.get(task.getProjectId()),
                        people.get(task.getAssigneeUserId()),
                        people.get(task.getReporterUserId()),
                        labels.getOrDefault(task.getId(), List.of()),
                        blockedBy.getOrDefault(task.getId(), List.of()),
                        blocking.getOrDefault(task.getId(), List.of())))
                .toList();
    }

    /** Every assignee and reporter on the page, in one query. */
    private Map<UUID, UserAccount> peopleOn(List<Task> page) {
        List<UUID> ids = page.stream()
                .flatMap(task -> java.util.stream.Stream.of(task.getAssigneeUserId(), task.getReporterUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return ids.isEmpty() ? Map.of() : users.findAllByIds(ids);
    }

    /**
     * The key and name of each project on the page.
     *
     * <p>One call per distinct project rather than per task. A board is one project, and even a
     * cross-project listing rarely spans more than a handful.
     */
    private Map<UUID, ProjectSummary> projectsOf(List<Task> page, UUID viewerUserId) {
        Map<UUID, ProjectSummary> summaries = new HashMap<>();
        for (Task task : page) {
            summaries.computeIfAbsent(task.getProjectId(), projectId -> projects
                    .contextOf(task.getWorkspaceId(), projectId, viewerUserId, true)
                    .map(ProjectSummary::of)
                    .orElseGet(() -> new ProjectSummary("", "")));
        }
        return summaries;
    }

    private Map<UUID, List<String>> labelsByTask(List<UUID> taskIds) {
        Map<UUID, List<String>> byTask = new HashMap<>();
        for (Object[] row : taskLabels.findNamesByTaskIds(taskIds)) {
            byTask.computeIfAbsent((UUID) row[0], key -> new ArrayList<>()).add((String) row[1]);
        }
        return byTask;
    }

    /**
     * Groups dependency rows by the task they hang off.
     *
     * <p>Both directions come back in the same shape, so one method builds both. The project key on
     * the far end is the same project as the near end, because a dependency cannot cross projects.
     */
    private Map<UUID, List<TaskLinkResponse>> linksFrom(
            List<Object[]> rows, Map<UUID, ProjectSummary> projectSummaries, List<Task> page) {

        Map<UUID, UUID> projectByTask = new HashMap<>();
        page.forEach(task -> projectByTask.put(task.getId(), task.getProjectId()));

        Map<UUID, List<TaskLinkResponse>> byTask = new HashMap<>();
        for (Object[] row : rows) {
            UUID owner = (UUID) row[0];
            UUID otherId = (UUID) row[1];
            int number = ((Number) row[2]).intValue();
            String title = (String) row[3];
            TaskStatus status = (TaskStatus) row[4];

            ProjectSummary project = projectSummaries.get(projectByTask.get(owner));
            String key = project == null ? String.valueOf(number) : project.key() + "-" + number;

            byTask.computeIfAbsent(owner, id -> new ArrayList<>())
                    .add(new TaskLinkResponse(otherId, number, key, title, status.name()));
        }
        return byTask;
    }

    private static TaskResponse build(
            Task task,
            ProjectSummary project,
            UserAccount assignee,
            UserAccount reporter,
            List<String> labels,
            List<TaskLinkResponse> blockedBy,
            List<TaskLinkResponse> blocking) {

        String projectKey = project == null ? "" : project.key();

        return new TaskResponse(
                task.getId(),
                task.getWorkspaceId(),
                task.getProjectId(),
                projectKey,
                project == null ? "" : project.name(),
                task.getTaskNumber(),
                projectKey + "-" + task.getTaskNumber(),
                task.getTitle(),
                task.getDescription(),
                task.getAssigneeUserId(),
                assignee == null ? null : assignee.email(),
                assignee == null ? null : assignee.fullName(),
                task.getReporterUserId(),
                reporter == null ? null : reporter.email(),
                reporter == null ? null : reporter.fullName(),
                task.getStatus().name(),
                task.getPriority().name(),
                task.getStartDate(),
                task.getDueDate(),
                task.getEstimatedMinutes(),
                task.getActualMinutes(),
                task.getBoardPosition(),
                labels,
                blockedBy,
                blocking,
                blockedBy.stream().anyMatch(link -> !TaskStatus.DONE.name().equals(link.status())),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }

    /** The two project fields a task response shows, so the whole context is not carried around. */
    private record ProjectSummary(String key, String name) {

        static ProjectSummary of(ProjectContext context) {
            return new ProjectSummary(context.key(), context.name());
        }
    }
}
