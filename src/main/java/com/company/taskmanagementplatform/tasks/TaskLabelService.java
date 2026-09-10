package com.company.taskmanagementplatform.tasks;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.company.taskmanagementplatform.labels.LabelCatalog;

/**
 * Attaching labels to a task.
 *
 * <p>The same shape as the projects module's tagging, over the same catalog and through the same
 * {@link LabelCatalog}. That is the point: the requirements call them tags on a project and labels
 * on a task, and a workspace that has "Backend" must not acquire "backend" because a different
 * module folded the name a different way.
 */
@Component
class TaskLabelService {

    private final LabelCatalog catalog;
    private final TaskLabelRepository taskLabels;

    TaskLabelService(LabelCatalog catalog, TaskLabelRepository taskLabels) {
        this.catalog = catalog;
        this.taskLabels = taskLabels;
    }

    /**
     * Replaces every label on the task with the given set.
     *
     * <p>A replacement rather than a merge, because labels are a set: sending the list a client is
     * showing should produce exactly that list, and an empty one should clear them.
     */
    void replace(UUID workspaceId, UUID taskId, List<String> names) {
        List<UUID> labelIds = catalog.resolve(workspaceId, names);

        taskLabels.deleteAllByIdTaskId(taskId);
        taskLabels.flush();

        for (UUID labelId : labelIds) {
            taskLabels.save(new TaskLabel(taskId, labelId, workspaceId));
        }
    }
}
