package com.company.taskmanagementplatform.labels;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private, like every repository in the platform. Reached through {@link LabelCatalog}. */
interface LabelRepository extends JpaRepository<Label, UUID> {

    /** Folded to match the unique index, so "Backend" and "backend" resolve to one label. */
    @Query(
            """
            SELECT l FROM Label l
            WHERE l.workspaceId = :workspaceId
              AND lower(trim(l.name)) = lower(trim(:name))
            """)
    Optional<Label> findByFoldedName(@Param("workspaceId") UUID workspaceId, @Param("name") String name);
}
