package com.company.taskmanagementplatform.teams;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);

    Page<TeamMember> findAllByTeamId(UUID teamId, Pageable pageable);

    List<TeamMember> findAllByTeamId(UUID teamId);

    long countByTeamId(UUID teamId);

    /**
     * Sizes for a whole page of teams in one query.
     *
     * <p>A count per row is exactly the shape that turns a list endpoint into N+1 without anybody
     * noticing until it is in production.
     *
     * @return rows of {@code [teamId, count]}
     */
    @Query("SELECT tm.teamId, count(tm) FROM TeamMember tm WHERE tm.teamId IN :teamIds GROUP BY tm.teamId")
    List<Object[]> countByTeamIds(@Param("teamIds") Collection<UUID> teamIds);

    /** Used when somebody leaves a workspace: every team of that workspace loses them at once. */
    long deleteAllByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /** Used when an account is removed altogether. */
    long deleteAllByUserId(UUID userId);
}
