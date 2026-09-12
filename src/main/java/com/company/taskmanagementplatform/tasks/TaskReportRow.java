package com.company.taskmanagementplatform.tasks;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One task as a report lists it: enough to render a row and link to it, and nothing more.
 *
 * <p>Serves both the overdue listing and the upcoming-deadline panel, because they are the same row
 * asked for on either side of today. The project and the person are identifiers; the reports module
 * resolves them to names in one lookup for a whole page rather than one per row.
 *
 * <p>Deliberately not the entity, for the reason every facade in this platform gives.
 */
public record TaskReportRow(
        UUID taskId,
        UUID projectId,
        int taskNumber,
        String title,
        TaskStatus status,
        TaskPriority priority,
        UUID assigneeUserId,
        LocalDate dueDate) {}
