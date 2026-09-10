package com.company.taskmanagementplatform.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * The sort allowlist, which is a security control rather than a convenience.
 *
 * <p>Spring will sort by any property name it can resolve. Passing a client's sort straight through
 * therefore turns a query parameter into a way to probe the shape of the entity, and invites ordering
 * by a column with no index behind it. Neither failure is visible from the response, which is exactly
 * why it needs a test rather than a review.
 */
class TaskSortTest {

    @Test
    void anUnsortedRequestFallsBackToNewestFirst() {
        Sort resolved = TaskQuery.validateSort(Sort.unsorted());

        assertThat(resolved.getOrderFor("createdAt")).isNotNull();
        assertThat(resolved.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void aNullSortIsTreatedTheSameWay() {
        assertThat(TaskQuery.validateSort(null).isSorted()).isTrue();
    }

    @Test
    void everyAllowedFieldIsAccepted() {
        for (String field : TaskQuery.sortableFields()) {
            assertThatCode(() -> TaskQuery.validateSort(Sort.by(field)))
                    .as("sorting by %s", field)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void theBoardColumnIsSortable() {
        // The Kanban view orders within a column by this, so it has to be on the
        // list even though nothing else sorts by it.
        assertThat(TaskQuery.sortableFields()).contains("boardPosition", "taskNumber", "dueDate", "priority");
    }

    @Test
    void anythingElseIsRefusedByName() {
        assertThatThrownBy(() -> TaskQuery.validateSort(Sort.by("description")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("description");
    }

    @Test
    void aColumnThatWouldLeakSomethingIsRefused() {
        // Not on the list, and the one somebody would reach for first.
        assertThatThrownBy(() -> TaskQuery.validateSort(Sort.by("assigneeUserId")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> TaskQuery.validateSort(Sort.by("deletedAt")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void oneBadFieldRefusesTheWholeSort() {
        // A multi-field sort must not quietly apply the half of it that was allowed.
        assertThatThrownBy(() -> TaskQuery.validateSort(Sort.by("title").and(Sort.by("workspaceId"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("workspaceId");
    }
}
