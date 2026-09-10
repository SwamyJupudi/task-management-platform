package com.company.taskmanagementplatform.auth;

/** What a single-use token is for. The values match the check constraint on the table. */
enum UserTokenType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}
