package com.autoparts.erp.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;
import java.util.Set;

import com.autoparts.erp.ai.rag.retrieval.PostgresFtsRetrievalEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiToolJdbcReaderIntegrationTest {

    @Test
    void readerDataSourceHasExpectedIdentityAndReadOnlyLimits() {
        String url = System.getenv("AI_TOOL_DB_URL");
        String username = System.getenv("AI_TOOL_DB_USERNAME");
        String password = System.getenv("AI_TOOL_DB_PASSWORD");
        Assumptions.assumeTrue(
                url != null && !url.isBlank()
                        && username != null && !username.isBlank()
                        && password != null && !password.isBlank(),
                "AI reader credentials are not configured"
        );

        AiToolJdbcConfiguration configuration = new AiToolJdbcConfiguration();
        try (HikariDataSource reader = configuration.developmentAiToolDataSource(
                url, username, password)) {
            JdbcTemplate template = configuration.developmentAiToolJdbcTemplate(reader, 15, 500);
            Map<String, Object> identity = template.queryForMap("""
                    SELECT
                        current_user,
                        session_user,
                        current_database(),
                        current_setting('transaction_read_only') AS transaction_read_only
                    """);

            assertEquals("autoparts_ai_reader", identity.get("current_user"));
            assertEquals("autoparts_ai_reader", identity.get("session_user"));
            assertEquals("autoparts_erp", identity.get("current_database"));
            assertEquals("on", identity.get("transaction_read_only"));
            assertEquals(true, reader.isReadOnly());

            template.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_documents",
                    Integer.class
            );
            PostgresFtsRetrievalEngine fts = new PostgresFtsRetrievalEngine(
                    new NamedParameterJdbcTemplate(reader)
            );
            fts.retrieve("spark plug ignition coil", Set.of("PUBLIC"), 5);
            assertThrows(
                    org.springframework.dao.DataAccessException.class,
                    () -> template.queryForList(
                            "SELECT id FROM ai_conversations LIMIT 1"
                    )
            );
        }
    }
}
