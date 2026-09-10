package com.company.taskmanagementplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.company.taskmanagementplatform.users.UserAccount;
import com.company.taskmanagementplatform.users.UserAccountService;
import com.company.taskmanagementplatform.workspaces.PlatformRoleService;

/**
 * Creates the first platform administrator, once, from the environment.
 *
 * <p>Nothing can be administered until one exists, and there is no good way to seed one in a
 * migration: a password in a migration is a password in the repository, and a hash in a migration is
 * a hash nobody can rotate. So it comes from the environment, is hashed by the same encoder as every
 * other password, and is never written to a log.
 *
 * <p>Three behaviours are worth stating because each was a decision:
 *
 * <ul>
 *   <li><b>Nothing configured</b> is not an error. A running installation that already has an
 *       administrator should not need these variables set for ever.
 *   <li><b>Half configured</b> is an error, and a loud one. An address without a password almost
 *       always means a deployment that believes it created an administrator and did not, and that
 *       belief is discovered at the worst possible moment. Failing at startup is kinder.
 *   <li><b>Already present</b> does nothing at all. A restart never resurrects a deleted
 *       administrator, never overwrites a changed password, and never quietly grants the role to
 *       whatever address happens to be in the environment today.
 * </ul>
 */
@Component
class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final UserAccountService users;
    private final PlatformRoleService platformRoles;
    private final String configuredEmail;
    private final String configuredPassword;

    SuperAdminBootstrap(
            UserAccountService users,
            PlatformRoleService platformRoles,
            @org.springframework.beans.factory.annotation.Value("${app.bootstrap.super-admin-email:}")
                    String configuredEmail,
            @org.springframework.beans.factory.annotation.Value("${app.bootstrap.super-admin-password:}")
                    String configuredPassword) {
        this.users = users;
        this.platformRoles = platformRoles;
        this.configuredEmail = configuredEmail;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean hasEmail = !configuredEmail.isBlank();
        boolean hasPassword = !configuredPassword.isBlank();

        if (hasEmail != hasPassword) {
            throw new IllegalStateException("Incomplete platform administrator bootstrap configuration: "
                    + (hasEmail
                            ? "SUPER_ADMIN_EMAIL is set but SUPER_ADMIN_PASSWORD is not."
                            : "SUPER_ADMIN_PASSWORD is set but SUPER_ADMIN_EMAIL is not.")
                    + " Set both, or neither.");
        }

        if (!hasEmail) {
            return;
        }

        java.util.UUID roleId = platformRoles.superAdminRoleId();

        if (users.anyoneHoldsPlatformRole(roleId)) {
            log.info("A platform administrator already exists; bootstrap skipped.");
            return;
        }

        UserAccount account = users.findByEmail(configuredEmail).orElse(null);
        if (account == null) {
            // Verified on creation: nobody can send this address a confirmation
            // message before the platform has an administrator to send it.
            account = users.register(configuredEmail, configuredPassword, "Platform", "Administrator", true);
        }

        // No role identifier is passed. The only thing this runner can grant is the
        // platform administrator role, so naming the wrong one is not expressible.
        platformRoles.assignSuperAdmin(account.id());
        // The address is identifying, not secret, and knowing which account holds
        // the role is exactly what an operator needs from this line. The password
        // appears nowhere.
        log.warn("Created the first platform administrator for {}", configuredEmail);
    }
}
