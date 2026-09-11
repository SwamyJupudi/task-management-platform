package com.company.taskmanagementplatform.comments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.projects.ProjectService;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;
import com.company.taskmanagementplatform.tasks.dto.TaskResponse;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.MembershipService;

/**
 * What happens to a discussion when the thing it hangs off goes away, and what deliberately does
 * not happen when a person does.
 *
 * <p>The second half is the interesting one. Every module built before this one has to stand people
 * down when they leave a workspace, because its people columns are foreign keys into a membership
 * table. A comment's author is keyed to {@code users} instead, so a comment survives its author
 * leaving, which is what anybody reading an old thread would expect and the reason the column was
 * keyed that way.
 */
class CommentCascadeIT extends AbstractCollaborationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProjectService projects;

    @Autowired
    private UserAccountService users;

    @Autowired
    private MembershipService memberships;

    @Test
    void removingATaskRemovesItsThread() {
        Scene scene = scene();
        CommentResponse comment = collaboration.comment(ref(scene), "still relevant", scene.adminId());

        taskFixtures.deleteTask(scene.workspaceId(), scene.taskId(), scene.adminId());

        assertThat(isLive(comment.id())).isFalse();
    }

    @Test
    void removingATaskTakesItsMentionsToo() {
        Scene scene = scene();
        UUID colleague = projectMember(scene, "EMPLOYEE");
        CommentResponse comment = collaboration.comment(
                ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());

        taskFixtures.deleteTask(scene.workspaceId(), scene.taskId(), scene.adminId());

        assertThat(mentionCount(comment.id())).isZero();
    }

    @Test
    void removingAProjectRemovesEveryThreadInIt() {
        Scene scene = scene();
        TaskResponse second =
                taskFixtures.task(scene.workspaceId(), scene.projectId(), "Another task", scene.adminId());

        CommentResponse first = collaboration.comment(ref(scene), "on the first", scene.adminId());
        CommentResponse other = collaboration.comment(taskFixtures.ref(second), "on the second", scene.adminId());

        projects.delete(scene.workspaceId(), scene.projectId(), scene.adminId());

        assertThat(isLive(first.id())).isFalse();
        assertThat(isLive(other.id())).isFalse();
    }

    @Test
    void aCommentOutlivesItsAuthorLeavingTheWorkspace() {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        CommentResponse theirs = collaboration.comment(ref(scene), "worth keeping", employee);

        // The task modules stand their assignee down for this to be possible at
        // all; this module has nothing to stand down and must not remove the words.
        memberships.removeMember(scene.workspaceId(), employee);

        assertThat(isLive(theirs.id())).isTrue();
        assertThat(authorOf(theirs.id())).isEqualTo(employee);
    }

    @Test
    void aCommentOutlivesItsAuthorsAccountBeingRemoved() {
        Scene scene = scene();
        UUID employee = projectMember(scene, "EMPLOYEE");
        CommentResponse theirs = collaboration.comment(ref(scene), "worth keeping", employee);

        users.softDelete(employee);

        assertThat(isLive(theirs.id())).isTrue();
        assertThat(authorOf(theirs.id())).isEqualTo(employee);
    }

    @Test
    void aMentionOfSomebodyWhoLaterLeavesIsLeftAlone() {
        Scene scene = scene();
        UUID colleague = projectMember(scene, "EMPLOYEE");
        CommentResponse comment = collaboration.comment(
                ref(scene), "asking " + CollaborationFixtures.mention(colleague), scene.adminId());

        memberships.removeMember(scene.workspaceId(), colleague);

        assertThat(mentionCount(comment.id())).isEqualTo(1);
    }

    private boolean isLive(UUID commentId) {
        Integer live = jdbc.queryForObject(
                "SELECT count(*) FROM comments WHERE id = ? AND deleted_at IS NULL", Integer.class, commentId);
        return live != null && live == 1;
    }

    private int mentionCount(UUID commentId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM comment_mentions WHERE comment_id = ?", Integer.class, commentId);
        return count == null ? 0 : count;
    }

    private UUID authorOf(UUID commentId) {
        return jdbc.queryForObject("SELECT author_user_id FROM comments WHERE id = ?", UUID.class, commentId);
    }
}
