package com.company.taskmanagementplatform.attachments;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * A {@link FileStore} backed by S3, or by anything that speaks its API.
 *
 * <p>The production store. {@code LocalFileStore} stays for development and the test suite, and
 * {@code StorageConfig} is what refuses to let it become the answer in production.
 *
 * <p>Nothing about the provider reaches a client. The bucket, the region and the key appear in no
 * response: a download is either bytes from this application or a 302 to a signed URL, and the
 * attachment response has never carried {@code storageProvider}.
 *
 * <p>Written against the S3 API rather than against Amazon, deliberately. MinIO, R2, Spaces and B2
 * all speak it, and {@code app.storage.endpoint} together with {@code path-style-access} is the
 * whole difference between them. The integration test runs against MinIO for exactly that reason.
 */
class S3FileStore implements FileStore {

    private static final Logger log = LoggerFactory.getLogger(S3FileStore.class);

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    S3FileStore(S3Client client, S3Presigner presigner, String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public String provider() {
        return StorageProperties.Provider.S3.name();
    }

    /**
     * Writes the object.
     *
     * <p>The length is passed rather than discovered, because the caller already knows it and the
     * SDK's streaming body needs it to avoid buffering the content a second time.
     *
     * <p>The content type stored here is the one detected from the bytes, never the one the client
     * declared. That matters more than it looks: it is what a presigned URL later serves the object
     * as, so a lie here would become a lie in somebody's browser.
     */
    @Override
    public void put(String key, InputStream content, String contentType, long sizeBytes) {
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(sizeBytes)
                        .build(),
                RequestBody.fromInputStream(content, sizeBytes));
    }

    @Override
    public InputStream open(String key) {
        return client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /**
     * Removes the bytes.
     *
     * <p>Nothing calls this yet, here or in the local store. Deletion is soft and the byte purge that
     * would reclaim the storage is hardening-phase work, recorded in {@code architecture.md}. The
     * method exists because the port declares it, and so that the purge finds it already tested.
     *
     * <p>Logs rather than throws, matching the local store: reclaiming space is housekeeping, and
     * failing somebody's request over it would be worse than leaving an object behind.
     */
    @Override
    public void delete(String key) {
        try {
            client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            log.warn("Could not remove stored object: key={}", key, e);
        }
    }

    /**
     * A short-lived URL the browser follows directly, with the two things that make a download safe
     * built into it.
     *
     * <p><strong>This is the security-carrying part of this class.</strong> On the streaming path the
     * controller sets {@code Content-Disposition: attachment} and the content type itself. On this
     * path it cannot: the bytes never pass through the application, so those headers come from the
     * object store. The two response overrides below are how the same guarantee survives the change
     * of route. Without them an accepted type that a browser will happily render — a PDF, a text
     * file — would be displayed inline from the bucket's own origin rather than saved.
     *
     * <p>The filename is encoded by the same {@code ContentDisposition} builder the controller uses,
     * so a name with a quote or an umlaut in it is encoded once, correctly, and identically whichever
     * route the download takes.
     *
     * <p>The URL is signed for {@code ttl} and carries its own authorization, which is the trade this
     * path makes: the check happened when it was issued, and anybody holding it can fetch the object
     * until it expires. {@code app.storage.download-url-ttl} is therefore a security setting, and it
     * is short.
     */
    @Override
    public Optional<URI> presignedUrl(String key, Duration ttl, String filename, String contentType) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .responseContentDisposition(ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .responseContentType(contentType)
                .build();

        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(get)
                .build();

        return Optional.of(presigner.presignGetObject(request).url().toString()).map(URI::create);
    }
}
