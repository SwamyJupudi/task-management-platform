package com.company.taskmanagementplatform.projects;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.projects.dto.ProjectMemberResponse;
import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.MembershipService;

/**
 * Who is on a project, and which of them owns it.
 *
 * <p>Two invariants hold this together, and each is enforced in the schema as well as here. A
 * project member must be a member of the project's workspace, so somebody removed from the workspace
 * cannot linger on its projects. And the owner is always one of the project's own members, which is
 * why naming an owner adds them and why removing a member refuses to strand the owner outside.
 */
@Service
public class ProjectMembershipService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectMapper mapper;
    private final MembershipService workspaceMembers;
    private final UserAccountService users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ProjectMembershipService(
            ProjectRepository projects,
            ProjectMemberRepository members,
            ProjectMapper mapper,
            MembershipService workspaceMembers,
            UserAccountService users,
            ApplicationEventPublisher events,
            Clock clock) {
        this.projects = projects;
        this.members = members;
        this.mapper = mapper;
        this.workspaceMembers = workspaceMembers;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /**
     * A page of the roster, joined with each person's profile.
     *
     * <p>Profiles are fetched in one call rather than one per row, for the same reason every other
     * roster in the platform does it.
     */
    @Transactional(readOnly = true)
    public Page<ProjectMemberResponse> listMembers(UUID workspaceId, UUID projectId, Pageable pageable) {
        Project project = requireProject(workspaceId, projectId);
        Page<ProjectMember> page = members.findAllByProjectId(project.getId(), pageable);

        Map<UUID, UserAccount> accounts =
                users.findAllByIds(page.getContent().stream().map(ProjectMember::getUserId).toList());

        return page.map(member -> toResponse(member, accounts.get(member.getUserId()), project));
    }

    @Transactional
    public ProjectMemberResponse addMember(UUID workspaceId, UUID projectId, UUID userId, UUID actorUserId) {
        Project project = requireProject(workspaceId, projectId);
        requireNotArchived(project);
        requireWorkspaceMember(workspaceId, userId);

        if (members.existsByProjectIdAndUserId(project.getId(), userId)) {
            throw new ConflictException("That person is already on this project.");
        }

        ProjectMember member = members.save(
                ProjectMember.join(project.getId(), workspaceId, userId, actorUserId, clock.instant()));

        // Named in the requirements as a notification trigger, so a later phase is
        // required to be able to hear this.
        events.publishEvent(new ProjectEvents.ProjectMemberAdded(workspaceId, projectId, userId, actorUserId));

        return toResponse(member, users.findById(userId).orElse(null), project);
    }

    /**
     * Takes somebody off a project.
     *
     * <p>Refuses to remove the owner, rather than quietly clearing the ownership as a side effect.
     * Losing a project's owner is a decision somebody should make deliberately, and a caller who
     * meant to do both can do them in either order through two explicit requests.
     *
     * <p>The event is published <em>before</em> the row is deleted, and the change is flushed in
     * between. That ordering became load-bearing when tasks arrived: a task's assignee is a foreign
     * key into {@code project_members}, so PostgreSQL refuses this delete while the person still
     * holds a task on the project. The tasks module stands them down on the event, inside this
     * transaction, which is what makes the removal possible at all rather than tidying that could be
     * deferred. The flush is the other half of it: Hibernate is free to order the statements in a
     * flush by entity type rather than by the order the calls were made.
     */
    @Transactional
    public void removeMember(UUID workspaceId, UUID projectId, UUID userId, UUID actorUserId) {
        Project project = requireProject(workspaceId, projectId);
        requireNotArchived(project);

        ProjectMember member = members.findByProjectIdAndUserId(project.getId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project member", userId));

        if (project.isOwnedBy(userId)) {
            throw new ConflictException("That person owns this project. Name a different owner first.");
        }

        events.publishEvent(new ProjectEvents.ProjectMemberRemoved(workspaceId, projectId, userId, actorUserId));
        members.flush();
        members.delete(member);
    }

    /**
     * Names the project's owner, adding them to the project if they are not on it yet.
     *
     * <p>Adding them keeps the invariant true rather than merely hoped for. Refusing anybody who is
     * not already a member would make the common case two requests and leave the caller to discover
     * the rule from an error.
     */
    @Transactional
    public ProjectResponse assignOwner(UUID workspaceId, UUID projectId, UUID userId, UUID actorUserId) {
        Project project = requireProject(workspaceId, projectId);
        requireNotArchived(project);
        requireWorkspaceMember(workspaceId, userId);

        UUID previous = project.getOwnerUserId();

        if (!members.existsByProjectIdAndUserId(project.getId(), userId)) {
            members.save(ProjectMember.join(project.getId(), workspaceId, userId, actorUserId, clock.instant()));
            events.publishEvent(new ProjectEvents.ProjectMemberAdded(workspaceId, projectId, userId, actorUserId));
        }

        project.assignOwner(userId);
        events.publishEvent(
                new ProjectEvents.ProjectOwnerChanged(workspaceId, projectId, previous, userId, actorUserId));

        return mapper.toResponse(project);
    }

    /** Leaves the project without an owner. The person stays a member. */
    @Transactional
    public ProjectResponse clearOwner(UUID workspaceId, UUID projectId, UUID actorUserId) {
        Project project = requireProject(workspaceId, projectId);
        requireNotArchived(project);

        UUID previous = project.getOwnerUserId();
        if (previous == null) {
            throw new ConflictException("That project has no owner to remove.");
        }

        project.clearOwner();
        events.publishEvent(
                new ProjectEvents.ProjectOwnerChanged(workspaceId, projectId, previous, null, actorUserId));

        return mapper.toResponse(project);
    }

    private void requireWorkspaceMember(UUID workspaceId, UUID userId) {
        if (!workspaceMembers.isMember(workspaceId, userId)) {
            throw new BadRequestException("That person is not a member of this workspace.");
        }
    }

    private Project requireProject(UUID workspaceId, UUID projectId) {
        return projects.findByIdAndWorkspaceIdAndDeletedAtIsNull(projectId, workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private static void requireNotArchived(Project project) {
        if (project.isArchived()) {
            throw new ConflictException("That project is archived. Move it out of archive before editing it.");
        }
    }

    private static ProjectMemberResponse toResponse(ProjectMember member, UserAccount account, Project project) {
        return new ProjectMemberResponse(
                member.getUserId(),
                account == null ? null : account.email(),
                account == null ? null : account.firstName(),
                account == null ? null : account.lastName(),
                project.isOwnedBy(member.getUserId()),
                account == null ? null : account.status().name(),
                member.getJoinedAt());
    }
}
