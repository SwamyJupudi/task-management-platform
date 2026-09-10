package com.company.taskmanagementplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.company.taskmanagementplatform.common.mail.MailMessage;
import com.company.taskmanagementplatform.common.mail.MailSender;
import com.company.taskmanagementplatform.support.AbstractIntegrationTest;

import tools.jackson.databind.json.JsonMapper;
import com.company.taskmanagementplatform.support.IdentityFixtures;
import com.company.taskmanagementplatform.support.RecordingMailSender;
import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.workspaces.dto.WorkspaceResponse;

/**
 * Inviting somebody to a workspace, and the two ways an invitation is redeemed.
 *
 * <p>The requirements list registration and invitation together, and this is why: an invitation has
 * to work for somebody who has no account as well as for somebody who does.
 */
class InvitationFlowIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private IdentityFixtures fixtures;

    @Autowired
    private MailSender mailSender;

    private RecordingMailSender mail;
    private UserAccount owner;
    private WorkspaceResponse workspace;

    @BeforeEach
    void setUp() {
        mail = (RecordingMailSender) mailSender;
        mail.clear();
        owner = fixtures.superAdmin(uniqueEmail("owner"));
        workspace = fixtures.workspace("Invites", uniqueSlug("invites"), owner.id());
    }

    @Test
    void anInvitationIsSentAndNeverReturnsItsToken() throws Exception {
        String invitee = uniqueEmail("new-person");

        mockMvc.perform(invite(invitee, "EMPLOYEE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(invitee))
                .andExpect(jsonPath("$.status").value("PENDING"))
                // Only the hash is stored, so there is nothing to return even here.
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist());

        assertThat(mail.lastToken(MailMessage.MailTemplate.WORKSPACE_INVITATION)).isPresent();
    }

    @Test
    void theLinkShowsOnlyTheWorkspaceNameBeforeSigningIn() throws Exception {
        String invitee = uniqueEmail("preview");
        mockMvc.perform(invite(invitee, "EMPLOYEE")).andExpect(status().isCreated());
        String token = mail.lastToken(MailMessage.MailTemplate.WORKSPACE_INVITATION).orElseThrow();

        // No bearer token: whoever holds the link may read this.
        mockMvc.perform(get("/api/v1/invitations").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Invites"))
                .andExpect(jsonPath("$.email").value(invitee))
                .andExpect(jsonPath("$.accountExists").value(false));
    }

    @Test
    void somebodyWithNoAccountJoinsAndIsVerifiedByTheTokenItself() throws Exception {
        // Redeeming proves the address as well as a verification message would: the
        // token went there and nowhere else.
        String invitee = uniqueEmail("joiner");
        mockMvc.perform(invite(invitee, "EMPLOYEE")).andExpect(status().isCreated());
        String token = mail.lastToken(MailMessage.MailTemplate.WORKSPACE_INVITATION).orElseThrow();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptance(token, IdentityFixtures.PASSWORD, "New", "Person")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(workspace.id().toString()));

        // Straight in, with no separate verification step.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", invitee, "password", IdentityFixtures.PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void aNewAccountNeedsAPasswordAndName() throws Exception {
        String invitee = uniqueEmail("incomplete");
        mockMvc.perform(invite(invitee, "EMPLOYEE")).andExpect(status().isCreated());
        String token = mail.lastToken(MailMessage.MailTemplate.WORKSPACE_INVITATION).orElseThrow();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anExistingAccountMustBeSignedInToAccept() throws Exception {
        // Otherwise holding the link would be enough to add somebody else's account
        // to a workspace.
        UserAccount existing = fixtures.verifiedUser(uniqueEmail("existing"));
        mockMvc.perform(invite(existing.email(), "EMPLOYEE")).andExpect(status().isCreated());
        String token = mail.lastToken(MailMessage.MailTemplate.WORKSPACE_INVITATION).orElseThrow();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anotherSignedInAccountCannotRedeemSomebodyElsesInvitation() throws Exception {
        UserAccount invited = fixtures.verifiedUser(uniqueEmail("invited"));
        UserAccount interloper = fixtures.verifiedUser(uniqueEmail("interloper"));
        mockMvc.perform(invite(invited.email(), "EMPLOYEE")).andExpect(status().isCreated());
        String token = mail.lastToken(MailMessage.MailTemplate.WORKSPACE_INVITATION).orElseThrow();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(interloper.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theInvitedAccountJoinsWhenSignedIn() throws Exception {
        UserAccount invited = fixtures.verifiedUser(uniqueEmail("joining"));
        mockMvc.perform(invite(invited.email(), "TEAM_LEAD")).andExpect(status().isCreated());
        String token = mail.lastToken(MailMessage.MailTemplate.WORKSPACE_INVITATION).orElseThrow();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(invited.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, fixtures.bearer(invited.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships[0].roleSlug").value("TEAM_LEAD"));
    }

    @Test
    void anInvitationWorksOnlyOnce() throws Exception {
        String invitee = uniqueEmail("once");
        mockMvc.perform(invite(invitee, "EMPLOYEE")).andExpect(status().isCreated());
        String token = mail.lastToken(MailMessage.MailTemplate.WORKSPACE_INVITATION).orElseThrow();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptance(token, IdentityFixtures.PASSWORD, "New", "Person")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptance(token, IdentityFixtures.PASSWORD, "New", "Person")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reInvitingSupersedesTheEarlierLink() throws Exception {
        String invitee = uniqueEmail("resent");
        mockMvc.perform(invite(invitee, "EMPLOYEE")).andExpect(status().isCreated());
        String first = mail.lastToken(MailMessage.MailTemplate.WORKSPACE_INVITATION).orElseThrow();

        mockMvc.perform(invite(invitee, "EMPLOYEE")).andExpect(status().isCreated());
        String second = mail.lastToken(MailMessage.MailTemplate.WORKSPACE_INVITATION).orElseThrow();
        assertThat(first).isNotEqualTo(second);

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptance(first, IdentityFixtures.PASSWORD, "New", "Person")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptance(second, IdentityFixtures.PASSWORD, "New", "Person")))
                .andExpect(status().isOk());
    }

    @Test
    void anUnknownTokenIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/invitations").param("token", "not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void somebodyAlreadyInTheWorkspaceCannotBeInvitedAgain() throws Exception {
        UserAccount member = fixtures.verifiedUser(uniqueEmail("already"));
        fixtures.addMember(workspace.id(), member.id(), "EMPLOYEE");

        mockMvc.perform(invite(member.email(), "EMPLOYEE")).andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder invite(
            String email, String roleSlug) throws Exception {
        return post("/api/v1/workspaces/" + workspace.id() + "/invitations")
                .header(HttpHeaders.AUTHORIZATION, fixtures.bearer(owner.id()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "roleSlug", roleSlug)));
    }

    private String acceptance(String token, String password, String firstName, String lastName) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("token", token);
        body.put("password", password);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        return json.writeValueAsString(body);
    }
}
