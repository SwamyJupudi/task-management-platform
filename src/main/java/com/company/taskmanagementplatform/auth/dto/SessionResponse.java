package com.company.taskmanagementplatform.auth.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One live session, as shown to the person it belongs to.
 *
 * <p>The address and the browser string are here because a list of sessions without them tells nobody
 * anything. They are shown only to their owner and appear in no other response and in no log.
 */
@Schema(name = "Session")
public record SessionResponse(
        UUID sessionId,
        @Schema(description = "Browser or client that started the session") String userAgent,
        @Schema(description = "Address the session was started from") String ipAddress,
        Instant startedAt,
        Instant expiresAt,
        @Schema(description = "Whether this is the session making the request") boolean current) {}
