package com.company.taskmanagementplatform.attachments;

import java.io.InputStream;
import java.net.URI;

/**
 * An authorized download, in whichever of the two forms the storage provider supports.
 *
 * <p>Exactly one of {@code redirectTo} and {@code stream} is set. A provider that can issue a
 * short-lived signed URL gives the first, and the endpoint answers 302 so the bytes never travel
 * through the application; one that cannot gives the second and the endpoint streams. The client
 * sees one address either way, which is what makes moving to direct-to-bucket downloads a
 * configuration change rather than an API change.
 *
 * <p>Authorization has already happened by the time this exists. Neither form leaks the storage key.
 */
public record AttachmentContent(
        String filename, String contentType, long sizeBytes, URI redirectTo, InputStream stream) {

    static AttachmentContent streamed(String filename, String contentType, long sizeBytes, InputStream stream) {
        return new AttachmentContent(filename, contentType, sizeBytes, null, stream);
    }

    static AttachmentContent redirect(String filename, String contentType, long sizeBytes, URI url) {
        return new AttachmentContent(filename, contentType, sizeBytes, url, null);
    }

    public boolean isRedirect() {
        return redirectTo != null;
    }
}
