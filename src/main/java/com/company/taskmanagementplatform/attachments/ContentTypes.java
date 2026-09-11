package com.company.taskmanagementplatform.attachments;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Decides what an uploaded file actually is, from its leading bytes.
 *
 * <p>The client's {@code Content-Type} header and the filename extension are both attacker
 * controlled, so neither is consulted for anything but an error message. What is stored and what is
 * later served is what this class detected.
 *
 * <p>It is an <strong>allowlist</strong>. Anything not recognised is refused, which is the opposite
 * of a denylist and the reason a new dangerous format does not need this file to be updated to stay
 * out.
 *
 * <p>Three of the decisions here are worth stating.
 *
 * <p><strong>SVG, HTML and XML are refused</strong>, even though they are perfectly good text. An SVG
 * is a script-carrying document that browsers execute, and refusing anything that begins with a
 * markup delimiter keeps all three out with one rule. Downloads are already served as attachments
 * with {@code nosniff}, so this is the second lock on that door rather than the only one.
 *
 * <p><strong>A CSV is stored as {@code text/plain}</strong>, because a CSV and a text file are
 * indistinguishable from their bytes, and inventing a distinction the data does not support would be
 * a guess dressed as a detection. The original filename keeps its extension, which is what the
 * person who downloads it actually uses.
 *
 * <p><strong>Office documents are detected by looking inside the archive</strong>, not by trusting
 * the extension on a ZIP. The JDK reads zip files, so this costs no dependency and turns "it says
 * .docx" into "it contains a Word document".
 */
final class ContentTypes {

    static final String PNG = "image/png";
    static final String JPEG = "image/jpeg";
    static final String GIF = "image/gif";
    static final String WEBP = "image/webp";
    static final String PDF = "application/pdf";
    static final String ZIP = "application/zip";
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    static final String TEXT = "text/plain";

    private ContentTypes() {}

    /**
     * What these bytes are, or empty when they are nothing this platform accepts.
     *
     * @param content the whole file. Uploads are bounded by {@code app.storage.max-file-size}, which
     *     is what makes holding one in memory a reasonable thing to do
     */
    static Optional<String> detect(byte[] content) {
        if (content.length == 0) {
            return Optional.empty();
        }

        if (startsWith(content, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 'G', 'I', 'F', '8')) {
            return Optional.of(GIF);
        }
        if (startsWith(content, 'R', 'I', 'F', 'F') && matchesAt(content, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        if (startsWith(content, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        if (startsWith(content, 'P', 'K', 0x03, 0x04)) {
            return Optional.of(insideTheArchive(content));
        }
        if (isPlainText(content)) {
            return Optional.of(TEXT);
        }
        return Optional.empty();
    }

    /**
     * Which Office format a zip holds, by the entry every one of them carries.
     *
     * <p>An archive that is none of them is an ordinary zip, which is on the allowlist in its own
     * right.
     */
    private static String insideTheArchive(byte[] content) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("word/document.xml")) {
                    return DOCX;
                }
                if (name.startsWith("xl/workbook.xml")) {
                    return XLSX;
                }
                if (name.startsWith("ppt/presentation.xml")) {
                    return PPTX;
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            // A zip we cannot walk is still a zip by its header. Refusing it here
            // would reject a large archive for being large.
            return ZIP;
        }
        return ZIP;
    }

    /**
     * Text, and not markup pretending to be text.
     *
     * <p>Valid UTF-8, no NUL or other control characters beyond the three that belong in prose, and
     * not beginning with a markup delimiter.
     */
    private static boolean isPlainText(byte[] content) {
        String decoded;
        try {
            decoded = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            return false;
        }

        if (decoded.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t')) {
            return false;
        }

        String leading = decoded.stripLeading();
        // Catches SVG, HTML and XML in one rule, including an SVG with a comment or
        // a declaration in front of the root element.
        return !leading.startsWith("<");
    }

    private static boolean startsWith(byte[] content, int... signature) {
        return matchesAt(content, 0, signature);
    }

    private static boolean matchesAt(byte[] content, int offset, int... signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[offset + i] & 0xFF) != (signature[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }
}
