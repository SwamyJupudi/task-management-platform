package com.company.taskmanagementplatform.projects;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.labels.LabelCatalog;

/**
 * Attaching tags to a project.
 *
 * <p>The folding, de-duplication and bounds live in {@link LabelCatalog}, which tasks use too, so a
 * tag means the same thing wherever it is applied. This class owns only the join rows, which are the
 * projects module's own.
 */
@Component
class ProjectLabelService {

    private final LabelCatalog catalog;
    private final ProjectLabelRepository projectLabels;

    ProjectLabelService(LabelCatalog catalog, ProjectLabelRepository projectLabels) {
        this.catalog = catalog;
        this.projectLabels = projectLabels;
    }

    /**
     * Replaces every tag on the project with the given set.
     *
     * <p>A replacement rather than a merge, because tags are a set: sending the list a client is
     * showing should produce exactly that list, and an empty one should clear them.
     */
    void replace(UUID workspaceId, UUID projectId, List<String> names) {
        List<UUID> labelIds = catalog.resolve(workspaceId, names);

        projectLabels.deleteAllByIdProjectId(projectId);
        projectLabels.flush();

        for (UUID labelId : labelIds) {
            projectLabels.save(new ProjectLabel(projectId, labelId, workspaceId));
        }
    }
}
