package com.company.taskmanagementplatform.attachments;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.company.taskmanagementplatform.attachments.dto.AttachmentResponse;
import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ConflictException;
import com.company.taskmanagementplatform.common.error.PayloadTooLargeException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.error.ServiceUnavailableException;
import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.tasks.TaskAccessGuard;
import com.company.taskmanagementplatform.tasks.TaskContribution;
import com.company.taskmanagementplatform.tasks.TaskRef;

/**
 * Files held against a task: putting them there, handing them back, and removing them.
 *
 * <p>Uploading takes a {@link TaskRef} the controller has had authorized. Reading, downloading and
 * deleting take an attachment identifier and authorize <em>here</em>, after the lookup, because
 * which task a file belongs to is not known until it has been loaded. {@code CommentService}
 * departs from the controller-authorizes convention for the same reason and says so at more length.
 *
 * <p>The upload is validated in five steps and the order is deliberate: size, then what the bytes
 * actually are, then whether that is a type this platform accepts, then the malware scan, then the
 * name. Nothing the client said about the file is consulted at any step except to phrase the error.
 * The scan comes last of the content checks because it is the only one that leaves the process, so
 * every cheap local refusal happens first and a file this platform would never accept is never sent
 * anywhere.
 *
 * <p><strong>The whole file is read into memory.</strong> That is safe only because it is bounded:
 * the servlet container refuses anything over its own limit before a body is read, and {@code
 * app.storage.max-file-size} is checked here. Detecting a content type and computing a checksum both
 * need the bytes anyway, and streaming twice to avoid holding a few megabytes would buy nothing.
 */
