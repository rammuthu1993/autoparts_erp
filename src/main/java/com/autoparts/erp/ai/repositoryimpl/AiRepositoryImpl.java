package com.autoparts.erp.ai.repositoryimpl;

import com.autoparts.erp.ai.dto.AiConversationResponse;
import com.autoparts.erp.ai.dto.AiMessageResponse;
import com.autoparts.erp.ai.repository.AiRepository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.autoparts.erp.ai.enums.AiAgentRunStatus;
import com.autoparts.erp.ai.model.AiAgentRun;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class AiRepositoryImpl implements AiRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AiRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void createConversation(
            UUID id,
            String userSubject,
            String title
    ) {
        String sql = """
                INSERT INTO ai_conversations
                    (id, user_subject, title)
                VALUES
                    (:id, :userSubject, :title)
                """;

        jdbc.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userSubject", userSubject)
                        .addValue("title", title)
        );
    }

    @Override
    public boolean conversationExists(
            UUID id,
            String userSubject
    ) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM ai_conversations
                    WHERE id = :id
                      AND user_subject = :userSubject
                      AND active = TRUE
                )
                """;

        Boolean exists = jdbc.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userSubject", userSubject),
                Boolean.class
        );

        return Boolean.TRUE.equals(exists);
    }

    // ---------------------------------------------
    // UPDATE CONVERSATION TITLE
    // ---------------------------------------------

    @Override
    public int updateConversationTitle(
            UUID id,
            String userSubject,
            String title
    ) {
        String sql = """
                UPDATE ai_conversations
                SET title = :title,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND user_subject = :userSubject
                  AND active = TRUE
                """;

        return jdbc.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userSubject", userSubject)
                        .addValue("title", title)
        );
    }

    // ---------------------------------------------
    // GET SINGLE CONVERSATION
    // ---------------------------------------------

    @Override
    public AiConversationResponse getConversationById(
            UUID id,
            String userSubject
    ) {
        String sql = """
                SELECT
                    id,
                    title,
                    created_at,
                    updated_at
                FROM ai_conversations
                WHERE id = :id
                  AND user_subject = :userSubject
                  AND active = TRUE
                """;

        List<AiConversationResponse> results = jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userSubject", userSubject),
                (rs, rowNum) -> new AiConversationResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getObject(
                                "created_at",
                                OffsetDateTime.class
                        ),
                        rs.getObject(
                                "updated_at",
                                OffsetDateTime.class
                        )
                )
        );

        return results.isEmpty() ? null : results.get(0);
    }

    // ---------------------------------------------
    // SAVE MESSAGE
    // ---------------------------------------------

    @Override
    @Transactional
    public void saveMessage(
            UUID conversationId,
            String role,
            String content
    ) {
        String insertSql = """
                INSERT INTO ai_messages
                    (conversation_id, role, content)
                VALUES
                    (:conversationId, :role, :content)
                """;

        jdbc.update(
                insertSql,
                new MapSqlParameterSource()
                        .addValue("conversationId", conversationId)
                        .addValue("role", role)
                        .addValue("content", content)
        );

        String updateSql = """
                UPDATE ai_conversations
                SET updated_at = CURRENT_TIMESTAMP
                WHERE id = :conversationId
                  AND active = TRUE
                """;

        jdbc.update(
                updateSql,
                new MapSqlParameterSource()
                        .addValue("conversationId", conversationId)
        );
    }

    // ---------------------------------------------
    // GET LATEST 20 MESSAGES FOR AI CONTEXT
    // ---------------------------------------------

    @Override
    public List<Map<String, Object>> getMessages(
            UUID conversationId
    ) {
        String sql = """
                SELECT role, content
                FROM (
                    SELECT role, content, created_at, id
                    FROM ai_messages
                    WHERE conversation_id = :conversationId
                    ORDER BY created_at DESC, id DESC
                    LIMIT 20
                ) recent_messages
                ORDER BY created_at ASC, id ASC
                """;

        return jdbc.queryForList(
                sql,
                new MapSqlParameterSource()
                        .addValue("conversationId", conversationId)
        );
    }

    // ---------------------------------------------
    // GET ALL ACTIVE CONVERSATIONS FOR USER
    // ---------------------------------------------

    @Override
    public List<AiConversationResponse> getConversations(
            String userSubject
    ) {
        String sql = """
                SELECT
                    id,
                    title,
                    created_at,
                    updated_at
                FROM ai_conversations
                WHERE user_subject = :userSubject
                  AND active = TRUE
                ORDER BY updated_at DESC
                """;

        return jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("userSubject", userSubject),
                (rs, rowNum) -> new AiConversationResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getObject(
                                "created_at",
                                OffsetDateTime.class
                        ),
                        rs.getObject(
                                "updated_at",
                                OffsetDateTime.class
                        )
                )
        );
    }

    // ---------------------------------------------
    // SAVE TOOL RUN
    // ---------------------------------------------

    @Override
    public void saveToolRun(
            UUID conversationId,
            String userSubject,
            String toolName,
            String arguments,
            String result,
            String status,
            String errorMessage
    ) {
        String sql = """
                INSERT INTO ai_tool_runs (
                    conversation_id,
                    user_subject,
                    tool_name,
                    arguments,
                    result,
                    status,
                    error_message
                )
                VALUES (
                    :conversationId,
                    :userSubject,
                    :toolName,
                    CAST(:arguments AS jsonb),
                    CAST(:result AS jsonb),
                    :status,
                    :errorMessage
                )
                """;

        jdbc.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("conversationId", conversationId)
                        .addValue("userSubject", userSubject)
                        .addValue("toolName", toolName)
                        .addValue("arguments", arguments)
                        .addValue("result", result)
                        .addValue("status", status)
                        .addValue("errorMessage", errorMessage)
        );
    }

    // ---------------------------------------------
    // GET COMPLETE CONVERSATION MESSAGE HISTORY
    // ---------------------------------------------

    @Override
    public List<AiMessageResponse> getConversationMessages(
            UUID conversationId
    ) {
        String sql = """
                SELECT
                    id,
                    role,
                    content,
                    created_at
                FROM ai_messages
                WHERE conversation_id = :conversationId
                ORDER BY created_at ASC, id ASC
                """;

        return jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("conversationId", conversationId),
                (rs, rowNum) -> new AiMessageResponse(
                        rs.getLong("id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                )
        );
    }
    
    @Override
    public int archiveConversation(
            UUID id,
            String userSubject
    ) {

        String sql = """
                UPDATE ai_conversations
                SET active = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND user_subject = :userSubject
                  AND active = TRUE
                """;

        return jdbc.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userSubject", userSubject)
        );
    }
    
    @Override
    public UUID createAgentRun(UUID conversationId, String userSubject) {

        UUID runId = UUID.randomUUID();

        String sql = """
                INSERT INTO ai_agent_runs (
                    id,
                    conversation_id,
                    user_subject,
                    status,
                    attempt_count,
                    started_at
                )
                VALUES (
                    :id,
                    :conversationId,
                    :userSubject,
                    :status,
                    :attemptCount,
                    CURRENT_TIMESTAMP
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", runId)
                .addValue("conversationId", conversationId)
                .addValue("userSubject", userSubject)
                .addValue("status", AiAgentRunStatus.RUNNING.name())
                .addValue("attemptCount", 1);

        jdbc.update(sql, params);

        return runId;
    }
    
    @Override
    public int updateAgentRunStatus(
            UUID runId,
            AiAgentRunStatus status,
            String errorMessage
    ) {

        String sql = """
                UPDATE ai_agent_runs
                SET
                    status = :status,
                    error_message = :errorMessage,
                    completed_at = CASE
                        WHEN :status IN ('COMPLETED', 'FAILED', 'CANCELLED')
                        THEN CURRENT_TIMESTAMP
                        ELSE completed_at
                    END
                WHERE id = :runId
                  AND status = 'RUNNING'
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("status", status.name())
                .addValue("errorMessage", errorMessage);

        return jdbc.update(sql, params);
    }
    
    @Override
    public AiAgentRun getAgentRun(UUID runId) {

        String sql = """
                SELECT
                    id,
                    conversation_id,
                    user_subject,
                    status,
                    attempt_count,
                    error_message,
                    started_at,
                    completed_at
                FROM ai_agent_runs
                WHERE id = :runId
                """;

        List<AiAgentRun> runs = jdbc.query(
                sql,
                new MapSqlParameterSource("runId", runId),
                (rs, rowNum) -> mapAgentRun(rs)
        );

        return runs.isEmpty() ? null : runs.get(0);
    }

    private AiAgentRun mapAgentRun(ResultSet rs) throws SQLException {

        return new AiAgentRun(
                rs.getObject("id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                rs.getString("user_subject"),
                AiAgentRunStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                rs.getString("error_message"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class)
        );
    }
}