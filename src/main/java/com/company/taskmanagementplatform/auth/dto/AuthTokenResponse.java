package com.company.taskmanagementplatform.auth.dto;

import com.company.taskmanagementplatform.users.dto.UserResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a successful sign-in or refresh returns.
 *
 * <p>The refresh token is not in here. It travels in a cookie the browser will not let a script read,
 * which is the whole point of the arrangement, and putting a copy in the body would undo it.
 */
@Schema(name = "AuthTokens")
public record AuthTokenResponse(
        @Schema(description = "Bearer token; hold it in memory only") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Seconds until the access token expires", example = "900") long expiresIn,
        @Schema(description = "The signed-in account") UserResponse user) {

    public static AuthTokenResponse of(String accessToken, long expiresIn, UserResponse user) {
        return new AuthTokenResponse(accessToken, "Bearer", expiresIn, user);
    }
}
