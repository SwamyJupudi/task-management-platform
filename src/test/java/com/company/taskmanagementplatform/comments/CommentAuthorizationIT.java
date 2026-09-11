package com.company.taskmanagementplatform.comments;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.projects.dto.ProjectResponse;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.teams.dto.TeamResponse;

/**
 * Who may read a thread, write on it, edit it, and remove from it.
 *
 * <p>Three rules are the substance of this phase's authorization and each has a test here.
 *
 * <p>A comment is visible exactly when its task is, so there is no comment read permission at all.
 * Anybody who can see a task may write on it, so the write scope that narrows editing a task to its
 * assignee and reporter deliberately does not apply. And editing is author-only however wide the
 * caller's grants are, while deleting widens to the project's owner, its team lead, and a holder of
 * {@code comment:manage_any}.
 */
class CommentAuthorizationIT extends AbstractCollaborationIT {

    @Test
    void somebodyOffTheProjectCannotSeeTheThreadAtAll() throws Exception {
        // 404 rather than 403: a forbidden response would confirm the task exists.
        Scene scene = scene();
        UUID outsider = member(scene, "EMPLOYEE");
        collaboration.comment(ref(scene), "internal discussion", scene.adminId());

        mockMvc.perform(get(commentsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmployeeOnTheProjectMayReadAndWrite() throws Exception {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");

        write(scene, employee, "I will pick this up").andExpect(status().isCreated());

        mockMvc.perform(get(commentsPath(scene)).header(HttpHeaders.AUTHORIZATION, bearer(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anEmployeeMayCommentOnATaskTheyNeitherHoldNorRaised() throws Exception {
        // The point of the contribution rule. The task write scope would refuse
        // them, and a discussion nobody but the assignee may join is not one.
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        TaskResponse someoneElsesTask =
                taskFixtures.task(scene.workspaceId(), scene.projectId(), "Not theirs", scene.adminId());

        mockMvc.perform(post(workspacePath(scene) + "/tasks/" + someoneElsesTask.id() + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", "one thought"))))
                .andExpect(status().isCreated());
    }

    @Test
    void nobodyMayRewriteSomebodyElsesWords() throws Exception {
        // Not even the workspace administrator, who holds comment:manage_any.
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        CommentResponse theirs = collaboration.comment(ref(scene), "my own words", employee);

        edit(scene, theirs.id(), scene.adminId(), "words I did not write").andExpect(status().isForbidden());
    }

    @Test
    void anAuthorMayEditTheirOwn() throws Exception {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        CommentResponse theirs = collaboration.comment(ref(scene), "my own words", employee);

        edit(scene, theirs.id(), employee, "my own words, corrected").andExpect(status().isOk());
    }

    @Test
    void anAuthorMayRemoveTheirOwn() throws Exception {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        CommentResponse theirs = collaboration.comment(ref(scene), "never mind", employee);

        remove(scene, theirs.id(), employee).andExpect(status().isNoContent());
    }

    @Test
    void anEmployeeMayNotRemoveSomebodyElsesComment() throws Exception {
        Scene scene = scene();
        UUID one = projectMember(scene, "EMPLOYEE");
        UUID two = projectMember(scene, "EMPLOYEE");
        CommentResponse theirs = collaboration.comment(ref(scene), "mine", one);

        remove(scene, theirs.id(), two).andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorMayRemoveAnybodysComment() throws Exception {
        // comment:manage_any widens deletion, and only deletion.
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        CommentResponse theirs = collaboration.comment(ref(scene), "off topic", employee);

        remove(scene, theirs.id(), scene.adminId()).andExpect(status().isNoContent());
    }

    @Test
    void aTeamLeadModeratesTheProjectsTheyLeadAndNoFurther() throws Exception {
        Scene scene = scene();
        UUID lead = member(scene, "TEAM_LEAD");

        // A project belonging to a team this person leads.
        TeamResponse team = taskFixtures.team(scene.workspaceId(), uniqueSlug("team"), lead, scene.adminId());
        ProjectResponse led = taskFixtures.project(
                scene.workspaceId(), uniqueKey(), scene.adminId(), team.id(), scene.adminId());
        UUID employee = member(scene, "EMPLOYEE");
        taskFixtures.addProjectMember(scene.workspaceId(), led.id(), employee, scene.adminId());
        TaskResponse task = taskFixtures.task(scene.workspaceId(), led.id(), "In their project", scene.adminId());

        CommentResponse theirs = collaboration.comment(taskFixtures.ref(task), "a remark", employee);

        // Inside the project they lead: allowed.
        remove(scene, theirs.id(), lead).andExpect(status().isNoContent());

        // Outside it: the scene's own project, which they neither own nor lead.
        UUID onScene = projectMember(scene, "EMPLOYEE");
        CommentResponse elsewhere = collaboration.comment(ref(scene), "a remark elsewhere", onScene);
        remove(scene, elsewhere.id(), lead).andExpect(status().isNotFound());
    }

    @Test
    void aCommentInAnotherWorkspaceIsNotFoundRatherThanForbidden() throws Exception {
        Scene scene = scene();
        Scene other = scene();
        CommentResponse theirs = collaboration.comment(ref(other), "elsewhere", other.adminId());

        remove(scene, theirs.id(), scene.adminId()).andExpect(status().isNotFound());
    }

    @Test
    void anAnonymousCallerReachesNothing() throws Exception {
        Scene scene = scene();

        mockMvc.perform(get(commentsPath(scene))).andExpect(status().isUnauthorized());
    }

    private ResultActions write(Scene scene, UUID actorId, String body) throws Exception {
        return mockMvc.perform(post(commentsPath(scene))
                .header(HttpHeaders.AUTHORIZATION, bearer(actorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("body", body))));
    }

    private ResultActions edit(Scene scene, UUID commentId, UUID actorId, String body) throws Exception {
        return mockMvc.perform(patch(commentPath(scene, commentId))
                .header(HttpHeaders.AUTHORIZATION, bearer(actorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("body", body))));
    }

    private ResultActions remove(Scene scene, UUID commentId, UUID actorId) throws Exception {
        return mockMvc.perform(
                delete(commentPath(scene, commentId)).header(HttpHeaders.AUTHORIZATION, bearer(actorId)));
    }
}
