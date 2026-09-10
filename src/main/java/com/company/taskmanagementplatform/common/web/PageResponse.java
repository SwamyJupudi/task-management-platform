package com.company.taskmanagementplatform.common.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The one paginated body shape returned by every list endpoint in the platform.
 *
 * <p>Spring's own {@code Page} is not returned directly. Its JSON is a serialisation of an internal
 * type rather than a designed contract, it changes between versions, and it carries fields no client
 * needs. This record is small, stable, and safe to generate a client from.
 *
 * @param content the page of items, already mapped to response types
 * @param page zero-based index of this page
 * @param size the requested page size
 * @param totalElements how many items match in total
 * @param totalPages how many pages of this size the total occupies
 * @param first whether this is the first page
 * @param last whether this is the final page
 */
@Schema(name = "Page", description = "A single page of results")
public record PageResponse<T>(
        @Schema(description = "The items on this page") List<T> content,
        @Schema(description = "Zero-based page index", example = "0") int page,
        @Schema(description = "Requested page size", example = "20") int size,
        @Schema(description = "Total matching items", example = "137") long totalElements,
        @Schema(description = "Total number of pages", example = "7") int totalPages,
        @Schema(description = "Whether this is the first page") boolean first,
        @Schema(description = "Whether this is the last page") boolean last) {

    /** Maps a persistence page to the response shape, converting each element on the way. */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
