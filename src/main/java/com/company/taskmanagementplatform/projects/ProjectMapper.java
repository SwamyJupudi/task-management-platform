package com.company.taskmanagementplatform.projects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.teams.TeamService;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

/**
 * Turns projects into response bodies, resolving the owner, the team, the tags and the roster size.
 *
 * <p>All four are per-row lookups written naively, and a page of twenty projects would then be
 * eighty-one queries. The batch method takes the whole page and does four.
 */
@Component
class ProjectMapper {

    private final UserAccountService users;
    private final TeamService teams;
    private final ProjectMemberRepository members;
    private final ProjectLabelRepository projectLabels;

    ProjectMapper(
            UserAccountService users,
            TeamService teams,
            ProjectMemberRepository members,
            ProjectLabelRepository projectLabels) {
        this.users = users;
        this.teams = teams;
        this.members = members;
        this.projectLabels = projectLabels;
    }

    ProjectResponse toResponse(Project project) {
        return toResponses(List.of(project)).get(0);
    }

    List<ProjectResponse> toResponses(List<Project> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }

        List<UUID> projectIds = projects.stream().map(Project::getId).toList();

        List<UUID> ownerIds = projects.stream()
                .map(Project::getOwnerUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, UserAccount> owners = ownerIds.isEmpty() ? Map.of() : users.findAllByIds(ownerIds);

        List<UUID> teamIds = projects.stream()
                .map(Project::getTeamId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, String> teamNames =
                teamIds.isEmpty() ? Map.of() : teams.teamNamesByIds(projects.get(0).getWorkspaceId(), teamIds);

        Map<UUID, Long> counts = memberCounts(projectIds);
        Map<UUID, List<String>> labels = labelsByProject(projectIds);

        return projects.stream()
                .map(project -> build(
                        project,
                        project.getOwnerUserId() == null ? null : owners.get(project.getOwnerUserId()),
                        project.getTeamId() == null ? null : teamNames.get(project.getTeamId()),
                        labels.getOrDefault(project.getId(), List.of()),
                        counts.getOrDefault(project.getId(), 0L)))
                .toList();
    }

    private Map<UUID, Long> memberCounts(List<UUID> projectIds) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : members.countByProjectIds(projectIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private Map<UUID, List<String>> labelsByProject(List<UUID> projectIds) {
        Map<UUID, List<String>> byProject = new HashMap<>();
        for (Object[] row : projectLabels.findNamesByProjectIds(projectIds)) {
            byProject.computeIfAbsent((UUID) row[0], key -> new ArrayList<>()).add((String) row[1]);
        }
        return byProject;
    }

    private static ProjectResponse build(
            Project project, UserAccount owner, String teamName, List<String> labels, long memberCount) {
        return new ProjectResponse(
                project.getId(),
                project.getWorkspaceId(),
                project.getKey(),
                project.getName(),
                project.getDescription(),
                project.getOwnerUserId(),
                owner == null ? null : owner.email(),
                owner == null ? null : owner.fullName(),
                project.getTeamId(),
                teamName,
                project.getStatus().name(),
                project.getPriority().name(),
                project.getStartDate(),
                project.getEndDate(),
                labels,
                project.getProgress(),
                memberCount,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
