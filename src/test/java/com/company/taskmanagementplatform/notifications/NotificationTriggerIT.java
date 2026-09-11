package com.company.taskmanagementplatform.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.support.CollaborationFixtures;
import com.company.taskmanagementplatform.tasks.dto.CreateTaskRequest;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;

/**
 * That each of the triggers the requirements name reaches the right people, and nobody else.
 *
 * <p>This is the test that closes the second loop opened in phase three. Five of these six events
 * have been published since the modules that publish them were built, with a comment saying phase
 * seven would listen; not one line changed in any of them to make these rows appear.
 */
class NotificationTriggerIT extends NotificationTestBase {

    @Test
    void assigningATaskTellsTheNewAssignee() throws Exception {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");

        assign(scene, scene.taskId(), employee, scene.adminId());

        eventually(() -> assertThat(typesFor(employee)).contains("task.assigned"));
    }

    @Test
    void assigningTellsNobodyElse() throws Exception {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        UUID bystander = projectMember(scene, "EMPLOYEE");

        assign(scene, scene.taskId(), employee, scene.adminId());

        eventually(() -> assertThat(typesFor(employee)).contains("task.assigned"));
        assertThat(typesFor(bystander)).doesNotContain("task.assigned");
    }

    @Test
    void assigningSomethingToYourselfTellsYouNothing() throws Exception {
        // Rule one. Being told what you just did is how a feed becomes noise.
        Scene scene = scene();

        // An assignee has to be on the project, and owning it is not the same as
        // being on it, so the admin joins their own project before taking the task.
        taskFixtures.addProjectMember(scene.workspaceId(), scene.projectId(), scene.adminId(), scene.adminId());
        assign(scene, scene.taskId(), scene.adminId(), scene.adminId());

        eventually(() -> assertThat(typesFor(scene.adminId())).isNotNull());
        assertThat(typesFor(scene.adminId())).doesNotContain("task.assigned");
    }

    @Test
    void aStatusChangeTellsTheAssigneeAndTheReporter() throws Exception {
        Scene scene = scene();
        UUID assignee = projectMember(scene, "EMPLOYEE");
        assign(scene, scene.taskId(), assignee, scene.adminId());

        // The admin raised the task in the scene, so they are its reporter. A third
        // person moves it, so both of the others should hear.
        UUID lead = projectMember(scene, "TEAM_LEAD");
        taskFixtures.moveTask(scene.workspaceId(), scene.taskId(), "IN_PROGRESS", lead);

        eventually(() -> {
            assertThat(typesFor(assignee)).contains("task.status_changed");
            assertThat(typesFor(scene.adminId())).contains("task.status_changed");
        });
        assertThat(typesFor(lead)).doesNotContain("task.status_changed");
    }

    @Test
    void beingBothAssigneeAndReporterEarnsOneNotification() throws Exception {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");

        // Assigned to them and raised in their name, so both halves of the rule
        // point at one person, then moved by somebody else.
        TaskResponse task = taskFixtures.task(
                scene.workspaceId(),
                scene.projectId(),
                new CreateTaskRequest(
                        "Theirs alone", null, employee, employee, null, null, null, null, null, null),
                scene.adminId());
        taskFixtures.moveTask(scene.workspaceId(), task.id(), "IN_PROGRESS", scene.adminId());

        eventually(() -> assertThat(countFor(employee, "task.status_changed")).isEqualTo(1));
    }

    @Test
    void aCommentTellsTheTasksPeople() throws Exception {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");

        collaboration.comment(ref(scene), "worth saying", employee);

        // The admin is the task's reporter; the employee wrote it, so hears nothing.
        eventually(() -> assertThat(typesFor(scene.adminId())).contains("comment.created"));
        assertThat(typesFor(employee)).doesNotContain("comment.created");
    }

    @Test
    void aMentionTellsThePersonNamed() throws Exception {
        Scene scene = scene();
        UUID colleague = projectMember(scene, "EMPLOYEE");

        collaboration.comment(ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());

        eventually(() -> assertThat(typesFor(colleague)).contains("comment.mentioned"));
    }

