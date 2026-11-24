package com.autoparts.erp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 Specification and Swagger UI configuration for Autoparts ERP.
 * Configures API metadata, tags, and Bearer JWT authentication for interactive testing.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI autopartsErpOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Autoparts ERP REST API")
                        .description("Enterprise Automotive Aftermarket ERP REST API documentation. " +
                                "Provides comprehensive endpoints for sales counter (POS), purchase orders, " +
                                "inventory management, customer & supplier ledgers, financial reporting, " +
                                "audit trails, and Sarvam AI assistant.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Autoparts ERP Architecture Team")
                                .email("support@autoparts-erp.com"))
                        .license(new License()
                                .name("Proprietary Enterprise License")
                                .url("https://autoparts-erp.com/licenses")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter JWT Bearer token to authorize API requests.")));
    }
}
