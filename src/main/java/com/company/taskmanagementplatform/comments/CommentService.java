package com.company.taskmanagementplatform.comments;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.attachments.AttachmentAdoptionFacade;
import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.comments.dto.CreateCommentRequest;
import com.company.taskmanagementplatform.comments.dto.UpdateCommentRequest;
import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.PermissionResolver;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.common.web.PageResponse;
import com.company.taskmanagementplatform.projects.ProjectAccessFacade;
import com.company.taskmanagementplatform.projects.ProjectContext;
import com.company.taskmanagementplatform.tasks.TaskAccessGuard;
import com.company.taskmanagementplatform.tasks.TaskContribution;
import com.company.taskmanagementplatform.tasks.TaskRef;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

/**
 * The discussion on a task: writing on it, editing your own words, and removing them.
 *
 * <p>Creating and listing take a {@link TaskRef}, because the controller has the task in the path and
 * has already had it authorized, exactly as subtasks work. Editing and deleting do not: a comment is
 * addressed flat, by its own identifier, so which task it belongs to is not known until it has been
 * loaded. Those two methods therefore authorize <em>here</em>, after the lookup, rather than in the
 * controller. That is the one place this module departs from the controller-authorizes convention,
 * and it is because the alternative is a lookup, then a guard, then the same lookup again.
 *
 * <p>Two rules are worth reading twice.
 *
 * <p><strong>Editing is author-only.</strong> {@code comment:manage_any} widens deletion and nothing
 * else. An administrator may remove somebody's words; nobody may rewrite them and leave them
 * attributed to the person who wrote them.
 *
 * <p><strong>You may only mention somebody who can already see the task.</strong> A mention of
 * anybody else is refused rather than accepted and dropped: accepting it would tell the writer their
 * message was delivered when no notification will ever be sent, and a silent drop is the kind of
 * failure nobody reports.
 */
@Service
public class CommentService {

    private final CommentRepository comments;
    private final CommentMentionRepository mentions;
    private final CommentMapper mapper;
    private final TaskAccessGuard tasks;
    private final ProjectAccessFacade projects;
    private final PermissionResolver permissions;
    private final UserAccountService users;
    private final AttachmentAdoptionFacade attachments;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    CommentService(
            CommentRepository comments,
            CommentMentionRepository mentions,
            CommentMapper mapper,
            TaskAccessGuard tasks,
            ProjectAccessFacade projects,
            PermissionResolver permissions,
            UserAccountService users,
            AttachmentAdoptionFacade attachments,
            ApplicationEventPublisher events,
            Clock clock) {
        this.comments = comments;
        this.mentions = mentions;
        this.mapper = mapper;
        this.tasks = tasks;
        this.projects = projects;
        this.permissions = permissions;
        this.users = users;
        this.attachments = attachments;
        this.events = events;
        this.clock = clock;
    }

