package com.company.taskmanagementplatform.comments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

/**
 * Turns comments into response bodies, resolving the author and everybody they named.
 *
 * <p>The batch method takes the whole page, collects every person it will need across every row, and
 * resolves them in one query. A lookup per row is exactly the shape that turns a list endpoint into
 * N+1 without anybody noticing until it is in production, and a thread with mentions would do it
 * twice over.
 */
@Component
class CommentMapper {

    private final CommentMentionRepository mentions;
    private final UserAccountService users;

    CommentMapper(CommentMentionRepository mentions, UserAccountService users) {
        this.mentions = mentions;
        this.users = users;
    }

    CommentResponse toResponse(Comment comment) {
        return toResponses(List.of(comment)).get(0);
    }

    List<CommentResponse> toResponses(List<Comment> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<UUID>> mentionedByComment = mentionsOf(comments);

        Set<UUID> people = new LinkedHashSet<>();
        comments.forEach(comment -> people.add(comment.getAuthorUserId()));
        mentionedByComment.values().forEach(people::addAll);

        Map<UUID, UserAccount> resolved = users.findAllByIds(people);

        return comments.stream()
                .map(comment -> build(
                        comment,
                        resolved.get(comment.getAuthorUserId()),
                        mentionedByComment.getOrDefault(comment.getId(), List.of()),
                        resolved))
                .toList();
    }

    /** One query for the whole page's mentions, keyed back to the comment that carries them. */
    private Map<UUID, List<UUID>> mentionsOf(List<Comment> comments) {
        List<UUID> commentIds = comments.stream().map(Comment::getId).toList();

        Map<UUID, List<UUID>> byComment = new LinkedHashMap<>();
        for (CommentMention mention : mentions.findAllByIdCommentIdIn(commentIds)) {
            byComment.computeIfAbsent(mention.getCommentId(), key -> new ArrayList<>())
                    .add(mention.getMentionedUserId());
        }
        return byComment;
    }

    private static CommentResponse build(
            Comment comment, UserAccount author, List<UUID> mentionedIds, Map<UUID, UserAccount> resolved) {

        List<CommentResponse.MentionResponse> mentions = mentionedIds.stream()
                .map(userId -> {
                    UserAccount person = resolved.get(userId);
                    return new CommentResponse.MentionResponse(
                            userId,
                            person == null ? null : person.email(),
                            person == null ? null : person.fullName());
                })
                .toList();

        return new CommentResponse(
                comment.getId(),
                comment.getWorkspaceId(),
                comment.getProjectId(),
                comment.getTaskId(),
                comment.getAuthorUserId(),
                author == null ? null : author.email(),
                author == null ? null : author.fullName(),
                comment.getBody(),
                comment.getEditedAt() != null,
                comment.getEditedAt(),
                mentions,
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
