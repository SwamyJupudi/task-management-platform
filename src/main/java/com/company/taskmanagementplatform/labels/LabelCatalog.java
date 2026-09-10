package com.company.taskmanagementplatform.labels;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * The one way in and out of the workspace tag catalog.
 *
 * <p>Projects call it tags and tasks call it labels, and both arrive here. The point of the shared
 * component rather than a copy in each module is that the folding rules exist once: a workspace that
 * has "Backend" cannot also acquire "backend", and the rule that decides so is the same rule the
 * unique index behind the table enforces. Two implementations of it would drift, and the drift would
 * show up as a constraint violation on an insert that a service had just declared valid.
 *
 * <p>Get-or-create rather than a separate catalog API, which is the scope approved for these phases.
 * Somebody tagging something should not have to register the tag first, and the catalog screen
 * belongs to the admin panel.
 */
@Service
public class LabelCatalog {

    /** Bounded so a tag list cannot become a way to write unbounded rows through one request. */
    public static final int MAX_LABELS_PER_ENTITY = 20;

    private static final int MAX_LABEL_LENGTH = 40;

    private final LabelRepository labels;

    LabelCatalog(LabelRepository labels) {
        this.labels = labels;
    }

    /**
     * Resolves names to label identifiers, creating the ones this workspace does not have yet.
     *
     * <p>Runs inside the caller's transaction, so a tagging that fails later takes any label it
     * invented with it.
     *
     * @param names as typed; null is treated as an empty list
     * @return the identifiers, in the order the names were first seen
     * @throws BadRequestException if a name is blank, too long, or there are too many
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> resolve(UUID workspaceId, List<String> names) {
        List<UUID> resolved = new ArrayList<>();
        for (String name : normalise(names)) {
            Label label = labels.findByFoldedName(workspaceId, name)
                    .orElseGet(() -> labels.save(Label.create(workspaceId, name)));
            resolved.add(label.getId());
        }
        return resolved;
    }

    /**
     * The identifier of one existing label, for a listing filtered by tag.
     *
     * <p>Empty when the workspace has no such tag, which a filter must read as "matches nothing"
     * rather than as "matches everything".
     */
    @Transactional(readOnly = true)
    public java.util.Optional<UUID> findId(UUID workspaceId, String name) {
        if (name == null || name.isBlank()) {
            return java.util.Optional.empty();
        }
        return labels.findByFoldedName(workspaceId, name.trim()).map(Label::getId);
    }

    /**
     * Trimmed, de-duplicated by fold, and bounded.
     *
     * <p>The first spelling used is the one kept, so a workspace's catalog reads the way the person
     * who introduced the tag wrote it.
     */
    static Set<String> normalise(List<String> names) {
        if (names == null) {
            return Set.of();
        }

        Set<String> folded = new LinkedHashSet<>();
        Set<String> kept = new LinkedHashSet<>();

        for (String raw : names) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (name.isEmpty()) {
                throw new BadRequestException("A tag cannot be blank.");
            }
            if (name.length() > MAX_LABEL_LENGTH) {
                throw new BadRequestException("A tag may be at most " + MAX_LABEL_LENGTH + " characters.");
            }
            if (folded.add(name.toLowerCase(Locale.ROOT))) {
                kept.add(name);
            }
        }

        if (kept.size() > MAX_LABELS_PER_ENTITY) {
            throw new BadRequestException("At most " + MAX_LABELS_PER_ENTITY + " tags may be applied.");
        }
        return kept;
    }
}
