
package com.autoparts.erp.ai.tools;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class PostgresQueryTimeoutIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldCancelPostgresQueryWhenQueryTimeoutIsExceeded() {

        // Dedicated test template with a short timeout.
        NamedParameterJdbcTemplate jdbcTemplate =
                new NamedParameterJdbcTemplate(dataSource);
        jdbcTemplate.getJdbcTemplate().setQueryTimeout(1);

        long startTime = System.nanoTime();

        DataAccessException exception = assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.queryForObject(
                        "SELECT 1 FROM pg_sleep(5)",
                        new MapSqlParameterSource(),
                        Integer.class
                )
        );

        long durationMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startTime
        );

        assertTrue(
                hasSqlState(exception, "57014"),
                "Expected PostgreSQL query cancellation SQL state 57014"
        );

        assertTrue(
                durationMs < 4500,
                "Query should be cancelled before the five-second sleep completes. "
                        + "Actual duration: " + durationMs + " ms"
        );
    }

    private boolean hasSqlState(Throwable throwable, String expectedState) {

        Throwable current = throwable;

        while (current != null) {

            if (current instanceof SQLException sqlException
                    && expectedState.equals(sqlException.getSQLState())) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
