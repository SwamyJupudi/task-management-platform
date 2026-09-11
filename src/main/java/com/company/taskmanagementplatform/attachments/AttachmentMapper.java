package com.company.taskmanagementplatform.attachments;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;

/**
 * Turns attachments into response bodies, resolving the uploader.
 *
 * <p>The batch method resolves everybody in one query, for the reason every other mapper in the
 * platform does: a lookup per row is how a list endpoint becomes N+1 without anybody noticing.
 */
@Component
class AttachmentMapper {

    private final UserAccountService users;

    AttachmentMapper(UserAccountService users) {
        this.users = users;
    }

    AttachmentResponse toResponse(Attachment attachment) {
        return toResponses(List.of(attachment)).get(0);
    }

    List<AttachmentResponse> toResponses(List<Attachment> attachments) {
        if (attachments.isEmpty()) {
            return List.of();
        }

        List<UUID> uploaderIds = attachments.stream()
                .map(Attachment::getUploaderUserId)
                .distinct()
                .toList();
        Map<UUID, UserAccount> uploaders = users.findAllByIds(uploaderIds);

        return attachments.stream()
                .map(attachment -> build(attachment, uploaders.get(attachment.getUploaderUserId())))
                .toList();
    }

    private static AttachmentResponse build(Attachment attachment, UserAccount uploader) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getWorkspaceId(),
                attachment.getProjectId(),
                attachment.getTaskId(),
                attachment.getCommentId(),
                attachment.getUploaderUserId(),
                uploader == null ? null : uploader.email(),
                uploader == null ? null : uploader.fullName(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getChecksumSha256(),
                attachment.getCreatedAt());
    }
}
