package com.company.taskmanagementplatform.labels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * The folding rules behind the one catalog projects and tasks share.
 *
 * <p>These exist once so that a tag means the same thing wherever it is applied. They also have to
 * agree with the unique index behind the table: a rule that folded differently would declare a name
 * valid and then fail on the insert, which is a failure nobody sees until production.
 */
class LabelCatalogNormalisationTest {

    @Test
    void nullAndEmptyBothMeanNoLabels() {
        assertThat(LabelCatalog.normalise(null)).isEmpty();
        assertThat(LabelCatalog.normalise(List.of())).isEmpty();
    }

    @Test
    void namesAreTrimmed() {
        assertThat(LabelCatalog.normalise(List.of("  backend  "))).containsExactly("backend");
    }

    @Test
    void caseAndSpaceFoldOntoOneLabel() {
        // Matches the unique index, which folds the same way. Without this a
        // workspace acquires "Backend" and "backend" as two separate tags.
        assertThat(LabelCatalog.normalise(List.of("Backend", "backend", " BACKEND ")))
                .containsExactly("Backend");
    }

    @Test
    void theFirstSpellingIsTheOneKept() {
        // So a workspace's catalog reads the way the person who introduced the tag
        // wrote it, rather than lower-cased into something nobody typed.
        assertThat(LabelCatalog.normalise(List.of("API", "api"))).containsExactly("API");
    }

    @Test
    void orderIsPreserved() {
        assertThat(LabelCatalog.normalise(List.of("one", "two", "three")))
                .containsExactly("one", "two", "three");
    }

    @Test
    void nullEntriesAreSkippedRatherThanRefused() {
        // A client sending a sparse array is careless, not malicious, and dropping
        // the holes is friendlier than refusing the whole request.
        List<String> withHoles = new ArrayList<>(Arrays.asList("backend", null, "api"));
        assertThat(LabelCatalog.normalise(withHoles)).containsExactly("backend", "api");
    }

    @Test
    void aBlankNameIsRefused() {
        assertThatThrownBy(() -> LabelCatalog.normalise(List.of("   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void aNameLongerThanTheColumnIsRefused() {
        // The check constraint bounds it at 40. Refusing here turns a constraint
        // violation into a sentence somebody can act on.
        assertThatThrownBy(() -> LabelCatalog.normalise(List.of("x".repeat(41))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("40");
    }

    @Test
    void aNameExactlyAtTheLimitIsAccepted() {
        assertThat(LabelCatalog.normalise(List.of("x".repeat(40)))).hasSize(1);
    }

    @Test
    void tooManyLabelsAreRefused() {
        // Bounded so that a labels array cannot become a way to write unbounded
        // rows through one request.
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= LabelCatalog.MAX_LABELS_PER_ENTITY; i++) {
            tooMany.add("label-" + i);
        }

        assertThatThrownBy(() -> LabelCatalog.normalise(tooMany)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void theBoundCountsFoldedNamesRatherThanRawOnes() {
        // Twenty-one spellings of one tag is one tag, so it must not be refused.
        List<String> duplicates = new ArrayList<>();
        for (int i = 0; i <= LabelCatalog.MAX_LABELS_PER_ENTITY; i++) {
            duplicates.add(i % 2 == 0 ? "backend" : "BACKEND");
        }

        assertThat(LabelCatalog.normalise(duplicates)).hasSize(1);
    }
}
