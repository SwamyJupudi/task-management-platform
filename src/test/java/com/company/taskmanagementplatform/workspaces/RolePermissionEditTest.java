package com.company.taskmanagementplatform.workspaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.company.taskmanagementplatform.common.error.BadRequestException;
import com.company.taskmanagementplatform.common.error.ResourceNotFoundException;
import com.company.taskmanagementplatform.common.security.Permissions;

/**
 * The arithmetic behind the role editor, reasoned about alone.
 *
 * <p>Three things are worth a unit test rather than only an integration one, because each is a
 * decision rather than a wiring detail: that an unknown code stops the whole write, that a no-op
 * edit writes nothing and announces nothing, and that the audit event carries the difference rather
 * than the result.
 *
 * <p>The lockout refusal is exercised in {@code RoleLockoutIT} instead, against real permission
 * resolution: it depends on who the caller is and what role they hold, and a mocked version of that
 * would be a test of the mock.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RolePermissionEditTest {

    private static final UUID WORKSPACE = UUID.randomUUID();

    @Mock
    private RoleRepository roles;

    @Mock
    private PermissionRepository permissions;

    @Mock
    private RolePermissionRepository rolePermissions;

    @Mock
    private WorkspaceMemberRepository members;

    @Mock
    private ApplicationEventPublisher events;

    @Test
    void refusesARoleThatDoesNotBelongToThisWorkspace() {
        when(roles.findByWorkspaceIdAndSlug(WORKSPACE, "SUPER_ADMIN")).thenReturn(java.util.Optional.empty());

        // A platform role has a null workspace and so matches no such pair. The
        // schema is what makes it unreachable, not a check in the service.
        assertThatThrownBy(() -> service().replacePermissions(WORKSPACE, "SUPER_ADMIN", List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void refusesAnUnknownCodeAndWritesNothing() {
        givenRole("EMPLOYEE", Permissions.TASK_READ);
        givenCatalog(Permissions.TASK_READ);

        assertThatThrownBy(() ->
                        service().replacePermissions(WORKSPACE, "EMPLOYEE", List.of("task:read", "task:teleport")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("task:teleport");

        // Every code is checked before anything is written, so the role is left
        // exactly as it was rather than half replaced.
        verify(rolePermissions, never()).deleteAllByRoleId(any());
        verify(rolePermissions, never()).saveAll(anyCollection());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void aNoOpEditWritesNothingAndAnnouncesNothing() {
        givenRole("EMPLOYEE", Permissions.TASK_READ, Permissions.WORKSPACE_READ);
        givenCatalog(Permissions.TASK_READ, Permissions.WORKSPACE_READ);

        service().replacePermissions(WORKSPACE, "EMPLOYEE", List.of("workspace:read", "task:read"));

        // Order does not make a difference, and an audit trail with a row for every
        // request that changed nothing is a trail nobody reads.
        verify(rolePermissions, never()).deleteAllByRoleId(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void theEventCarriesTheDifferenceRatherThanTheResult() {
        givenRole("EMPLOYEE", Permissions.TASK_READ, Permissions.COMMENT_CREATE);
        givenCatalog(Permissions.TASK_READ, Permissions.COMMENT_CREATE, Permissions.WORKSPACE_READ);

        service().replacePermissions(WORKSPACE, "EMPLOYEE", List.of("task:read", "workspace:read"));

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());

        RoleEvents.PermissionsChanged event = (RoleEvents.PermissionsChanged) published.getValue();

        // "What changed" is the question an audit trail answers. A row listing the
        // forty codes a role ended up with answers a different one.
        assertThat(event.added()).containsExactly(Permissions.WORKSPACE_READ);
        assertThat(event.removed()).containsExactly(Permissions.COMMENT_CREATE);
        assertThat(event.roleSlug()).isEqualTo("EMPLOYEE");
        assertThat(event.workspaceId()).isEqualTo(WORKSPACE);
    }

    @Test
    void anEmptyListIsLegalAndMeansTheRoleGrantsNothing() {
        givenRole("EMPLOYEE", Permissions.TASK_READ);
        givenCatalog(Permissions.TASK_READ);

        var response = service().replacePermissions(WORKSPACE, "EMPLOYEE", List.of());

        assertThat(response.permissions()).isEmpty();
        verify(rolePermissions).deleteAllByRoleId(any());
    }

    @Test
    void duplicatesAreFoldedRatherThanRefused() {
        givenRole("EMPLOYEE");
        givenCatalog(Permissions.TASK_READ);

        var response = service().replacePermissions(WORKSPACE, "EMPLOYEE", List.of("task:read", "task:read"));

        // Asking for the same code twice is not a mistake worth failing a request
        // over, and the table's primary key would refuse the second row anyway.
        assertThat(response.permissions()).containsExactly(Permissions.TASK_READ);
    }

    // --- scaffolding --------------------------------------------------------

    private RoleAdminService service() {
        return new RoleAdminService(roles, permissions, rolePermissions, members, events);
    }

    private void givenRole(String slug, String... currentCodes) {
        Role role = Role.workspaceRole(WORKSPACE, SystemRole.EMPLOYEE);
        when(roles.findByWorkspaceIdAndSlug(WORKSPACE, slug)).thenReturn(java.util.Optional.of(role));

        when(rolePermissions.findAllByIdRoleId(any()))
                .thenReturn(java.util.Arrays.stream(currentCodes)
                        .map(code -> new RolePermission(role.getId(), idOf(code)))
                        .toList());
    }

    private void givenCatalog(String... codes) {
        List<Permission> catalog =
                java.util.Arrays.stream(codes).map(RolePermissionEditTest::permission).toList();

        when(permissions.findAll()).thenReturn(catalog);
        when(permissions.findAllByCodeIn(anyCollection())).thenAnswer(call -> {
            java.util.Collection<?> wanted = call.getArgument(0);
            return catalog.stream()
                    .filter(entry -> wanted.contains(entry.getCode()))
                    .toList();
        });
    }

    /**
     * A stable identifier per code, so "what the role holds now" and "what the catalog offers" line
     * up without the test having to thread real rows through two mocks.
     */
    private static UUID idOf(String code) {
        return UUID.nameUUIDFromBytes(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * A catalog row, built by reflection.
     *
     * <p>{@code Permission} has no public constructor and no setter, deliberately: its rows are
     * written by migrations and never by the application. Adding a factory to it so that a test can
     * call one would put a mutator on a production entity for no production reason, which is worse
     * than the four lines below. The alternative is an integration test, and there are several.
     */
    private static Permission permission(String code) {
        String[] halves = code.split(":", 2);
        try {
            java.lang.reflect.Constructor<Permission> constructor = Permission.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Permission entry = constructor.newInstance();

            set(entry, "id", idOf(code));
            set(entry, "code", code);
            set(entry, "resource", halves[0]);
            set(entry, "action", halves[1]);
            set(entry, "description", code);
            return entry;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Permission's shape changed; update this test", e);
        }
    }

    private static void set(Object target, String field, Object value) throws ReflectiveOperationException {
        java.lang.reflect.Field found = target.getClass().getDeclaredField(field);
        found.setAccessible(true);
        found.set(target, value);
    }
}
