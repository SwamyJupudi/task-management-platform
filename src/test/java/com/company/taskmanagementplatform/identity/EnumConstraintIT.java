package com.company.taskmanagementplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

/**
 * Holds each Java enumeration together with the check constraint that mirrors it.
 *
 * <p>Enumerations are stored as text with a check constraint rather than as a native type, which is
 * the right call and has one cost: the list exists twice and nothing notices when the two drift.
 * Adding a constant without a migration produces a write that fails in production and passes every
 * test that does not happen to use the new value.
 *
 * <p>The constraint text is read back from the catalog rather than hardcoded here, so this compares
 * the code against what the database actually has rather than against a third copy of the list.
 */
class EnumConstraintIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void userStatusMatchesItsConstraint() {
        assertEveryConstantIsAccepted(
                "users_status_check", com.company.taskmanagementplatform.users.UserStatus.class);
    }

    @Test
    void workspaceStatusMatchesItsConstraint() {
        assertEveryConstantIsAccepted(
                "workspaces_status_check", com.company.taskmanagementplatform.workspaces.WorkspaceStatus.class);
    }

    @Test
    void roleScopeMatchesItsConstraint() {
        assertEveryConstantIsAccepted(
                "roles_scope_check", com.company.taskmanagementplatform.workspaces.RoleScope.class);
    }

    @Test
    void invitationStatusMatchesItsConstraint() {
        assertEveryConstantIsAccepted(
                "workspace_invitations_status_check",
                com.company.taskmanagementplatform.workspaces.InvitationStatus.class);
    }

    @Test
    void revocationReasonMatchesItsConstraint() {
        // The one most likely to drift: a new reason is a natural thing to add while
        // writing a feature, and the migration is easy to forget.
        assertEveryConstantIsAccepted("refresh_tokens_revoked_reason_check", revocationReasonValues());
    }

    @Test
    void userTokenTypeMatchesItsConstraint() {
        assertEveryConstantIsAccepted("user_tokens_type_check", userTokenTypeValues());
    }

    @Test
    void taskStatusMatchesItsConstraint() {
        assertEveryConstantIsAccepted(
                "tasks_status_check", com.company.taskmanagementplatform.tasks.TaskStatus.class);
    }

    @Test
    void taskPriorityMatchesItsConstraint() {
        assertEveryConstantIsAccepted(
                "tasks_priority_check", com.company.taskmanagementplatform.tasks.TaskPriority.class);
    }

    @Test
    void subtaskStatusMatchesTheSameEnumeration() {
        // Subtasks share the task state machine, so one enumeration has to satisfy
        // two constraints. A value added for tasks and forgotten on subtasks would
        // pass every test that did not happen to tick a checklist item.
        assertEveryConstantIsAccepted(
                "subtasks_status_check", com.company.taskmanagementplatform.tasks.TaskStatus.class);
    }

    @Test
    void theScopeCarriedOnUsersMatchesTheRoleScopeConstant() {
        // The users module cannot import RoleScope, so it mirrors the literal. This
        // is the test that keeps the copy honest.
        String definition = constraintDefinition("users_platform_role_scope_check");

        assertThat(definition)
                .contains(com.company.taskmanagementplatform.workspaces.RoleScope.PLATFORM.name());
    }

    private void assertEveryConstantIsAccepted(String constraintName, Class<? extends Enum<?>> type) {
        assertEveryConstantIsAccepted(
                constraintName,
                Arrays.stream(type.getEnumConstants()).map(Enum::name).toList());
    }

    private void assertEveryConstantIsAccepted(String constraintName, List<String> constants) {
        String definition = constraintDefinition(constraintName);

        assertThat(constants)
                .as("constants named in %s", constraintName)
                .isNotEmpty()
                .allSatisfy(constant -> assertThat(definition)
                        .as("constraint %s accepts %s", constraintName, constant)
                        .contains("'" + constant + "'"));
    }

    private String constraintDefinition(String constraintName) {
        String definition =
                jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
                        String.class,
                        constraintName);

        assertThat(definition).as("constraint %s exists", constraintName).isNotNull();
        return definition;
    }

    /** Read reflectively because the enumerations are package-private to their module. */
    private List<String> revocationReasonValues() {
        return constantsOf("com.company.taskmanagementplatform.auth.RevocationReason");
    }

    private List<String> userTokenTypeValues() {
        return constantsOf("com.company.taskmanagementplatform.auth.UserTokenType");
    }

    private List<String> constantsOf(String className) {
        try {
            Class<?> type = Class.forName(className);
            Object[] constants = type.getEnumConstants();
            return Arrays.stream(constants).map(constant -> ((Enum<?>) constant).name()).toList();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Enumeration " + className + " was renamed or removed", e);
        }
    }
}
