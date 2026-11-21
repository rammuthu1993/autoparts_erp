package com.autoparts.erp.ai.tools;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresQueryTimeoutIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCancelPostgresQueryWhenQueryTimeoutIsExceeded() {

        // Use the real PostgreSQL DataSource and a dedicated short timeout.
        jdbcTemplate.setQueryTimeout(1);

        long startTime = System.nanoTime();

        DataAccessException exception = assertThrows(
                DataAccessException.class,
                () -> jdbcTemplate.queryForObject(
                        "SELECT 1 FROM pg_sleep(5)",
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

            if (current instanceof SQLException sqlException) {

                SQLException sql = sqlException;

                while (sql != null) {
                    if (expectedState.equals(sql.getSQLState())) {
                        return true;
                    }
                    sql = sql.getNextException();
                }
            }

            current = current.getCause();
        }

        return false;
    }
}