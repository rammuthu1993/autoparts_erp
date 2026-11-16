
package com.autoparts.erp.ai.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AiToolJdbcConfiguration {

    private static final int QUERY_TIMEOUT_SECONDS = 30;

    @Bean("aiToolJdbcTemplate")
    public JdbcTemplate aiToolJdbcTemplate(DataSource dataSource) {

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

        return jdbcTemplate;
    }
}
