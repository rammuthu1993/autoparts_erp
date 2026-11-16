
package com.autoparts.erp.ai.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class AiToolJdbcConfiguration {

    private static final int QUERY_TIMEOUT_SECONDS = 30;

    @Bean("aiToolJdbcTemplate")
    public NamedParameterJdbcTemplate aiToolJdbcTemplate(
            DataSource dataSource
    ) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // Preserve existing JDBC configuration
        jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

        // Wrap JdbcTemplate to support named SQL parameters
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }
}
