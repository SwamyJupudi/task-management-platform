package com.company.taskmanagementplatform.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * That the two cross-workspace listings page, sort within an allowlist, and refuse a page too large.
 *
 * <p>These are the only listings in the platform with no tenant predicate narrowing them first, so
 * the caps are the whole of what stands between a query parameter and an unbounded read over every
 * project in the installation. The test profile lowers the cap to twenty-five so this test does not
 * have to build a hundred rows to reach it.
 */
class AdminPagingIT extends AdminApiTestBase {

    @Test
    void bothListingsPage() throws Exception {
        Estate estate = estate();
        String platform = bearer(estate.platformAdminId());

        mockMvc.perform(get(PLATFORM_PROJECTS)
                        .param("page", "0")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1));

        mockMvc.perform(get(PLATFORM_ACCOUNTS)
                        .param("page", "0")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void everyAllowlistedProjectSortIsAccepted() throws Exception {
        Estate estate = estate();

        for (String field : new String[] {"name", "key", "status", "progress", "createdAt", "updatedAt"}) {
            mockMvc.perform(sorted(PLATFORM_PROJECTS, field, estate)).andExpect(status().isOk());
        }
    }

    @Test
    void everyAllowlistedAccountSortIsAccepted() throws Exception {
        Estate estate = estate();

        for (String field : new String[] {"email", "firstName", "lastName", "status", "createdAt", "lastLoginAt"}) {
            mockMvc.perform(sorted(PLATFORM_ACCOUNTS, field, estate)).andExpect(status().isOk());
        }
    }

    @Test
    void aFieldOutsideTheAllowlistIsRefused() throws Exception {
        Estate estate = estate();

        // Spring would happily resolve these, which is what turns the parameter
        // into a way to probe the shape of an entity.
        mockMvc.perform(sorted(PLATFORM_PROJECTS, "description", estate)).andExpect(status().isBadRequest());
        mockMvc.perform(sorted(PLATFORM_ACCOUNTS, "passwordHash", estate)).andExpect(status().isBadRequest());
    }

    @Test
    void theWorkspaceNameCannotBeSortedByEvenThoughItIsInTheResponse() throws Exception {
        Estate estate = estate();

        // It is resolved from a second module after the page is fetched, so sorting
        // by it would order each page independently of the others.
        mockMvc.perform(sorted(PLATFORM_PROJECTS, "workspaceName", estate)).andExpect(status().isBadRequest());
    }

    @Test
    void aPageOverTheCapIsRefusedRatherThanClamped() throws Exception {
        Estate estate = estate();
        String platform = bearer(estate.platformAdminId());

        // The cap is sixty in the test profile: above the audit trail's own
        // default of fifty, which a request naming no size at all would otherwise
        // trip over.
        for (String path : new String[] {PLATFORM_PROJECTS, PLATFORM_ACCOUNTS, PLATFORM_ACTIVITY}) {
            mockMvc.perform(get(path).param("size", "61").header(HttpHeaders.AUTHORIZATION, platform))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void aPageAtTheCapIsAccepted() throws Exception {
        Estate estate = estate();

        mockMvc.perform(get(PLATFORM_PROJECTS)
                        .param("size", "60")
                        .header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId())))
                .andExpect(status().isOk());
    }

    @Test
    void aRequestNamingNoSizeAtAllIsNeverRefused() throws Exception {
        Estate estate = estate();
        String platform = bearer(estate.platformAdminId());

        // The invariant behind the cap's value, asserted rather than assumed. Each
        // of these carries its own @PageableDefault, and the audit trail's is fifty
        // where the listings use twenty. A cap set below the largest of them turns
        // the plainest possible request into a 400.
        for (String path : new String[] {PLATFORM_PROJECTS, PLATFORM_ACCOUNTS, PLATFORM_ACTIVITY}) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, platform))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void theProjectOverviewFiltersByWorkspaceWithoutThatBeingAScope() throws Exception {
        Estate estate = estate();
        String platform = bearer(estate.platformAdminId());

        // A workspaceId here narrows an already-authorized platform read. It is not
        // a scope, and it cannot widen anything, because there is nothing wider
        // than what the caller already reaches.
        mockMvc.perform(get(PLATFORM_PROJECTS)
                        .param("workspaceId", estate.firstWorkspaceId().toString())
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].workspaceId").value(estate.firstWorkspaceId().toString()))
                .andExpect(jsonPath("$.content[0].workspaceName").value("First"));

        // And unfiltered, it spans both. A listing that quietly carried a tenant
        // predicate would pass every assertion above and fail this one.
        mockMvc.perform(get(PLATFORM_PROJECTS)
                        .param("size", "25")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void theAccountDirectoryFiltersByStatusAndLockState() throws Exception {
        Estate estate = estate();
        String platform = bearer(estate.platformAdminId());

        mockMvc.perform(get(PLATFORM_ACCOUNTS)
                        .param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk());

        // Nobody in this fixture is locked, so the filter proves itself by
        // returning nothing rather than everything, which is the failure mode that
        // matters for a filter.
        mockMvc.perform(get(PLATFORM_ACCOUNTS)
                        .param("locked", "true")
                        .header(HttpHeaders.AUTHORIZATION, platform))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private MockHttpServletRequestBuilder sorted(String path, String field, Estate estate) {
        return get(path).param("sort", field).header(HttpHeaders.AUTHORIZATION, bearer(estate.platformAdminId()));
    }
}
