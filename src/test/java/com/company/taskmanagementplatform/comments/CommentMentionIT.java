package com.company.taskmanagementplatform.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.support.AbstractCollaborationIT;
import com.company.taskmanagementplatform.support.CollaborationFixtures;

/**
 * Naming somebody in a comment.
 *
 * <p>Two rules are under test and both were decisions rather than defaults. The server reads
 * mentions out of the body, so the text and the rows can never disagree. And a mention of somebody
 * who cannot see the task is refused rather than accepted and quietly dropped, because a silent drop
 * tells the writer their message was delivered when nobody will ever be told about it.
 */
class CommentMentionIT extends AbstractCollaborationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aMentionIsReadOutOfTheBodyAndResolvedToAPerson() throws Exception {
        Scene scene = scene();
        UUID colleague = projectMember(scene, "EMPLOYEE");

        write(scene, scene.adminId(), "handing this to " + CollaborationFixtures.mention(colleague) + " for review")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions.length()").value(1))
                .andExpect(jsonPath("$.mentions[0].userId").value(colleague.toString()))
                .andExpect(jsonPath("$.mentions[0].name").value("Test Person"))
                .andExpect(jsonPath("$.mentions[0].email").exists());
    }

    @Test
    void theMentionIsWrittenAsARowForTheNotificationPhaseToFind() {
        Scene scene = scene();
        UUID colleague = projectMember(scene, "EMPLOYEE");

        CommentResponse comment = collaboration.comment(
                ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());

        assertThat(mentionedIn(comment.id())).containsExactly(colleague);
    }

    @Test
    void namingSomebodyTwiceIsOneMention() {
        Scene scene = scene();
        UUID colleague = projectMember(scene, "EMPLOYEE");
        String twice = CollaborationFixtures.mention(colleague) + " and again " + CollaborationFixtures.mention(colleague);

        CommentResponse comment = collaboration.comment(ref(scene), twice, scene.adminId());

        assertThat(mentionedIn(comment.id())).containsExactly(colleague);
    }

    @Test
    void mentioningSomebodyWhoCannotSeeTheTaskIsRefused() throws Exception {
        // In the workspace, but not on the project, so the task is invisible to
        // them and no notification could ever be delivered.
        Scene scene = scene();
        UUID outsider = member(scene, "EMPLOYEE");

        write(scene, scene.adminId(), "what about " + CollaborationFixtures.mention(outsider))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mentioningSomebodyFromAnotherWorkspaceIsRefused() throws Exception {
        Scene scene = scene();
        Scene elsewhere = scene();

        write(scene, scene.adminId(), "hello " + CollaborationFixtures.mention(elsewhere.adminId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mentioningAnAdministratorWhoIsNotOnTheProjectIsAllowed() throws Exception {
        // They hold project:read_any, so they can see the task, so they can be told
        // about it. Reachability is the existing rule rather than a new one.
        Scene scene = scene();
        UUID otherAdmin = member(scene, "ADMIN");

        write(scene, scene.adminId(), "for your eyes " + CollaborationFixtures.mention(otherAdmin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions.length()").value(1));
    }

    @Test
    void aRefusedMentionLeavesNoComment() throws Exception {
        Scene scene = scene();
        UUID outsider = member(scene, "EMPLOYEE");

        write(scene, scene.adminId(), "hi " + CollaborationFixtures.mention(outsider))
                .andExpect(status().isBadRequest());

        Integer comments = jdbc.queryForObject(
                "SELECT count(*) FROM comments WHERE task_id = ?", Integer.class, scene.taskId());
        assertThat(comments).isZero();
    }

    @Test
    void textThatMerelyLooksLikeAMentionIsOrdinaryProse() throws Exception {
        Scene scene = scene();

        write(scene, scene.adminId(), "email @ada about it, and @[user:nobody] too")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions.length()").value(0));
    }

    @Test
    void anEditRewritesTheMentionsToMatchTheNewText() throws Exception {
        Scene scene = scene();
        UUID first = projectMember(scene, "EMPLOYEE");
        UUID second = projectMember(scene, "TEAM_LEAD");

        CommentResponse comment = collaboration.comment(
                ref(scene), "over to " + CollaborationFixtures.mention(first), scene.adminId());

        mockMvc.perform(patch(commentPath(scene, comment.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("body", "actually " + CollaborationFixtures.mention(second)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentions.length()").value(1))
                .andExpect(jsonPath("$.mentions[0].userId").value(second.toString()));

        // The person who is no longer named must not be notified again.
        assertThat(mentionedIn(comment.id())).containsExactly(second);
    }

    @Test
    void anEditThatRemovesEverybodyLeavesNoMentions() throws Exception {
        Scene scene = scene();
        UUID colleague = projectMember(scene, "EMPLOYEE");

        CommentResponse comment = collaboration.comment(
                ref(scene), "over to " + CollaborationFixtures.mention(colleague), scene.adminId());

        mockMvc.perform(patch(commentPath(scene, comment.id()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", "never mind, I will do it"))))
                .andExpect(status().isOk());

        assertThat(mentionedIn(comment.id())).isEmpty();
    }

    @Test
    void removingACommentTakesItsMentionsWithIt() {
        Scene scene = scene();
        UUID colleague = projectMember(scene, "EMPLOYEE");

        CommentResponse comment = collaboration.comment(
                ref(scene), "hello " + CollaborationFixtures.mention(colleague), scene.adminId());

        deleteComment(scene, comment.id());

        assertThat(mentionedIn(comment.id())).isEmpty();
    }

    private void deleteComment(Scene scene, UUID commentId) {
        try {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete(commentPath(scene, commentId))
                            .header(HttpHeaders.AUTHORIZATION, bearer(scene.adminId())))
                    .andExpect(status().isNoContent());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private java.util.List<UUID> mentionedIn(UUID commentId) {
        return jdbc.queryForList(
                "SELECT mentioned_user_id FROM comment_mentions WHERE comment_id = ?", UUID.class, commentId);
    }

    private ResultActions write(Scene scene, UUID actorId, String body) throws Exception {
        return mockMvc.perform(post(commentsPath(scene))
                .header(HttpHeaders.AUTHORIZATION, bearer(actorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("body", body))));
    }
}
