package com.autoparts.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class AutopartsErpApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutopartsErpApplication.class, args);
		String password = "Admin@123";
		System.out.println(password + "paswword");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Set INITIAL_ADMIN_PASSWORD before running this utility.");
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

        System.out.println(encoder.encode(password));
	}

}
