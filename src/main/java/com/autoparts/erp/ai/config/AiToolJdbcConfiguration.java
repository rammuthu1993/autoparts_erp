
package com.autoparts.erp.ai.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
public class AiToolJdbcConfiguration {

    /**
     * Main application DataSource.
     *
     * Must remain the primary DataSource so that:
     * - Flyway uses the application database connection.
     * - Application repositories use the normal application connection.
     * - AI tools can use a separate connection in production.
     */
    @Bean(name = "dataSource", destroyMethod = "close")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource applicationDataSource(
            DataSourceProperties properties) {

        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * Production-only AI DataSource.
     *
     * This must use a dedicated PostgreSQL account with
     * database-level SELECT-only permissions.
     */
    @Bean(name = "aiToolDataSource", destroyMethod = "close")
    @Profile("prod")
    public HikariDataSource productionAiToolDataSource(
            @Value("${app.ai.datasource.url}") String url,
            @Value("${app.ai.datasource.username}") String username,
            @Value("${app.ai.datasource.password}") String password) {

        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName("org.postgresql.Driver")
                .url(url)
                .username(username)
                .password(password)
                .build();

        dataSource.setPoolName("ai-tool-readonly-pool");
        dataSource.setMaximumPoolSize(3);
        dataSource.setMinimumIdle(0);
        dataSource.setConnectionTimeout(5_000);
        dataSource.setValidationTimeout(3_000);
        dataSource.setMaxLifetime(1_500_000);
        dataSource.setReadOnly(true);

        return dataSource;
    }

    /**
     * Development:
     * Reuse the primary application DataSource directly.
     *
     * IMPORTANT:
     * Do not create an aiToolDataSource alias in development.
     */
    @Bean(name = "aiToolJdbcTemplate")
    @Profile("!prod")
    public JdbcTemplate developmentAiToolJdbcTemplate(
            @Qualifier("dataSource") DataSource applicationDataSource,
            @Value("${app.ai.datasource.query-timeout-seconds:15}") int queryTimeoutSeconds,
            @Value("${app.ai.datasource.max-rows:500}") int maxRows) {

        return createAiToolJdbcTemplate(applicationDataSource, queryTimeoutSeconds, maxRows);
    }

    /**
     * Production:
     * AI lookup tools use the dedicated read-only DataSource.
     */
    @Bean(name = "aiToolJdbcTemplate")
    @Profile("prod")
    public JdbcTemplate productionAiToolJdbcTemplate(
            @Qualifier("aiToolDataSource") DataSource aiToolDataSource,
            @Value("${app.ai.datasource.query-timeout-seconds:15}") int queryTimeoutSeconds,
            @Value("${app.ai.datasource.max-rows:500}") int maxRows) {

        return createAiToolJdbcTemplate(aiToolDataSource, queryTimeoutSeconds, maxRows);
    }

    private JdbcTemplate createAiToolJdbcTemplate(
            DataSource dataSource, int queryTimeoutSeconds, int maxRows) {

        if (queryTimeoutSeconds < 1) {
            throw new IllegalArgumentException("AI tool query timeout must be at least 1 second");
        }
        if (maxRows < 1) {
            throw new IllegalArgumentException("AI tool max rows must be at least 1");
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(queryTimeoutSeconds);
        jdbcTemplate.setMaxRows(maxRows);
        return jdbcTemplate;
    }
}
