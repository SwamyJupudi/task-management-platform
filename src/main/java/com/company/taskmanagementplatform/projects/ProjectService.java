package com.company.taskmanagementplatform.projects;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.projects.dto.CreateProjectRequest;
import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.projects.dto.UpdateProjectRequest;
import com.company.taskmanagementplatform.teams.TeamService;
import com.company.taskmanagementplatform.workspaces.MembershipService;

/**
 * Projects: creating them, editing them, and moving them through their lifecycle.
 *
 * <p>Membership and ownership live in {@link ProjectMembershipService}, following the shape of the
 * permissions: this class is {@code project:create}, {@code project:update} and {@code
 * project:delete}, and that one is {@code project:manage_members}.
 *
 * <p>Reaching other modules happens through their services and never their tables: {@link
 * MembershipService} answers whether somebody belongs to the workspace, {@link TeamService} whether
 * a team is one of its own.
 *
 * <p>Every method takes identifiers and loads the row itself, because the guard authorizes in its own
 * read-only transaction and anything it returned would arrive detached.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectLabelService labels;
    private final ProjectMapper mapper;
    private final MembershipService workspaceMembers;
    private final TeamService teams;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ProjectService(
            ProjectRepository projects,
            ProjectMemberRepository members,
            ProjectLabelService labels,
            ProjectMapper mapper,
            MembershipService workspaceMembers,
            TeamService teams,
            ApplicationEventPublisher events,
            Clock clock) {
        this.projects = projects;
        this.members = members;
        this.labels = labels;
        this.mapper = mapper;
        this.workspaceMembers = workspaceMembers;
        this.teams = teams;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Creates a project in PLANNING, and puts the owner on it if one was named.
     *
     * <p>The opening status is not the caller's to choose. Allowing it would make the transition
     * rules optional, since any state could be reached by creating a project already in it.
     */
    @Transactional
    public ProjectResponse create(UUID workspaceId, CreateProjectRequest request, UUID creatorUserId) {
        String key = requireKey(request.key());
        String name = requireName(request.name());
        requireKeyIsFree(workspaceId, key, null);
        requireNameIsFree(workspaceId, name, null);
        requireDateOrder(request.startDate(), request.endDate());

        Project project = projects.save(Project.create(workspaceId, key, name, creatorUserId));
        project.describe(trimToNull(request.description()));
        project.schedule(request.startDate(), request.endDate());

        if (request.priority() != null) {
            project.reprioritise(parsePriority(request.priority()));
        }

        if (request.teamId() != null) {
            requireTeamInWorkspace(workspaceId, request.teamId());
            project.assignTeam(request.teamId());
        }

        if (request.ownerUserId() != null) {
            requireWorkspaceMember(workspaceId, request.ownerUserId());
            members.save(ProjectMember.join(
                    project.getId(), workspaceId, request.ownerUserId(), creatorUserId, clock.instant()));
            project.assignOwner(request.ownerUserId());
        }

        projects.flush();
        labels.replace(workspaceId, project.getId(), request.labels());

        events.publishEvent(new ProjectEvents.ProjectCreated(
                workspaceId, project.getId(), project.getKey(), project.getName(), creatorUserId));

        return mapper.toResponse(project);
    }

    @Transactional
    public ProjectResponse update(UUID workspaceId, UUID projectId, UpdateProjectRequest request, UUID actorUserId) {
        Project project = requireProject(workspaceId, projectId);
        requireNotArchived(project);

        if (request.name() != null) {
            String name = requireName(request.name());
            requireNameIsFree(workspaceId, name, projectId);
            project.rename(name);
        }

        if (request.description() != null) {
            project.describe(trimToNull(request.description()));
        }

        if (Boolean.TRUE.equals(request.clearTeam())) {
            project.clearTeam();
        } else if (request.teamId() != null) {
            requireTeamInWorkspace(workspaceId, request.teamId());
            project.assignTeam(request.teamId());
        }

        if (request.priority() != null) {
            project.reprioritise(parsePriority(request.priority()));
        }

        // Read both dates from the request where sent, so a change to one is still
        // checked against the other rather than against a value being replaced.
        LocalDate start = request.startDate() != null ? request.startDate() : project.getStartDate();
        LocalDate end = request.endDate() != null ? request.endDate() : project.getEndDate();
        if (request.startDate() != null || request.endDate() != null) {
            requireDateOrder(start, end);
            project.schedule(start, end);
        }

        if (request.labels() != null) {
            labels.replace(workspaceId, projectId, request.labels());
        }

        events.publishEvent(new ProjectEvents.ProjectUpdated(workspaceId, projectId, actorUserId));
        return mapper.toResponse(project);
    }

    /**
     * Moves a project to another status, if the state machine allows it.
     *
     * @throws ConflictException naming both statuses, if the move is not legal from here
     */
    @Transactional
    public ProjectResponse changeStatus(UUID workspaceId, UUID projectId, String target, UUID actorUserId) {
        Project project = requireProject(workspaceId, projectId);
        ProjectStatus to = parseStatus(target);
        ProjectStatus from = project.getStatus();

        if (from == to) {
            throw new ConflictException("That project is already " + readable(to) + ".");
        }
        if (!from.canMoveTo(to)) {
            throw new ConflictException(
                    "A project that is " + readable(from) + " cannot be moved to " + readable(to) + ".");
        }

        project.moveTo(to);
        events.publishEvent(new ProjectEvents.ProjectStatusChanged(workspaceId, projectId, from, to, actorUserId));
        return mapper.toResponse(project);
    }

    /**
     * Hides a project and frees its key and name.
     *
     * <p>The roster and the tags are left in place. The project row still exists, so the join rows
     * are not orphans, and keeping them makes restoring the row a complete restore rather than a
     * partial one. Every read filters on {@code deleted_at}, so none of it is reachable meanwhile.
     */
    @Transactional
    public void delete(UUID workspaceId, UUID projectId, UUID actorUserId) {
        Project project = requireProject(workspaceId, projectId);
        project.softDelete(clock.instant());
        events.publishEvent(new ProjectEvents.ProjectDeleted(workspaceId, projectId, actorUserId));
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(
            UUID workspaceId, ProjectFilter filter, ProjectVisibility visibility, Pageable pageable) {

        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), ProjectQuery.validateSort(pageable.getSort()));

        Page<Project> page = projects.findAll(ProjectQuery.matching(workspaceId, filter, visibility), sorted);
        List<ProjectResponse> mapped = mapper.toResponses(page.getContent());

        return new PageImpl<>(mapped, sorted, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProjectResponse describe(UUID workspaceId, UUID projectId) {
        return mapper.toResponse(requireProject(workspaceId, projectId));
    }

    // --- rules ------------------------------------------------------------

    private void requireWorkspaceMember(UUID workspaceId, UUID userId) {
        if (!workspaceMembers.isMember(workspaceId, userId)) {
            throw new BadRequestException("That person is not a member of this workspace.");
        }
    }

    private void requireTeamInWorkspace(UUID workspaceId, UUID teamId) {
        if (!teams.existsInWorkspace(workspaceId, teamId)) {
            throw new BadRequestException("That team does not exist in this workspace.");
        }
    }

    private void requireKeyIsFree(UUID workspaceId, String key, UUID excludingId) {
        if (projects.existsByFoldedKey(workspaceId, key, excludingId)) {
            throw new ConflictException("A project with that key already exists in this workspace.");
        }
    }

    private void requireNameIsFree(UUID workspaceId, String name, UUID excludingId) {
        if (projects.existsByFoldedName(workspaceId, name, excludingId)) {
            throw new ConflictException("A project with that name already exists in this workspace.");
        }
    }

    private Project requireProject(UUID workspaceId, UUID projectId) {
        return projects.findByIdAndWorkspaceIdAndDeletedAtIsNull(projectId, workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private static void requireDateOrder(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BadRequestException("A project cannot end before it starts.");
        }
    }

    private static void requireNotArchived(Project project) {
        if (project.isArchived()) {
            throw new ConflictException("That project is archived. Move it out of archive before editing it.");
        }
    }

    private static String requireKey(String raw) {
        String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!key.matches("^[A-Z][A-Z0-9]{1,9}$")) {
            throw new BadRequestException("A project key is 2 to 10 letters and digits, starting with a letter.");
        }
        return key;
    }

    private static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BadRequestException("A project needs a name.");
        }
        return name;
    }

    static ProjectStatus parseStatus(String raw) {
        try {
            return ProjectStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("That is not a project status.");
        }
    }

    static ProjectPriority parsePriority(String raw) {
        try {
            return ProjectPriority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("That is not a project priority.");
        }
    }

    private static String readable(ProjectStatus status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
