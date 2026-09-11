package com.company.taskmanagementplatform.attachments;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.tasks.TaskRef;

/**
 * The one thing another module may ask of this one.
 *
 * <p>{@code comments} calls it to claim files that were uploaded a moment earlier, which is the
 * second half of the two-step attachment flow: upload to the task, then name the identifiers when
 * writing the comment. Mixing a file part and a JSON part in one request is awkward for every client
 * and makes a partial failure ambiguous, so the two steps stay two.
 *
 * <p>The dependency runs the other way as well, and deliberately in a different form. {@code
 * comments} imports this class and calls it; {@code attachments} imports only {@code CommentEvents}
 * and listens. A call is right for adoption because it has to validate and to fail the request when
 * it cannot; an event is right for the cascade because nothing needs an answer.
 *
 * <p>It takes and returns values, never entities, like every other facade in the platform.
 */
@Component
public class AttachmentAdoptionFacade {

    private final AttachmentRepository attachments;

    AttachmentAdoptionFacade(AttachmentRepository attachments) {
        this.attachments = attachments;
    }

    /**
     * Attaches already-uploaded files to a comment that has just been written.
     *
     * <p>Four things are checked, and each of them is a way the operation could otherwise be used to
     * reach something: the file must exist, it must be on this very task, it must have been uploaded
     * by the person writing the comment, and it must not already belong to a comment. The third is
     * what stops somebody adopting a colleague's file into their own words; the fourth is what stops
     * a file being moved out from under an existing comment.
     *
     * @throws BadRequestException naming what was wrong, because every case here is something the
     *     caller can correct
     */
    @Transactional
    public void adoptForComment(TaskRef task, UUID commentId, List<UUID> attachmentIds, UUID uploaderUserId) {
        List<UUID> wanted = attachmentIds.stream().distinct().toList();
        if (wanted.isEmpty()) {
            return;
        }

        Map<UUID, Attachment> found = attachments
                .findAllByIdInAndTaskIdAndDeletedAtIsNull(wanted, task.taskId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Attachment::getId, Function.identity()));

        for (UUID attachmentId : wanted) {
            Attachment attachment = found.get(attachmentId);

            if (attachment == null) {
                throw new BadRequestException("No such file on this task: " + attachmentId);
            }
            if (!attachment.isUploadedBy(uploaderUserId)) {
                throw new BadRequestException("You can only attach files you uploaded yourself.");
            }
            if (attachment.belongsToAComment()) {
                throw new BadRequestException("That file already belongs to another comment.");
            }

            attachment.attachTo(commentId);
        }

        attachments.flush();
    }
}
