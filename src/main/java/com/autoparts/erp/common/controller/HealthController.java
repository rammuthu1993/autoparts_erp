package com.autoparts.erp.common.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @SecurityRequirements
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "application", "autoparts-erp"
        );
    }
}