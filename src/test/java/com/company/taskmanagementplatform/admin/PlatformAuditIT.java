package com.company.taskmanagementplatform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.company.taskmanagementplatform.users.UserAccount;

/**
 * The promise this phase discharges, proven end to end.
 *
 * <p>Architecture.md has said of the platform administrator since phase two that "every action it
 * takes is audited from the phase that adds auditing". Phase six added auditing and could not meet
 * that for platform actions, because {@code activity_logs.workspace_id} was {@code NOT NULL} and no
 * platform action happens inside a workspace. Nothing noticed, because no platform action had an
 * endpoint yet. Phase nine gives it several, so this is where the promise falls due and this is the
 * test that says it was kept.
 *
 * <p>Every row asserted here carries a <strong>null workspace</strong>, which is the shape {@code
 * V10} made possible. Rows are written after commit on the activity module's own thread, so each
 * assertion retries rather than assuming they have arrived; {@code ActivityConfig} explains why that
 * thread exists at all.
 */
class PlatformAuditIT extends AdminApiTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void editingSomebodysProfileIsRecorded() throws Exception {
        Estate estate = estate();
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("subject"));

        mockMvc.perform(patch("/api/v1/users/" + subject.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Grace\",\"lastName\":\"Hopper\"}"))
                .andExpect(status().isOk());

        eventually(() -> {
            Map<String, Object> row = one(subject.id(), "user.profile_updated");
            assertThat(row.get("workspace_id")).isNull();
            assertThat(row.get("entity_type")).isEqualTo("USER");
            assertThat(row.get("actor_user_id")).isEqualTo(estate.platformAdminId());
            // The correlation id ties the row to the log lines behind the request.
            assertThat(row.get("request_id")).isNotNull();
        });
    }

    @Test
    void deactivatingAndReactivatingAnAccountAreBothRecorded() throws Exception {
        Estate estate = estate();
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("subject"));
        String platform = bearer(estate.platformAdminId());

        mockMvc.perform(post("/api/v1/users/" + subject.id() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/" + subject.id() + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());

        eventually(() -> {
            assertThat(one(subject.id(), "user.deactivated").get("workspace_id")).isNull();
            // The status is carried because reactivation lands on
            // PENDING_APPROVAL for an account no administrator has approved.
            assertThat(String.valueOf(one(subject.id(), "user.activated").get("metadata")))
                    .contains("ACTIVE");
        });
    }

    @Test
    void removingAnAccountRecordsTheAddressItHadBeforeItVanished() throws Exception {
        Estate estate = estate();
        String email = uniqueEmail("departing");
        UserAccount subject = fixtures.verifiedUser(email);

        mockMvc.perform(delete("/api/v1/users/" + subject.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isNoContent());

        // Read before the write and before the events. By the time the audit
        // listener runs, the row is soft-deleted and every ordinary read hides it,
        // so an entry naming only an identifier would be unreadable for ever.
        eventually(() -> assertThat(String.valueOf(one(subject.id(), "user.deleted").get("metadata")))
                .contains(email));
    }

    @Test
    void grantingAndRevokingThePlatformRoleAreBothRecorded() throws Exception {
        Estate estate = estate();
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("promoted"));
        String platform = bearer(estate.platformAdminId());

        mockMvc.perform(put("/api/v1/users/" + subject.id() + "/platform-role")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/users/" + subject.id() + "/platform-role")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isNoContent());

        eventually(() -> {
            assertThat(one(subject.id(), "platform_role.granted").get("workspace_id")).isNull();
            assertThat(one(subject.id(), "platform_role.revoked").get("workspace_id")).isNull();
        });
    }

    @Test
    void startingARecoveryIsRecordedAndTheTokenIsNot() throws Exception {
        Estate estate = estate();
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("forgetful"));

        mockMvc.perform(post("/api/v1/users/" + subject.id() + "/password-reset")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isNoContent());

        // That a recovery was started, and nothing else. A token in an audit row
        // would be a live credential in a table nobody can delete from.
        eventually(() ->
                assertThat(one(subject.id(), "user.password_reset_requested").get("metadata"))
                        .isNull());
    }

    @Test
    void unlockingAnAccountThatWasNotLockedRecordsNothing() throws Exception {
        Estate estate = estate();
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("never-locked"));
        String platform = bearer(estate.platformAdminId());

        mockMvc.perform(post("/api/v1/users/" + subject.id() + "/unlock")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());

        // A later action on the same account, used as a fence. The audit executor
        // is a single FIFO thread by design, so once this row exists any row the
        // unlock would have written is already there too. That makes the negative
        // assertion below deterministic rather than a sleep hoping for the best.
        mockMvc.perform(post("/api/v1/users/" + subject.id() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());
        eventually(() -> assertThat(rows(subject.id(), "user.deactivated")).hasSize(1));

        // Idempotent, and silent when nothing happened. An audit trail with a row
        // for every no-op is a trail nobody reads.
        assertThat(rows(subject.id(), "user.unlocked")).isEmpty();
    }

    @Test
    void aRoleEditIsRecordedAgainstItsWorkspaceRatherThanThePlatform() throws Exception {
        Estate estate = estate();

        mockMvc.perform(put(rolePermissionsPath(estate.firstWorkspaceId(), "EMPLOYEE"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"task:read\",\"workspace:read\"]}"))
                .andExpect(status().isOk());

        eventually(() -> {
            Map<String, Object> row = jdbc
                    .queryForList(
                            "SELECT * FROM activity_logs WHERE action = ? AND workspace_id = ?",
                            "role.permissions_changed",
                            estate.firstWorkspaceId())
                    .get(0);

            // The one administrative row that names a workspace, because a role
            // belongs to one, so it lands in that workspace's own history. That is
            // where somebody wondering why their permissions changed would look.
            assertThat(row.get("workspace_id")).isEqualTo(estate.firstWorkspaceId());
            assertThat(row.get("entity_type")).isEqualTo("ROLE");

            // The difference rather than the result: "what changed" is the question
            // an audit trail answers.
            assertThat(String.valueOf(row.get("metadata"))).contains("removed").contains("EMPLOYEE");
        });
    }

    @Test
    void aPlatformRowIsStillAppendOnlyAfterTheMigrationThatMadeItPossible() throws Exception {
        Estate estate = estate();
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("immutable"));

        mockMvc.perform(patch("/api/v1/users/" + subject.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}"))
                .andExpect(status().isOk());

        eventually(() -> assertThat(rows(subject.id(), "user.profile_updated")).hasSize(1));

        // V10 altered the one table the platform promises cannot be edited, and the
        // promise is a trigger. Dropping a NOT NULL should not disturb it.
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE activity_logs SET action = 'tampered' WHERE entity_id = ?", subject.id()))
                .hasMessageContaining("append only");
    }

    @Test
    void theAdminActivityEndpointReturnsTheseRowsAndTheWorkspaceBrowseDoesNot() throws Exception {
        Estate estate = estate();
        UserAccount subject = fixtures.verifiedUser(uniqueEmail("browsed"));
        String platform = bearer(estate.platformAdminId());

        mockMvc.perform(post("/api/v1/users/" + subject.id() + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());
        eventually(() -> assertThat(rows(subject.id(), "user.deactivated")).hasSize(1));

        assertThat(body(get(PLATFORM_ACTIVITY), platform)).contains(subject.id().toString());

        // The other direction, which matters just as much: a platform row must not
        // surface in a workspace's own history. Both listings are disjoint by
        // construction, decided by whether the workspace is null.
        assertThat(body(
                        get("/api/v1/workspaces/" + estate.firstWorkspaceId() + "/activity"),
                        platform))
                .doesNotContain("user.deactivated");
    }

    // --- helpers ------------------------------------------------------------

    private String body(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String bearer)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Map<String, Object> one(UUID entityId, String action) {
        List<Map<String, Object>> found = rows(entityId, action);
        assertThat(found).as("one %s row for %s", action, entityId).hasSize(1);
        return found.get(0);
    }

    private List<Map<String, Object>> rows(UUID entityId, String action) {
        return jdbc.queryForList(
                "SELECT * FROM activity_logs WHERE entity_id = ? AND action = ?", entityId, action);
    }
}
