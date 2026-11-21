package com.autoparts.erp.ai.config;

import com.autoparts.erp.ai.repositoryimpl.AiRepositoryImpl;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiPrimaryJdbcPersistenceIntegrationTest {

    @Test
    void primaryRepositoryPersistsChatRecordsAndRollbackPreservesExistingData() {
        String url = System.getenv("DB_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        Assumptions.assumeTrue(
                url != null && !url.isBlank()
                        && username != null && !username.isBlank()
                        && password != null && !password.isBlank(),
                "Primary database credentials are not configured"
        );

        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl(url);
        properties.setUsername(username);
        properties.setPassword(password);
        properties.setDriverClassName("org.postgresql.Driver");

        AiToolJdbcConfiguration configuration = new AiToolJdbcConfiguration();
        try (HikariDataSource primary = configuration.applicationDataSource(properties)) {
            NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(primary);
            AiRepositoryImpl repository = new AiRepositoryImpl(template);
            TransactionTemplate transaction = new TransactionTemplate(
                    new DataSourceTransactionManager(primary)
            );

            Integer conversationsBefore = template.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM ai_conversations", Integer.class
            );
            Integer messagesBefore = template.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM ai_messages", Integer.class
            );
            Integer agentRunsBefore = template.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM ai_agent_runs", Integer.class
            );

            UUID conversationId = UUID.randomUUID();
            transaction.executeWithoutResult(status -> {
                repository.createConversation(
                        conversationId,
                        "integration-test-user",
                        "Phase 5 rollback fixture"
                );
                repository.saveMessage(conversationId, "USER", "integration-test-message");
                UUID agentRunId = repository.createAgentRun(
                        conversationId,
                        "integration-test-user"
                );
                assertNotNull(agentRunId);

                assertEquals(conversationsBefore + 1, template.getJdbcTemplate().queryForObject(
                        "SELECT COUNT(*) FROM ai_conversations", Integer.class
                ));
                assertEquals(messagesBefore + 1, template.getJdbcTemplate().queryForObject(
                        "SELECT COUNT(*) FROM ai_messages", Integer.class
                ));
                assertEquals(agentRunsBefore + 1, template.getJdbcTemplate().queryForObject(
                        "SELECT COUNT(*) FROM ai_agent_runs", Integer.class
                ));
                status.setRollbackOnly();
            });

            assertEquals(conversationsBefore, template.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM ai_conversations", Integer.class
            ));
            assertEquals(messagesBefore, template.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM ai_messages", Integer.class
            ));
            assertEquals(agentRunsBefore, template.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM ai_agent_runs", Integer.class
            ));
        }
    }
}
