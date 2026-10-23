package com.autoparts.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class AutopartsErpApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(AutopartsErpApplication.class, args);
		BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder(12);
//		boolean matches = passwordEncoder.matches(
//			    "admin",
//			    "$2a$12$XXD.dZsHa5bSA3e4nu5zUeoGvEgl4fVRjbvbdbx3C79p79mvdfX3K"
//			);
//
//			System.out.println("Password matches: " + matches);
		String rawPassword = "Admin@12345";

        String hash = encoder.encode(rawPassword);

        System.out.println("BCrypt hash: " + hash);

        System.out.println(
            "Matches: " + encoder.matches(rawPassword, hash)
        );
	}

}
