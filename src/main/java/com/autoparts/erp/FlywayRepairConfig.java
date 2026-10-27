package com.autoparts.erp;

import org.flywaydb.core.Flyway;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRepairConfig {

    @Bean
    CommandLineRunner repairFlyway(Flyway flyway) {
        return args -> {
            flyway.repair();
            System.out.println("Flyway repair completed.");
        };
    }
}