@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachments;
    private final AttachmentMapper mapper;
    private final TaskAccessGuard tasks;
    private final FileStore store;
    private final MalwareScanner scanner;
    private final StorageProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AttachmentService(
            AttachmentRepository attachments,
            AttachmentMapper mapper,
            TaskAccessGuard tasks,
            FileStore store,
            MalwareScanner scanner,
            StorageProperties properties,
            ApplicationEventPublisher events,
            Clock clock) {
        this.attachments = attachments;
        this.mapper = mapper;
        this.tasks = tasks;
        this.store = store;
        this.scanner = scanner;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(TaskRef task) {
        return mapper.toResponses(attachments.findAllByTaskIdAndDeletedAtIsNullOrderByCreatedAtAsc(task.taskId()));
    }

    /**
     * Stores one file against a task.
     *
     * <p>The bytes are written to the store before the row is written, and that order matters: a row
     * pointing at an object that does not exist is a broken download nobody can explain, whereas an
     * object with no row is invisible and is what the purge is for.
     */
    @Transactional
    public AttachmentResponse upload(TaskRef task, MultipartFile file, UUID actorUserId) {
        byte[] content = read(file);

        if (content.length == 0) {
            throw new BadRequestException("That file is empty.");
        }
        long limit = properties.maxFileSize().toBytes();
        if (content.length > limit) {
            throw new PayloadTooLargeException(
                    "That file is larger than the " + properties.maxFileSize().toMegabytes() + " MB limit.");
        }

        String contentType = ContentTypes.detect(content)
                .orElseThrow(() -> new BadRequestException(
                        "That kind of file cannot be attached. Images, PDFs, text, archives "
                                + "and Office documents are accepted."));

        java.time.Instant scannedAt = clock.instant();
        scan(content, task, actorUserId);

        String filename = Filenames.sanitise(file.getOriginalFilename());
        String key = storageKey(task);

        store.put(key, new ByteArrayInputStream(content), contentType, content.length);

        Attachment attachment = Attachment.createScanned(
                task.workspaceId(),
                task.projectId(),
                task.taskId(),
                actorUserId,
                filename,
                contentType,
                content.length,
                sha256(content),
                store.provider(),
                key,
                scannedAt);

        attachments.save(attachment);
        attachments.flush();

        events.publishEvent(new AttachmentEvents.AttachmentUploaded(
                task.workspaceId(),
                task.projectId(),
                task.taskId(),
                attachment.getId(),
                filename,
                attachment.getSizeBytes(),
                actorUserId));

        return mapper.toResponse(attachment);
    }

    /**
     * Refuses the upload unless the scanner says the bytes are clean.
     *
     * <p><strong>Before anything is stored, which is the whole design of this step.</strong> Scanning after
     * the object is written would mean an infected file existed in the store, however briefly, and would
     * leave the cleanup of it as a second thing to get right; scanning after the row is written would mean it
     * was momentarily downloadable. Scanning here means an infected upload leaves nothing behind at all — no
     * object, no row, nothing for a purge to find — and the {@code PENDING} state the schema defines is never
     * reached by this path.
     *
     * <p>Three outcomes, three answers, and the third is the one that matters:
     *
     * <ul>
     *   <li><strong>Clean</strong> — the upload proceeds.
     *   <li><strong>Infected</strong> — 400, with a message that says the file was refused and nothing about
     *       what was found. The signature goes to the log, where somebody investigating can see it; telling
     *       the uploader would hand an attacker a free oracle for tuning a payload against the engine.
     *   <li><strong>The scanner could not say</strong> — 503, and the upload is refused. This is the
     *       fail-closed half, and accepting the file instead would be the one decision that makes the whole
     *       feature pointless: an attacker who could make the scanner unreachable would then be able to
     *       upload anything. A 503 tells the caller to try again, which is true, rather than 500's "this is
     *       broken".
     * </ul>
     *
     * <p>Nothing here logs the file. Not the content, not the filename, not the checksum. A signature name is
     * the engine's own vocabulary and describes what was found rather than what was in the file.
     */
    private void scan(byte[] content, TaskRef task, UUID actorUserId) {
        ScanVerdict verdict = scanner.scan(content);

        if (verdict.isClean()) {
            return;
        }

        if (verdict.isInfected()) {
            log.warn(
                    "Refused an infected upload: signature={} scanner={} workspaceId={} taskId={} uploaderUserId={}",
                    verdict.detail(),
                    scanner.provider(),
                    task.workspaceId(),
                    task.taskId(),
                    actorUserId);
            throw new BadRequestException("That file was refused because it did not pass a malware check.");
        }

        log.error(
                "Could not scan an upload, so it was refused: reason={} scanner={} workspaceId={} taskId={}",
                verdict.detail(),
                scanner.provider(),
                task.workspaceId(),
                task.taskId());
        throw new ServiceUnavailableException(
                "That file could not be checked for malware just now. Please try again in a moment.");
    }

    @Transactional(readOnly = true)
    public AttachmentResponse metadata(UUID workspaceId, UUID attachmentId) {
        return mapper.toResponse(requireReadable(workspaceId, attachmentId));
    }

    /**
     * The bytes of one file, for somebody who may see the task holding it.
     *
     * <p>Reading a file needs nothing but {@code task:read}. There is deliberately no {@code
     * attachment:read}: a file is visible exactly when its task is, which is the same rule comments
     * follow and the same reasoning that left tasks without a read grant of their own.
     */
    @Transactional(readOnly = true)
    public AttachmentContent download(UUID workspaceId, UUID attachmentId) {
        Attachment attachment = requireReadable(workspaceId, attachmentId);

        // The last gate before bytes leave the platform, and the only place it can
        // be. Both download routes pass through here, the streamed one and the
        // presigned redirect, so a file that is not CLEAN can be listed and have its
        // metadata read and still cannot be fetched.
        //
        // Today's upload path only ever writes CLEAN, so this is defence in depth
        // rather than a branch users reach. It is what makes an asynchronous scanner
        // safe to plug into the port without revisiting this method, and what lets an
        // operator quarantine a file by changing one column.
        if (!attachment.isApprovedForDownload()) {
            throw new ConflictException("That file is not available for download while it is being checked.");
        }

        Optional<java.net.URI> signed = store.presignedUrl(
                attachment.getStorageKey(),
                properties.downloadUrlTtl(),
                attachment.getFilename(),
                attachment.getContentType());

        return signed.map(url -> AttachmentContent.redirect(
                        attachment.getFilename(), attachment.getContentType(), attachment.getSizeBytes(), url))
                .orElseGet(() -> AttachmentContent.streamed(
                        attachment.getFilename(),
                        attachment.getContentType(),
                        attachment.getSizeBytes(),
                        store.open(attachment.getStorageKey())));
    }

    /**
     * Removes a file, soft.
     *
     * <p>The uploader may remove their own. Anybody else needs {@code attachment:manage_any}, or to
     * own the task's project or lead its team, which is the write scope a comment carries and a task
     * carries one level up.
     *
     * <p><strong>The bytes stay, for a while.</strong> Soft deletion is the platform convention where
     * restoring matters, and it would be a strange kind of restore that brought back a row pointing at
     * nothing. {@code AttachmentBytePurge} reclaims the object once the row has been soft-deleted for
     * longer than {@code app.storage.purge.retention}, which makes that setting also the window in which
     * a file deleted by mistake can still be recovered.
     */
    @Transactional
    public void delete(UUID workspaceId, UUID attachmentId, UUID actorUserId) {
        Attachment attachment = require(workspaceId, attachmentId);
        TaskContribution contribution = tasks.requireContribution(
                workspaceId,
                attachment.getTaskId(),
                Permissions.ATTACHMENT_DELETE,
                Permissions.ATTACHMENT_MANAGE_ANY);

        if (!attachment.isUploadedBy(actorUserId) && !contribution.moderator()) {
            throw new AccessDeniedException(
                    "Only the person who uploaded it, the project owner or its team lead may remove this file");
        }

        attachment.softDelete(clock.instant());

        events.publishEvent(new AttachmentEvents.AttachmentDeleted(
                attachment.getWorkspaceId(),
                attachment.getProjectId(),
                attachment.getTaskId(),
                attachmentId,
                attachment.getFilename(),
                actorUserId));
    }

    private Attachment requireReadable(UUID workspaceId, UUID attachmentId) {
        Attachment attachment = require(workspaceId, attachmentId);
        // Reaching the file needs no more than reaching the task it hangs off.
        tasks.requireReadableTask(workspaceId, attachment.getTaskId());
        return attachment;
    }

    private Attachment require(UUID workspaceId, UUID attachmentId) {
        return attachments.findByIdAndWorkspaceIdAndDeletedAtIsNull(attachmentId, workspaceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Attachment", attachmentId));
    }

    /**
     * Where the bytes live.
     *
     * <p>Nothing a user supplied appears in it. The filename is stored in a column, where it is data,
     * rather than in a path, where it would be an instruction.
     */
    private static String storageKey(TaskRef task) {
        return "workspace/" + task.workspaceId() + "/task/" + task.taskId() + "/" + UUID.randomUUID();
    }

    private static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("That file is empty.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    /** For integrity, and so a later phase can recognise the same file uploaded twice. */
    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-256. Unreachable, and not worth a checked exception.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
