package com.company.taskmanagementplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.common.security.Permissions;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * Holds the two halves of the permission catalog together.
 *
 * <p>The codes exist twice: as constants the annotations refer to, and as rows a migration wrote.
 * Nothing else notices when the two drift. A constant with no row is a permission nobody can ever
 * hold, and a row with no constant is a grant nothing checks, and both fail silently in production.
 */
class PermissionCatalogIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyConstantHasASeededRow() {
        List<String> seeded = jdbc.queryForList("SELECT code FROM permissions", String.class);

        assertThat(seeded).containsAll(Permissions.ALL);
    }

    @Test
    void everySeededRowHasAConstant() {
        List<String> seeded = jdbc.queryForList("SELECT code FROM permissions", String.class);

        assertThat(Permissions.ALL).containsAll(seeded);
    }

    @Test
    void thePlatformAdministratorIsMappedToEveryPermission() {
        // The obligation the seed migration carries. Since there is no bypass for
        // SUPER_ADMIN in the authorization path, a permission added without a
        // mapping is one the platform administrator silently does not hold.
        List<String> granted = jdbc.queryForList(
                """
                SELECT p.code
                FROM role_permissions rp
                JOIN permissions p ON p.id = rp.permission_id
                JOIN roles r ON r.id = rp.role_id
                WHERE r.slug = 'SUPER_ADMIN' AND r.scope = 'PLATFORM'
                """,
                String.class);

        assertThat(granted).containsExactlyInAnyOrderElementsOf(Permissions.ALL);
    }

    @Test
    void thereIsExactlyOnePlatformRole() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM roles WHERE scope = 'PLATFORM'", Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void thePlatformRoleIsMarkedAsSeededSoItCannotBeDeleted() {
        Boolean system = jdbc.queryForObject(
                "SELECT is_system FROM roles WHERE slug = 'SUPER_ADMIN' AND scope = 'PLATFORM'", Boolean.class);

        assertThat(system).isTrue();
    }
}
