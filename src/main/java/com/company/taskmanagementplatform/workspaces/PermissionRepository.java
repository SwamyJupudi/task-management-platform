package com.company.taskmanagementplatform.workspaces;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findAllByOrderByCodeAsc();

    List<Permission> findAllByCodeIn(Collection<String> codes);
}
