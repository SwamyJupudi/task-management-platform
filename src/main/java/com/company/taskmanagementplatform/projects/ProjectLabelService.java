package com.company.taskmanagementplatform.projects;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.common.error.BadRequestException;

/**
 * Attaching tags to a project, creating them in the workspace catalog on first use.
 *
 * <p>Get-or-create rather than a separate catalog API, which is the approved scope for this phase.
 * Somebody tagging a project should not have to go and register the tag first, and the catalog
 * screen belongs to the admin panel.
 *
 * <p>Names are folded before lookup so that "Backend" and "backend" resolve to one label, matching
 * the unique index behind the table. The first spelling used is the one stored and shown.
 */
@Component
class ProjectLabelService {

    private static final int MAX_LABELS_PER_PROJECT = 20;

    private final LabelRepository labels;
    private final ProjectLabelRepository projectLabels;

    ProjectLabelService(LabelRepository labels, ProjectLabelRepository projectLabels) {
        this.labels = labels;
        this.projectLabels = projectLabels;
    }

    /**
     * Replaces every tag on the project with the given set.
     *
     * <p>A replacement rather than a merge, because tags are a set: sending the list a client is
     * showing should produce exactly that list, and an empty one should clear them.
     */
    void replace(UUID workspaceId, UUID projectId, List<String> names) {
        Set<String> wanted = normalise(names);

        projectLabels.deleteAllByIdProjectId(projectId);
        projectLabels.flush();

        for (String name : wanted) {
            Label label = labels.findByFoldedName(workspaceId, name)
                    .orElseGet(() -> labels.save(Label.create(workspaceId, name)));
            projectLabels.save(new ProjectLabel(projectId, label.getId(), workspaceId));
        }
    }

    /**
     * Trimmed, de-duplicated by fold, and bounded.
     *
     * @throws BadRequestException if a name is empty, too long, or there are too many
     */
    private static Set<String> normalise(List<String> names) {
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
            if (name.length() > 40) {
                throw new BadRequestException("A tag may be at most 40 characters.");
            }
            if (folded.add(name.toLowerCase(java.util.Locale.ROOT))) {
                kept.add(name);
            }
        }

        if (kept.size() > MAX_LABELS_PER_PROJECT) {
            throw new BadRequestException("A project may carry at most " + MAX_LABELS_PER_PROJECT + " tags.");
        }
        return kept;
    }
}
