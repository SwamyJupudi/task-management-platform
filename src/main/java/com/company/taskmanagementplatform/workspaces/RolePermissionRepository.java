package com.company.taskmanagementplatform.workspaces;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.RolePermissionId> {

    /**
     * The permission codes granted by any of these roles.
     *
     * <p>One query answers the whole authorization question, and there is no branch in it for the
     * platform administrator. {@code SUPER_ADMIN} arrives in {@code roleIds} like any other role and
     * its grants are ordinary rows, which is exactly why authorization has one implementation rather
     * than a general one and a privileged one.
     */
    @Query("""
            SELECT DISTINCT p.code
            FROM RolePermission rp, Permission p
            WHERE rp.id.permissionId = p.id
              AND rp.id.roleId IN :roleIds
            """)
    Set<String> findPermissionCodesByRoleIds(@Param("roleIds") Collection<UUID> roleIds);

    List<RolePermission> findAllByIdRoleId(UUID roleId);

    /**
     * Clears a role's grants, so the editor can write the replacement set.
     *
     * <p>The editor replaces the whole set rather than applying a delta, which is the same choice
     * the label editor made and for the same reason: a delta needs the client to know the current
     * state to compute it, and two administrators editing one role would silently merge into a set
     * neither of them chose.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("DELETE FROM RolePermission rp WHERE rp.id.roleId = :roleId")
    void deleteAllByRoleId(@Param("roleId") UUID roleId);
}
