
package com.autoparts.erp.ai.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class AiToolJdbcConfiguration {

    private static final int QUERY_TIMEOUT_SECONDS = 30;

    @Bean("aiToolNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate aiToolNamedParameterJdbcTemplate(
            DataSource dataSource
    ) {
        NamedParameterJdbcTemplate jdbcTemplate =
                new NamedParameterJdbcTemplate(dataSource);

        jdbcTemplate.getJdbcTemplate()
                .setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return jdbcTemplate;
    }
}
