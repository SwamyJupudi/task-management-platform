package com.company.taskmanagementplatform.projects;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProjectLabelRepository extends JpaRepository<ProjectLabel, ProjectLabel.ProjectLabelId> {

    List<ProjectLabel> findAllByIdProjectId(UUID projectId);

    long deleteAllByIdProjectId(UUID projectId);

    /**
     * The tags of a whole page of projects in one query.
     *
     * @return rows of {@code [projectId, labelName]}
     */
    @Query(
            """
            SELECT pl.id.projectId, l.name FROM ProjectLabel pl
            JOIN Label l ON l.id = pl.id.labelId
            WHERE pl.id.projectId IN :projectIds
            ORDER BY l.name
            """)
    List<Object[]> findNamesByProjectIds(@Param("projectIds") Collection<UUID> projectIds);
}
