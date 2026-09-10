package com.company.taskmanagementplatform.support;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.projects.ProjectContext;
import com.company.taskmanagementplatform.projects.ProjectMembershipService;
import com.company.taskmanagementplatform.projects.ProjectService;
import com.company.taskmanagementplatform.projects.dto.CreateProjectRequest;
import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.subtasks.SubtaskService;
import com.company.taskmanagementplatform.subtasks.dto.CreateSubtaskRequest;
import com.company.taskmanagementplatform.subtasks.dto.SubtaskResponse;
import com.company.taskmanagementplatform.tasks.TaskRef;
import com.company.taskmanagementplatform.tasks.TaskService;
import com.company.taskmanagementplatform.tasks.dto.CreateTaskRequest;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.teams.TeamMembershipService;
import com.company.taskmanagementplatform.teams.TeamService;
import com.company.taskmanagementplatform.teams.dto.CreateTeamRequest;
import com.company.taskmanagementplatform.teams.dto.TeamResponse;

/**
 * Builds the projects, tasks and subtasks a test needs, through the same services the application
 * uses.
 *
 * <p>Through the services rather than by inserting rows, for the reason {@link IdentityFixtures}
 * gives: setting up state with raw SQL is how a suite ends up asserting against a shape the
 * application would never actually produce. The schema tests are the deliberate exception, and they
 * write their own SQL because the constraints are the thing under test.
 *
 * <p>None of these need a security context. Every service here takes the acting person as an
 * argument rather than reading it from the request, which is what lets a fixture drive them
 * directly. Authorization is exercised through MockMvc by the tests that are about authorization.
 */
@TestComponent
public class TaskFixtures {

    private final ProjectService projects;
    private final ProjectMembershipService projectMembers;
    private final ProjectAccessFacade projectAccess;
    private final TeamService teams;
    private final TeamMembershipService teamMembers;
    private final TaskService tasks;
    private final SubtaskService subtasks;

    TaskFixtures(
            ProjectService projects,
            ProjectMembershipService projectMembers,
            ProjectAccessFacade projectAccess,
            TeamService teams,
            TeamMembershipService teamMembers,
            TaskService tasks,
            SubtaskService subtasks) {
        this.projects = projects;
        this.projectMembers = projectMembers;
        this.projectAccess = projectAccess;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.tasks = tasks;
        this.subtasks = subtasks;
    }

    @Transactional
    public ProjectResponse project(UUID workspaceId, String key, UUID creatorUserId) {
        return projects.create(
                workspaceId,
                new CreateProjectRequest(key, "Project " + key, null, null, null, null, null, null, null),
                creatorUserId);
    }

    @Transactional
    public ProjectResponse project(UUID workspaceId, String key, UUID ownerUserId, UUID teamId, UUID creatorUserId) {
        return projects.create(
                workspaceId,
                new CreateProjectRequest(key, "Project " + key, null, ownerUserId, teamId, null, null, null, null),
                creatorUserId);
    }

    @Transactional
    public void addProjectMember(UUID workspaceId, UUID projectId, UUID userId, UUID actorUserId) {
        projectMembers.addMember(workspaceId, projectId, userId, actorUserId);
    }

    @Transactional
    public TeamResponse team(UUID workspaceId, String name, UUID leadUserId, UUID creatorUserId) {
        TeamResponse team = teams.create(workspaceId, new CreateTeamRequest(name, null, null), creatorUserId);
        if (leadUserId != null) {
            teamMembers.assignLead(workspaceId, team.id(), leadUserId, creatorUserId);
        }
        return team;
    }

    @Transactional
    public TaskResponse task(UUID workspaceId, UUID projectId, String title, UUID creatorUserId) {
        return task(workspaceId, projectId, title, null, creatorUserId);
    }

    @Transactional
    public TaskResponse task(
            UUID workspaceId, UUID projectId, String title, UUID assigneeUserId, UUID creatorUserId) {
        return tasks.create(
                workspaceId,
                context(workspaceId, projectId, creatorUserId),
                new CreateTaskRequest(title, null, assigneeUserId, null, null, null, null, null, null, null),
                creatorUserId);
    }

    @Transactional
    public TaskResponse task(UUID workspaceId, UUID projectId, CreateTaskRequest request, UUID creatorUserId) {
        return tasks.create(workspaceId, context(workspaceId, projectId, creatorUserId), request, creatorUserId);
    }

    @Transactional
    public TaskResponse moveTask(UUID workspaceId, UUID taskId, String status, UUID actorUserId) {
        return tasks.changeStatus(workspaceId, taskId, status, actorUserId);
    }

    @Transactional
    public void deleteTask(UUID workspaceId, UUID taskId, UUID actorUserId) {
        tasks.delete(workspaceId, taskId, actorUserId);
    }

    @Transactional
    public SubtaskResponse subtask(TaskRef task, String title, UUID creatorUserId) {
        return subtasks.create(task, new CreateSubtaskRequest(title, null, null, null), creatorUserId);
    }

    @Transactional
    public SubtaskResponse moveSubtask(TaskRef task, UUID subtaskId, String status, UUID actorUserId) {
        return subtasks.changeStatus(task, subtaskId, status, actorUserId);
    }

    @Transactional
    public void deleteSubtask(TaskRef task, UUID subtaskId, UUID actorUserId) {
        subtasks.delete(task, subtaskId, actorUserId);
    }

    /** A reference to a task, as the guard would have produced it, for driving subtasks directly. */
    public TaskRef ref(TaskResponse task) {
        return new TaskRef(task.id(), task.projectId(), task.workspaceId());
    }

    /** The project as the guard would hand it to the service, without needing a security context. */
    public ProjectContext context(UUID workspaceId, UUID projectId, UUID viewerUserId) {
        return projectAccess
                .contextOf(workspaceId, projectId, viewerUserId, true)
                .orElseThrow(() -> new IllegalStateException("No project " + projectId + " in that workspace"));
    }

    /** Labels on a task, applied through the same path an edit uses. */
    @Transactional
    public TaskResponse taskWithLabels(
            UUID workspaceId, UUID projectId, String title, List<String> labels, UUID creatorUserId) {
        return tasks.create(
                workspaceId,
                context(workspaceId, projectId, creatorUserId),
                new CreateTaskRequest(title, null, null, null, null, null, null, null, null, labels),
                creatorUserId);
    }
}
