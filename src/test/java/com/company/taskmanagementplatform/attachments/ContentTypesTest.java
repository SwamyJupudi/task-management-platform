package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/**
 * What the sniffer accepts, and more importantly what it refuses.
 *
 * <p>Every case here is written against bytes rather than against a filename, because a filename is
 * what an attacker controls and the bytes are what a browser will eventually act on.
 */
class ContentTypesTest {

    @Test
    void recognisesAPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

        assertThat(ContentTypes.detect(png)).contains(ContentTypes.PNG);
    }

    @Test
    void recognisesAJpeg() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

        assertThat(ContentTypes.detect(jpeg)).contains(ContentTypes.JPEG);
    }

    @Test
    void recognisesAGif() {
        assertThat(ContentTypes.detect("GIF89a...".getBytes(StandardCharsets.US_ASCII)))
                .contains(ContentTypes.GIF);
    }

    @Test
    void recognisesAWebp() {
        byte[] webp = "RIFF____WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

        assertThat(ContentTypes.detect(webp)).contains(ContentTypes.WEBP);
    }

    @Test
    void doesNotMistakeAnyRiffContainerForAWebp() {
        // A WAV is a RIFF too, and checking only the first four bytes would accept
        // it as an image. Its real header carries a little-endian length, so the
        // bytes here are what one actually looks like rather than a readable stand-in.
        byte[] wav = {'R', 'I', 'F', 'F', 0x24, 0x08, 0x00, 0x00, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};

        assertThat(ContentTypes.detect(wav)).isEmpty();
    }

    @Test
    void recognisesAPdf() {
        assertThat(ContentTypes.detect("%PDF-1.7\n%...".getBytes(StandardCharsets.US_ASCII)))
                .contains(ContentTypes.PDF);
    }

    @Test
    void recognisesPlainText() {
        assertThat(ContentTypes.detect("first line\nsecond line\n".getBytes(StandardCharsets.UTF_8)))
                .contains(ContentTypes.TEXT);
    }

    @Test
    void treatsACsvAsTheTextItIs() {
        // A CSV and a text file are indistinguishable from their bytes, and
        // inventing a distinction the data does not support would be a guess
        // dressed up as a detection.
        assertThat(ContentTypes.detect("name,role\nada,engineer\n".getBytes(StandardCharsets.UTF_8)))
                .contains(ContentTypes.TEXT);
    }

    @Test
    void refusesAnSvgEvenThoughItIsValidText() {
        // The reason this rule exists: an SVG is a script-carrying document that
        // browsers execute.
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(ContentTypes.detect(svg)).isEmpty();
    }

    @Test
    void refusesHtmlAndXmlByTheSameRule() {
        assertThat(ContentTypes.detect("<!DOCTYPE html><html></html>".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
        assertThat(ContentTypes.detect("<?xml version='1.0'?><root/>".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
    }

    @Test
    void refusesAnSvgWithLeadingWhitespace() {
        assertThat(ContentTypes.detect("\n   <svg></svg>".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
    }

    @Test
    void refusesAWindowsExecutable() {
        byte[] exe = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

        assertThat(ContentTypes.detect(exe)).isEmpty();
    }

    @Test
    void refusesAnEmptyFile() {
        assertThat(ContentTypes.detect(new byte[0])).isEmpty();
    }

    @Test
    void refusesBinaryThatIsNotOnTheList() {
        byte[] elf = {0x7F, 'E', 'L', 'F', 2, 1, 1, 0};

        assertThat(ContentTypes.detect(elf)).isEmpty();
    }

    @Test
    void recognisesAnOrdinaryZip() throws Exception {
        assertThat(ContentTypes.detect(zipContaining("notes.txt"))).contains(ContentTypes.ZIP);
    }

    @Test
    void looksInsideAZipToRecogniseAWordDocument() throws Exception {
        // Not by the extension on the archive, which is the thing being claimed.
        assertThat(ContentTypes.detect(zipContaining("word/document.xml"))).contains(ContentTypes.DOCX);
    }

    @Test
    void looksInsideAZipToRecogniseASpreadsheetAndAPresentation() throws Exception {
        assertThat(ContentTypes.detect(zipContaining("xl/workbook.xml"))).contains(ContentTypes.XLSX);
        assertThat(ContentTypes.detect(zipContaining("ppt/presentation.xml"))).contains(ContentTypes.PPTX);
    }

    private static byte[] zipContaining(String entryName) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write("content".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
