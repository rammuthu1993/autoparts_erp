
package com.autoparts.erp.common.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {

    public static void main(String[] args) {

        String password = System.getenv("Admin@123");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Set INITIAL_ADMIN_PASSWORD before running this utility.");
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

        System.out.println(encoder.encode(password));
    }
}
