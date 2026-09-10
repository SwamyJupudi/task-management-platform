package com.company.taskmanagementplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Confirms an address.
 *
 * <p>A body rather than a query string, and a POST rather than a GET on the link itself. A token in a
 * URL ends up in browser history, in the referrer header of whatever the page loads next, and in the
 * access log of anything between the user and the server. The mailed link opens a page, and the page
 * posts this.
 */
@Schema(name = "VerifyEmailRequest")
public record VerifyEmailRequest(@NotBlank String token) {}
