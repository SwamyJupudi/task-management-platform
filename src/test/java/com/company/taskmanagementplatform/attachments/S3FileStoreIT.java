package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * {@link S3FileStore} against a real server that speaks the S3 API.
 *
 * <p>LocalStack rather than a mocked client, because everything worth testing here is a property of
 * the protocol rather than of the code: whether a presigned URL actually works when fetched, whether
 * the response overrides survive the signature, whether a deleted key is really gone. A mock would
 * assert that the SDK was called and prove none of it.
 *
 * <p>The credentials here are the container's, invented for this test and thrown away with it. In
 * production nothing supplies credentials at all: {@code StorageConfig} builds the client on the
 * SDK's default chain, which is why this test constructs its own client instead of asking Spring for
 * one.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3FileStoreIT {

    private static final String BUCKET = "attachments";
    private static final byte[] CONTENT = "the quick brown fox".getBytes(StandardCharsets.UTF_8);

    /**
     * Signature validation is off in LocalStack by default, which would make the expiry test pass
     * for the wrong reason: an expired URL would still be served. Turning it on is what makes this
     * container behave like S3 about the one property the download path depends on.
     */
    private static final LocalStackContainer S3 = new LocalStackContainer("localstack/localstack:3")
            .withServices("s3")
            .withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0");

    private static S3Client client;
    private static S3Presigner presigner;
    private static S3FileStore store;

    @BeforeAll
    static void startS3() {
        S3.start();

        StaticCredentialsProvider credentials =
                StaticCredentialsProvider.create(AwsBasicCredentials.create(S3.getAccessKey(), S3.getSecretKey()));
        S3Configuration pathStyle =
                S3Configuration.builder().pathStyleAccessEnabled(true).build();

        client = S3Client.builder()
                .endpointOverride(S3.getEndpoint())
                .region(Region.of(S3.getRegion()))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();

        presigner = S3Presigner.builder()
                .endpointOverride(S3.getEndpoint())
                .region(Region.of(S3.getRegion()))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();

        client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        store = new S3FileStore(client, presigner, BUCKET);
    }

    @AfterAll
    static void stopS3() {
        if (presigner != null) presigner.close();
        if (client != null) client.close();
        S3.stop();
    }

    @Test
    void theProviderIsTheOneTheDatabaseColumnAllows() {
        // The row records this string and the column constrains it to
        // ('LOCAL','S3'), so a typo here would be a constraint violation on the
        // first upload rather than a compile error.
        assertThat(store.provider()).isEqualTo("S3");
    }

    @Test
    void whatIsWrittenIsWhatComesBack() throws Exception {
        String key = key();
        store.put(key, new ByteArrayInputStream(CONTENT), "text/plain", CONTENT.length);

        try (InputStream in = store.open(key)) {
            assertThat(in.readAllBytes()).isEqualTo(CONTENT);
        }
    }

    @Test
    void readingSomethingThatWasNeverWrittenFails() {
        assertThatThrownBy(() -> store.open(key())).isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void deletingRemovesTheBytes() {
        String key = key();
        store.put(key, new ByteArrayInputStream(CONTENT), "text/plain", CONTENT.length);

        store.delete(key);

        assertThatThrownBy(() -> store.open(key)).isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    void deletingSomethingThatIsNotThereIsNotAnError() {
        // Housekeeping never fails the caller. S3 answers 204 for an absent key,
        // so this asserts the contract rather than the catch block.
        store.delete(key());
    }

    /**
     * The test this class exists for.
     *
     * <p>On the redirect path the application sets no headers at all — the bytes never pass through
     * it. Everything that makes the download safe has to be inside the signature, and this is what
     * proves it is: fetch the URL exactly as a browser would and read what comes back.
     */
    @Test
    void aPresignedUrlServesTheFileAsADownloadWithItsDetectedType() throws Exception {
        String key = key();
        store.put(key, new ByteArrayInputStream(CONTENT), "text/plain", CONTENT.length);

        Optional<URI> url = store.presignedUrl(key, Duration.ofMinutes(5), "meeting notes.txt", "text/plain");
        assertThat(url).isPresent();

        HttpResponse<byte[]> response = fetch(url.orElseThrow());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(CONTENT);
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValueSatisfying(disposition -> assertThat(disposition)
                        .startsWith("attachment")
                        .contains("meeting notes.txt"));
        assertThat(response.headers().firstValue("Content-Type")).hasValue("text/plain");
    }

    @Test
    void aFilenameThatNeedsEncodingSurvivesTheSignature() throws Exception {
        String key = key();
        store.put(key, new ByteArrayInputStream(CONTENT), "text/plain", CONTENT.length);

        Optional<URI> url = store.presignedUrl(key, Duration.ofMinutes(5), "quarterly büdget \"final\".txt", "text/plain");

        HttpResponse<byte[]> response = fetch(url.orElseThrow());
        assertThat(response.statusCode()).isEqualTo(200);

        // RFC 6266 encodes the non-ASCII name into filename*, which is what
        // Spring's ContentDisposition builds and what survives the round trip.
        String disposition = response.headers().firstValue("Content-Disposition").orElseThrow();
        assertThat(disposition).startsWith("attachment");
        assertThat(URLDecoder.decode(disposition, StandardCharsets.UTF_8)).contains("büdget");
    }

    @Test
    void anExpiredUrlNoLongerWorks() throws Exception {
        String key = key();
        store.put(key, new ByteArrayInputStream(CONTENT), "text/plain", CONTENT.length);

        // One second, then wait it out. The TTL is the only thing standing between
        // a leaked URL and permanent access, so it has to actually expire.
        Optional<URI> url = store.presignedUrl(key, Duration.ofSeconds(1), "notes.txt", "text/plain");
        Thread.sleep(1500);

        assertThat(fetch(url.orElseThrow()).statusCode()).isEqualTo(403);
    }

    /**
     * What the URL is made of, since it ends up in a Location header, a proxy log and a browser's
     * history.
     *
     * <p>SigV4 puts the access key <em>id</em> in {@code X-Amz-Credential} by design — it is public —
     * and the secret appears nowhere, only the signature derived from it. Asserting the secret's
     * absence directly is not possible against this container, which uses the literal "test" for both
     * halves of the pair; asserting the shape is, and the shape is what guarantees it.
     */
    @Test
    void theUrlCarriesASignatureAndAnExpiryRatherThanASecret() {
        String key = key();
        store.put(key, new ByteArrayInputStream(CONTENT), "text/plain", CONTENT.length);

        String url = store.presignedUrl(key, Duration.ofMinutes(5), "notes.txt", "text/plain")
                .orElseThrow()
                .toString();

        assertThat(url)
                .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                .contains("X-Amz-Signature=")
                // The TTL this store was asked for, carried into the signature
                // rather than merely remembered by the application.
                .contains("X-Amz-Expires=300");
    }

    /** Not closed: HttpClient is only AutoCloseable from Java 21 and this targets 17. */
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static HttpResponse<byte[]> fetch(URI url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /** The shape {@code AttachmentService} generates: no user input, slash separated. */
    private static String key() {
        return "workspace/" + UUID.randomUUID() + "/task/" + UUID.randomUUID() + "/" + UUID.randomUUID();
    }
}