    /**
     * One task's thread, oldest first.
     *
     * <p>The order is fixed rather than taken from the request. A discussion has one meaningful
     * order, the one it happened in, and a sort parameter here would be an allowlist of one field
     * pretending to be a choice.
     */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(TaskRef task, Pageable pageable) {
        Pageable inOrder = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));

        Page<Comment> page = comments.findAllByTaskIdAndDeletedAtIsNull(task.taskId(), inOrder);
        List<CommentResponse> mapped = mapper.toResponses(page.getContent());

        return new PageResponse<>(
                mapped,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional
    public CommentResponse create(TaskRef task, CreateCommentRequest request, UUID actorUserId) {
        String body = CommentBody.require(request.body());

        Comment comment = Comment.create(
                task.workspaceId(), task.projectId(), task.taskId(), actorUserId, body);
        comments.save(comment);
        comments.flush();

        List<UUID> mentioned = rewriteMentions(comment, body);

        if (request.attachmentIds() != null && !request.attachmentIds().isEmpty()) {
            attachments.adoptForComment(task, comment.getId(), request.attachmentIds(), actorUserId);
        }

        events.publishEvent(new CommentEvents.CommentCreated(
                task.workspaceId(), task.projectId(), task.taskId(), comment.getId(), mentioned, actorUserId));
        mentioned.forEach(userId -> events.publishEvent(new CommentEvents.UserMentioned(
                task.workspaceId(), task.projectId(), task.taskId(), comment.getId(), userId, actorUserId)));

        return mapper.toResponse(comment);
    }

    /**
     * Rewrites the words of one comment.
     *
     * @throws AccessDeniedException if the caller did not write it, whatever else they hold
     */
    @Transactional
    public CommentResponse update(
            UUID workspaceId, UUID commentId, UpdateCommentRequest request, UUID actorUserId) {

        Comment comment = require(workspaceId, commentId);
        tasks.requireContributableTask(workspaceId, comment.getTaskId(), Permissions.COMMENT_UPDATE);

        if (!comment.isWrittenBy(actorUserId)) {
            // Deliberately not widened by comment:manage_any. Removing somebody's
            // words is an administrative act; rewriting them and leaving their name
            // on the result is not one anybody should be able to perform.
            throw new AccessDeniedException("Only the person who wrote a comment may edit it");
        }

        String body = CommentBody.require(request.body());
        Set<UUID> previouslyMentioned = new LinkedHashSet<>(mentionedIds(commentId));

        comment.editBody(body, clock.instant());
        List<UUID> mentioned = rewriteMentions(comment, body);

        List<UUID> newlyMentioned =
                mentioned.stream().filter(userId -> !previouslyMentioned.contains(userId)).toList();

        events.publishEvent(new CommentEvents.CommentUpdated(
                comment.getWorkspaceId(),
                comment.getProjectId(),
                comment.getTaskId(),
                commentId,
                newlyMentioned,
                actorUserId));
        newlyMentioned.forEach(userId -> events.publishEvent(new CommentEvents.UserMentioned(
                comment.getWorkspaceId(),
                comment.getProjectId(),
                comment.getTaskId(),
                commentId,
                userId,
                actorUserId)));

        return mapper.toResponse(comment);
    }

    /**
     * Removes a comment, soft, taking its files with it.
     *
     * <p>The author may remove their own. Anybody else needs {@code comment:manage_any}, or to own
     * the task's project or lead its team, which is the same write scope a task carries one level up
     * and is what lets a lead moderate the projects they run without reaching the whole workspace.
     */
    @Transactional
    public void delete(UUID workspaceId, UUID commentId, UUID actorUserId) {
        Comment comment = require(workspaceId, commentId);
        TaskContribution contribution = tasks.requireContribution(
                workspaceId, comment.getTaskId(), Permissions.COMMENT_DELETE, Permissions.COMMENT_MANAGE_ANY);

        if (!comment.isWrittenBy(actorUserId) && !contribution.moderator()) {
            throw new AccessDeniedException(
                    "Only the author, the project owner or its team lead may remove this comment");
        }

        comment.softDelete(clock.instant());

        // The mention rows go now rather than at the purge: nothing should be
        // notified about a comment that is no longer there to read.
        mentions.deleteAllByIdCommentId(commentId);

        events.publishEvent(new CommentEvents.CommentDeleted(
                comment.getWorkspaceId(), comment.getProjectId(), comment.getTaskId(), commentId, actorUserId));
    }

    // --- mentions ---------------------------------------------------------

    /**
     * Replaces a comment's mention rows with what its body now says.
     *
     * <p>Wholesale rather than a difference, because the body is the record and the rows are derived
     * from it. Computing a delta would be a second implementation of the same fact.
     */
    private List<UUID> rewriteMentions(Comment comment, String body) {
        List<UUID> mentioned = List.copyOf(MentionParser.parse(body));
        requireEveryoneCanSeeTheTask(comment, mentioned);

        mentions.deleteAllByIdCommentId(comment.getId());
        mentions.flush();

        mentioned.forEach(userId -> mentions.save(
                new CommentMention(comment.getId(), userId, comment.getWorkspaceId())));
        mentions.flush();

        return mentioned;
    }

    private List<UUID> mentionedIds(UUID commentId) {
        return mentions.findAllByIdCommentId(commentId).stream()
                .map(CommentMention::getMentionedUserId)
                .toList();
    }

    /**
     * Refuses a mention of anybody who could not open the task the comment is on.
     *
     * <p>Reachability is the existing rule rather than a new one: a member of the task's project, its
     * owner, the lead of its team, or somebody holding {@code project:read_any}. Nothing here widens
     * what a person can see; it only refuses to name somebody who cannot see it.
     *
     * <p>One resolution per person named. A comment names a handful of people, so this is a handful
     * of queries and not a page of them.
     */
    private void requireEveryoneCanSeeTheTask(Comment comment, List<UUID> mentioned) {
        if (mentioned.isEmpty()) {
            return;
        }

        Map<UUID, UserAccount> found = users.findAllByIds(mentioned);
        List<String> unreachable = new ArrayList<>();

        for (UUID userId : mentioned) {
            UserAccount person = found.get(userId);
            if (person == null || !canSeeTask(comment, userId)) {
                unreachable.add(person == null ? userId.toString() : person.email());
            }
        }

        if (!unreachable.isEmpty()) {
            throw new BadRequestException(
                    "You can only mention people who can see this task. These cannot: "
                            + String.join(", ", unreachable));
        }
    }

    private boolean canSeeTask(Comment comment, UUID userId) {
        boolean unrestricted = permissions
                .resolveForWorkspace(userId, comment.getWorkspaceId())
                .contains(Permissions.PROJECT_READ_ANY);

        return projects.contextOf(comment.getWorkspaceId(), comment.getProjectId(), userId, unrestricted)
                .map(ProjectContext::readable)
                .orElse(false);
    }

    private Comment require(UUID workspaceId, UUID commentId) {
        return comments.findByIdAndWorkspaceIdAndDeletedAtIsNull(commentId, workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Comment", commentId));
    }
}
