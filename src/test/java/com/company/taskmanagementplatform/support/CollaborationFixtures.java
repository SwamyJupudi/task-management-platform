package com.company.taskmanagementplatform.support;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.attachments.AttachmentService;
import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.comments.CommentService;
import com.company.taskmanagementplatform.comments.dto.CommentResponse;
import com.company.taskmanagementplatform.comments.dto.CreateCommentRequest;
import com.company.taskmanagementplatform.tasks.TaskRef;

/**
 * Builds the comments and attachments a test needs, through the same services the application uses.
 *
 * <p>Through the services rather than by inserting rows, for the reason {@link IdentityFixtures}
 * gives. The schema test is the deliberate exception and writes its own SQL, because the constraints
 * are what it is about.
 *
 * <p>None of these need a security context. Every service here takes the acting person as an
 * argument, which is what lets a fixture drive them directly; authorization is exercised through
 * MockMvc by the tests that are about authorization.
 */
@TestComponent
public class CollaborationFixtures {

    /** The first bytes of a real PNG, which is what the sniffer looks at and all it looks at. */
    public static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'
    };

    public static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes();

    public static final byte[] TEXT = "the quick brown fox\nsecond line\n".getBytes();

    private final CommentService comments;
    private final AttachmentService attachments;

    CollaborationFixtures(CommentService comments, AttachmentService attachments) {
        this.comments = comments;
        this.attachments = attachments;
    }

    @Transactional
    public CommentResponse comment(TaskRef task, String body, UUID actorUserId) {
        return comments.create(task, new CreateCommentRequest(body, null), actorUserId);
    }

    @Transactional
    public CommentResponse comment(TaskRef task, String body, List<UUID> attachmentIds, UUID actorUserId) {
        return comments.create(task, new CreateCommentRequest(body, attachmentIds), actorUserId);
    }

    @Transactional
    public AttachmentResponse attachment(TaskRef task, String filename, byte[] content, UUID actorUserId) {
        return attachments.upload(
                task,
                new MockMultipartFile("file", filename, "application/octet-stream", content),
                actorUserId);
    }

    /** The canonical form a mention takes in a body, so a test does not hand-assemble it. */
    public static String mention(UUID userId) {
        return "@[user:" + userId + "]";
    }
}
