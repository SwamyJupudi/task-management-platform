package com.company.taskmanagementplatform.attachments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * What survives of an uploaded filename.
 *
 * <p>None of this is what keeps a traversal off the filesystem: the storage key contains nothing a
 * user supplied. These rules are for the other end of a file's life, where the name is echoed in a
 * header and rendered in a list.
 */
class FilenamesTest {

    @Test
    void keepsAnOrdinaryName() {
        assertThat(Filenames.sanitise("architecture.pdf")).isEqualTo("architecture.pdf");
    }

    @Test
    void keepsSpacesAndUnicode() {
        assertThat(Filenames.sanitise("design notes (v2) über.png")).isEqualTo("design notes (v2) über.png");
    }

    @Test
    void dropsAPosixDirectoryPath() {
        assertThat(Filenames.sanitise("/etc/passwd")).isEqualTo("passwd");
    }

    @Test
    void dropsAWindowsDirectoryPath() {
        assertThat(Filenames.sanitise("C:\\Users\\ada\\report.docx")).isEqualTo("report.docx");
    }

    @Test
    void dropsTraversalSegments() {
        assertThat(Filenames.sanitise("../../../../etc/shadow")).isEqualTo("shadow");
        assertThat(Filenames.sanitise("..")).isEqualTo("file");
        assertThat(Filenames.sanitise(".")).isEqualTo("file");
    }

    @Test
    void removesControlCharactersThatCouldForgeAHeaderOrALogLine() {
        String hostile = "report" + (char) 13 + (char) 10 + "X-Evil: yes.pdf";

        assertThat(Filenames.sanitise(hostile)).isEqualTo("reportX-Evil: yes.pdf");
    }

    @Test
    void replacesQuotesRatherThanLettingThemCloseAHeaderValue() {
        assertThat(Filenames.sanitise("re\"port\".pdf")).isEqualTo("re'port'.pdf");
    }

    @Test
    void fallsBackWhenNothingUsableIsLeft() {
        assertThat(Filenames.sanitise("")).isEqualTo("file");
        assertThat(Filenames.sanitise("   ")).isEqualTo("file");
        assertThat(Filenames.sanitise(null)).isEqualTo("file");
        assertThat(Filenames.sanitise("/")).isEqualTo("file");
    }

    @Test
    void truncatesAtTheColumnLimit() {
        String long_ = "x".repeat(400) + ".png";

        assertThat(Filenames.sanitise(long_)).hasSize(Filenames.MAX_LENGTH);
    }
}