    @Test
    void somebodyMentionedDoesNotAlsoHearAboutTheComment() throws Exception {
        // Rule three, end to end: the mention and the comment are the same fact.
        Scene scene = scene();
        UUID assignee = projectMember(scene, "EMPLOYEE");
        TaskResponse task =
                taskFixtures.task(scene.workspaceId(), scene.projectId(), "Mention target", assignee, scene.adminId());

        collaboration.comment(
                taskFixtures.ref(task), "over to " + CollaborationFixtures.mention(assignee), scene.adminId());

        eventually(() -> assertThat(typesFor(assignee)).contains("comment.mentioned"));
        assertThat(countFor(assignee, "comment.created")).isZero();
    }

    @Test
    void beingAddedToAProjectTellsTheNewMember() throws Exception {
        Scene scene = scene();
        UUID newcomer = member(scene, "EMPLOYEE");

        taskFixtures.addProjectMember(scene.workspaceId(), scene.projectId(), newcomer, scene.adminId());

        eventually(() -> assertThat(typesFor(newcomer)).contains("project.member_added"));
    }

    @Test
    void aProjectStatusChangeTellsEverybodyOnIt() throws Exception {
        Scene scene = scene();
        UUID first = projectMember(scene, "EMPLOYEE");
        UUID second = projectMember(scene, "EMPLOYEE");

        changeProjectStatus(scene, "ACTIVE", scene.adminId());

        eventually(() -> {
            assertThat(typesFor(first)).contains("project.status_changed");
            assertThat(typesFor(second)).contains("project.status_changed");
        });
        // The admin owns the project and made the change, so rule one still applies.
        assertThat(typesFor(scene.adminId())).doesNotContain("project.status_changed");
    }

    @Test
    void aStatusChangeCarriesBothStatuses() throws Exception {
        Scene scene = scene();
        UUID assignee = projectMember(scene, "EMPLOYEE");
        assign(scene, scene.taskId(), assignee, scene.adminId());

        taskFixtures.moveTask(scene.workspaceId(), scene.taskId(), "IN_PROGRESS", scene.adminId());

        eventually(() -> assertThat(String.valueOf(latestFor(assignee, "task.status_changed").get("metadata")))
                .contains("TODO")
                .contains("IN_PROGRESS"));
    }

    @Test
    void everyEventDrivenRowNamesItsActorAndItsProject() throws Exception {
        Scene scene = scene();
        UUID colleague = projectMember(scene, "EMPLOYEE");

        CommentResponse comment =
                collaboration.comment(ref(scene), "hello " + CollaborationFixtures.mention(colleague), scene.adminId());

        eventually(() -> {
            var row = latestFor(colleague, "comment.mentioned");
            assertThat(row.get("actor_user_id")).isEqualTo(scene.adminId());
            assertThat(row.get("project_id")).isEqualTo(scene.projectId());
            assertThat(row.get("entity_id")).isEqualTo(comment.id());
            assertThat(row.get("entity_type")).isEqualTo("COMMENT");
        });
    }

    @Test
    void nothingIsWrittenForTheTriggersTheRequirementsDoNotName() throws Exception {
        // A feed that announced everything would be one nobody reads. Creating a
        // task, editing it and attaching a file are all silent.
        Scene scene = scene();
        UUID assignee = projectMember(scene, "EMPLOYEE");
        TaskResponse task = taskFixtures.task(
                scene.workspaceId(), scene.projectId(), "Quietly created", assignee, scene.adminId());

        collaboration.attachment(
                taskFixtures.ref(task), "spec.pdf", CollaborationFixtures.PDF, scene.adminId());
        taskFixtures.subtask(taskFixtures.ref(task), "a step", scene.adminId());

        eventually(() -> assertThat(typesFor(assignee)).isNotNull());
        assertThat(typesFor(assignee))
                .doesNotContain("task.created", "task.updated", "attachment.uploaded", "subtask.created");
    }
}
