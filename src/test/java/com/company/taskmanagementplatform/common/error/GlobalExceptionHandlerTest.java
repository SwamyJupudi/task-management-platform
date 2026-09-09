package com.company.taskmanagementplatform.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.taskmanagementplatform.common.web.RequestIdFilter;

/**
 * The error contract, exercised through a throwaway controller.
 *
 * <p>The most important assertion here is the negative one: an unexpected failure must not leak its
 * message to the caller.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void notFoundUsesTheMessageTheApplicationChose() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Project 42 was not found"))
                .andExpect(jsonPath("$.path").value("/test/not-found"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void conflictIsReportedAsConflict() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void validationFailureListsTheOffendingFields() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void unreadableBodyIsReportedAsMalformed() throws Exception {
        mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unexpectedFailureNeverLeaksItsCause() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("jdbc:postgresql"))))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void successfulResponsesAreUntouched() throws Exception {
        mockMvc.perform(get("/test/ok")).andExpect(status().isOk());
    }

    @RestController
    @RequestMapping("/test")
    static class FailingController {

        @org.springframework.web.bind.annotation.GetMapping("/ok")
        String ok() {
            return "ok";
        }

        @org.springframework.web.bind.annotation.GetMapping("/not-found")
        String notFound() {
            throw ResourceNotFoundException.of("Project", 42);
        }

        @org.springframework.web.bind.annotation.GetMapping("/conflict")
        String conflict() {
            throw new ConflictException("Project key is already taken");
        }

        @org.springframework.web.bind.annotation.GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("connection to jdbc:postgresql://db:5432 refused for user tmp_app");
        }

        @PostMapping("/validate")
        String validate(@jakarta.validation.Valid @RequestBody Payload payload) {
            return payload.name();
        }
    }

    record Payload(@NotBlank String name) {}
}
