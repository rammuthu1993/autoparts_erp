package com.autoparts.erp.auth.bootstrap;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(
        prefix = "app.security.bootstrap-admin",
        name = "enabled",
        havingValue = "true"
)
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    private final String username;
    private final String password;

    public BootstrapAdminInitializer(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate,
            org.springframework.core.env.Environment environment
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;

        this.username = environment.getProperty(
                "app.security.bootstrap-admin.username", ""
        );

        this.password = environment.getProperty(
                "app.security.bootstrap-admin.password", ""
        );
    }

    @Override
    public void run(ApplicationArguments args) {

        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Bootstrap ADMIN username and password must be configured"
            );
        }

        if (username.length() > 100) {
            throw new IllegalStateException(
                    "Bootstrap ADMIN username cannot exceed 100 characters"
            );
        }

        if (password.length() < 12 || password.length() > 128) {
            throw new IllegalStateException(
                    "Bootstrap ADMIN password must contain between 12 and 128 characters"
            );
        }

        transactionTemplate.executeWithoutResult(status -> {

            // Prevent concurrent application instances from
            // creating multiple initial administrator accounts.
            jdbcTemplate.execute(
                    "LOCK TABLE users IN EXCLUSIVE MODE"
            );

            Integer userCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users",
                    Integer.class
            );

            if (userCount != null && userCount > 0) {
                log.info(
                        "Bootstrap skipped: user accounts already exist."
                );
                return;
            }

            Long adminRoleId = jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM roles
                    WHERE name = ?
                    """,
                    Long.class,
                    "ADMIN"
            );

            if (adminRoleId == null) {
                throw new IllegalStateException(
                        "ADMIN role does not exist. Check the V1 role migration."
                );
            }

            String encodedPassword =
                    passwordEncoder.encode(password);

            Long userId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO users (
                        uuid,
                        username,
                        password_hash,
                        enabled
                    )
                    VALUES (?, ?, ?, TRUE)
                    RETURNING id
                    """,
                    Long.class,
                    UUID.randomUUID(),
                    username.trim(),
                    encodedPassword
            );

            if (userId == null) {
                throw new IllegalStateException(
                        "Unable to create bootstrap administrator"
                );
            }

            jdbcTemplate.update(
                    """
                    INSERT INTO user_roles (
                        user_id,
                        role_id
                    )
                    VALUES (?, ?)
                    """,
                    userId,
                    adminRoleId
            );

            log.info(
                    "Initial ADMIN account created successfully. Username: {}",
                    username.trim()
            );
        });
    }
}
