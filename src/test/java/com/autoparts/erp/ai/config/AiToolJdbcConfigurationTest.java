package com.autoparts.erp.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

class AiToolJdbcConfigurationTest {

    private final AiToolJdbcConfiguration configuration = new AiToolJdbcConfiguration();

    @Test
    void developmentAiDataSourceUsesDedicatedCredentialsAndIsReadOnly() {
        try (HikariDataSource ai = configuration.developmentAiToolDataSource(
                "jdbc:postgresql://localhost:5432/autoparts_erp", "autoparts_ai_reader", "test-only")) {
            assertEquals("autoparts_ai_reader", ai.getUsername());
            assertTrue(ai.isReadOnly());
            assertEquals(3, ai.getMaximumPoolSize());
        }
    }

    @Test
    void developmentTemplateUsesDedicatedAiDataSourceAndAppliesConfiguredLimits() {
        try (HikariDataSource ai = configuration.developmentAiToolDataSource(
                "jdbc:postgresql://localhost:5432/autoparts_erp", "autoparts_ai_reader", "test-only")) {
            JdbcTemplate template = configuration.developmentAiToolJdbcTemplate(ai, 7, 125);
            assertSame(ai, template.getDataSource());
            assertEquals(7, template.getQueryTimeout());
            assertEquals(125, template.getMaxRows());
        }
    }

    @Test
    void productionTemplateUsesDedicatedDataSourceAndAppliesConfiguredLimits() {
        try (HikariDataSource application = new HikariDataSource();
             HikariDataSource ai = configuration.productionAiToolDataSource(
                     "jdbc:postgresql://localhost:5432/autoparts_erp", "ai_tool_reader", "test-only")) {
            JdbcTemplate template = configuration.productionAiToolJdbcTemplate(ai, 11, 75);
            assertSame(ai, template.getDataSource());
            assertNotSame(application, template.getDataSource());
            assertTrue(ai.isReadOnly());
            assertEquals(3, ai.getMaximumPoolSize());
            assertEquals(11, template.getQueryTimeout());
            assertEquals(75, template.getMaxRows());
        }
    }


    @Test
    void namedParameterTemplatesKeepApplicationAndAiDataSourcesSeparated() {
        try (HikariDataSource application = new HikariDataSource();
             HikariDataSource ai = new HikariDataSource()) {
            var applicationTemplate = configuration.applicationNamedParameterJdbcTemplate(application);
            var aiTemplate = configuration.aiToolNamedParameterJdbcTemplate(ai);
            assertSame(application, applicationTemplate.getJdbcTemplate().getDataSource());
            assertSame(ai, aiTemplate.getJdbcTemplate().getDataSource());
            assertNotSame(applicationTemplate.getJdbcTemplate().getDataSource(),
                    aiTemplate.getJdbcTemplate().getDataSource());
        }
    }

    @Test
    void invalidLimitsFailFast() {
        try (HikariDataSource application = new HikariDataSource()) {
            assertThrows(IllegalArgumentException.class,
                    () -> configuration.developmentAiToolJdbcTemplate(application, 0, 500));
            assertThrows(IllegalArgumentException.class,
                    () -> configuration.developmentAiToolJdbcTemplate(application, 15, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> configuration.developmentAiToolJdbcTemplate(application, -1, 500));
        }
    }

    @Test
    void primaryDataSourceUsesApplicationConnectionProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://localhost:5432/autoparts_erp");
        properties.setUsername("app_user");
        properties.setPassword("test-only");
        properties.setDriverClassName("org.postgresql.Driver");
        try (HikariDataSource application = configuration.applicationDataSource(properties)) {
            assertEquals("jdbc:postgresql://localhost:5432/autoparts_erp", application.getJdbcUrl());
            assertEquals("app_user", application.getUsername());
        }
    }
}
