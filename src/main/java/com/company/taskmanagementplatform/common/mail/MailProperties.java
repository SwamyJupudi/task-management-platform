package com.company.taskmanagementplatform.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the stand-in mail transport.
 *
 * @param linkBaseUrl where the links in a message point, normally the frontend origin
 * @param logTokens whether the development transport may write a single-use token to the log. False
 *     everywhere except development and test, because the logging rules forbid it. See {@link
 *     LoggingMailSender}.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String linkBaseUrl, boolean logTokens) {}
