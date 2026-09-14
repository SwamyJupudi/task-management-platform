package com.company.taskmanagementplatform.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The configuration this module owns.
 *
 * <p>Only the purge settings. Everything else the authentication flows read lives under {@code
 * app.security} and is bound by {@code common.security.SecurityBeansConfig}, because those values are
 * shared with the filter chain and the token services rather than private to this package.
 *
 * <p>Registered here rather than on {@code common.scheduling.SchedulingConfig}, which switches the
 * scheduler on. That class must not know what any module schedules: {@code common} does not depend on a
 * module anywhere in this codebase, and the one place the rule is written down is {@code
 * PermissionResolver}, which is declared in {@code common.security} and implemented in {@code
 * workspaces} for exactly this reason.
 */
@Configuration
@EnableConfigurationProperties(TokenPurgeProperties.class)
class AuthConfig {}
