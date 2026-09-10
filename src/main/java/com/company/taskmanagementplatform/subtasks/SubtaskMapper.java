package com.company.taskmanagementplatform.subtasks;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.subtasks.dto.SubtaskResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

/**
 * Turns subtasks into response bodies, resolving the assignee.
 *
 * <p>The batch method takes the whole checklist and resolves the people in one query, because a
 * lookup per row is exactly the shape that turns a list endpoint into N+1 without anybody noticing
 * until it is in production.
 */
@Component
class SubtaskMapper {

    private final UserAccountService users;

    SubtaskMapper(UserAccountService users) {
        this.users = users;
    }

    SubtaskResponse toResponse(Subtask subtask) {
        return toResponses(List.of(subtask)).get(0);
    }

    List<SubtaskResponse> toResponses(List<Subtask> subtasks) {
        if (subtasks.isEmpty()) {
            return List.of();
        }

        List<UUID> assigneeIds = subtasks.stream()
                .map(Subtask::getAssigneeUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, UserAccount> assignees = assigneeIds.isEmpty() ? Map.of() : users.findAllByIds(assigneeIds);

        return subtasks.stream()
                .map(subtask -> build(
                        subtask,
                        subtask.getAssigneeUserId() == null ? null : assignees.get(subtask.getAssigneeUserId())))
                .toList();
    }

    private static SubtaskResponse build(Subtask subtask, UserAccount assignee) {
        return new SubtaskResponse(
                subtask.getId(),
                subtask.getWorkspaceId(),
                subtask.getProjectId(),
                subtask.getTaskId(),
                subtask.getTitle(),
                subtask.getAssigneeUserId(),
                assignee == null ? null : assignee.email(),
                assignee == null ? null : assignee.fullName(),
                subtask.getStatus().name(),
                subtask.getStatus().isComplete(),
                subtask.getDueDate(),
                subtask.getPosition(),
                subtask.getCompletedAt(),
                subtask.getCreatedAt(),
                subtask.getUpdatedAt());
    }
}
