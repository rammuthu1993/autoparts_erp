package com.autoparts.erp.cashregister.repositoryimpl;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.enums.CashSessionStatus;
import com.autoparts.erp.cashregister.model.CashRegister;
import com.autoparts.erp.cashregister.model.CashRegisterMovement;
import com.autoparts.erp.cashregister.model.CashRegisterSession;
import com.autoparts.erp.cashregister.repository.CashRegisterRepository;

@Repository
public class CashRegisterRepositoryImpl implements CashRegisterRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CashRegisterRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<CashRegister> registerMapper = (rs, rowNum) ->
        new CashRegister(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getString("register_code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBoolean("active"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );

    private final RowMapper<CashRegisterSession> sessionMapper =
        (rs, rowNum) -> new CashRegisterSession(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("cash_register_id"),
            rs.getString("register_name"),
            rs.getLong("opened_by"),
            rs.getObject("opened_at", OffsetDateTime.class),
            rs.getBigDecimal("opening_balance"),
            rs.getObject("closed_by", Long.class),
            rs.getObject("closed_at", OffsetDateTime.class),
            rs.getBigDecimal("expected_closing_balance"),
            rs.getBigDecimal("actual_closing_balance"),
            rs.getBigDecimal("difference_amount"),
            CashSessionStatus.valueOf(rs.getString("status"))
        );

    private final RowMapper<CashRegisterMovement> movementMapper =
        (rs, rowNum) -> new CashRegisterMovement(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("session_id"),
            CashMovementType.valueOf(rs.getString("movement_type")),
            CashMovementSourceType.valueOf(rs.getString("source_type")),
            rs.getObject("source_uuid", UUID.class),
            rs.getBigDecimal("amount"),
            rs.getString("description"),
            rs.getLong("created_by"),
            rs.getObject("created_at", OffsetDateTime.class)
        );

    @Override
    public void lockRegisterSetup() {
        // Serialize initial register setup across concurrent requests.
        jdbcTemplate.getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(781234, 1)");
    }

    @Override
    public long countActiveRegisters() {
        Long count = jdbcTemplate.getJdbcTemplate().queryForObject(
            "SELECT COUNT(*) FROM cash_registers WHERE active = TRUE", Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public CashRegister createRegister(CashRegister register) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", register.uuid())
                .addValue("registerCode", register.registerCode())
                .addValue("name", register.name())
                .addValue("description", register.description());

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO cash_registers (
                uuid, register_code, name, description
            )
            VALUES (:uuid, :registerCode, :name, :description)
            RETURNING *
            """,
            params,
            registerMapper
        );
    }

    @Override
    public Optional<CashRegister> findRegisterByUuid(UUID uuid) {
        MapSqlParameterSource params = new MapSqlParameterSource("uuid", uuid);

        return jdbcTemplate.query(
            """
            SELECT *
            FROM cash_registers
            WHERE uuid = :uuid
            """,
            params,
            registerMapper
        ).stream().findFirst();
    }

    @Override
    public List<CashRegister> findAllRegisters() {

        return jdbcTemplate.query(
            """
            SELECT *
            FROM cash_registers
            ORDER BY created_at DESC, id DESC
            LIMIT 50
            """,
            new MapSqlParameterSource(),
            registerMapper
        );
    }

    private static final String SESSION_SELECT = """
        SELECT
            s.*,
            r.name AS register_name
        FROM cash_register_sessions s
        JOIN cash_registers r
            ON r.id = s.cash_register_id
        """;

    @Override
    public Optional<CashRegisterSession> findOpenSessionForUpdate(
        UUID registerUuid
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource("registerUuid", registerUuid);

        return jdbcTemplate.query(
            SESSION_SELECT + """
                WHERE r.uuid = :registerUuid
                  AND s.status = 'OPEN'
                FOR UPDATE OF s
                """,
            params,
            sessionMapper
        ).stream().findFirst();
    }

    @Override
    public CashRegisterSession openSession(
        UUID sessionUuid,
        Long registerId,
        Long userId,
        BigDecimal openingBalance
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sessionUuid", sessionUuid)
                .addValue("registerId", registerId)
                .addValue("userId", userId)
                .addValue("openingBalance", openingBalance);

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO cash_register_sessions (
                uuid,
                cash_register_id,
                opened_by,
                opening_balance
            )
            VALUES (:sessionUuid, :registerId, :userId, :openingBalance)
            RETURNING
                id, uuid, cash_register_id, opened_by,
                opened_at, opening_balance, closed_by,
                closed_at, expected_closing_balance,
                actual_closing_balance, difference_amount, status,
                (SELECT name FROM cash_registers WHERE id = cash_register_id)
                    AS register_name
            """,
            params,
            sessionMapper
        );
    }

    @Override
    public Optional<CashRegisterSession> findSessionByUuid(UUID uuid) {
        MapSqlParameterSource params = new MapSqlParameterSource("uuid", uuid);

        return jdbcTemplate.query(
            SESSION_SELECT + " WHERE s.uuid = :uuid",
            params,
            sessionMapper
        ).stream().findFirst();
    }

    @Override
    public Optional<CashRegisterSession> findSessionByUuidForUpdate(
        UUID uuid
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource("uuid", uuid);

        return jdbcTemplate.query(
            SESSION_SELECT + """
                WHERE s.uuid = :uuid
                FOR UPDATE OF s
                """,
            params,
            sessionMapper
        ).stream().findFirst();
    }

    @Override
    public boolean closeSession(
        Long sessionId,
        Long userId,
        BigDecimal expectedBalance,
        BigDecimal actualBalance,
        BigDecimal differenceAmount
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("expectedBalance", expectedBalance)
                .addValue("actualBalance", actualBalance)
                .addValue("differenceAmount", differenceAmount)
                .addValue("sessionId", sessionId);

        return jdbcTemplate.update(
            """
            UPDATE cash_register_sessions
            SET status = 'CLOSED',
                closed_by = :userId,
                closed_at = CURRENT_TIMESTAMP,
                expected_closing_balance = :expectedBalance,
                actual_closing_balance = :actualBalance,
                difference_amount = :differenceAmount
            WHERE id = :sessionId
              AND status = 'OPEN'
            """,
            params
        ) == 1;
    }

    @Override
    public CashRegisterMovement createMovement(
        CashRegisterMovement movement
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("uuid", movement.uuid())
                .addValue("sessionId", movement.sessionId())
                .addValue("movementType", movement.movementType().name())
                .addValue("sourceType", movement.sourceType().name())
                .addValue("sourceUuid", movement.sourceUuid())
                .addValue("amount", movement.amount())
                .addValue("description", movement.description())
                .addValue("createdBy", movement.createdBy());

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO cash_register_movements (
                uuid,
                session_id,
                movement_type,
                source_type,
                source_uuid,
                amount,
                description,
                created_by
            )
            VALUES (:uuid, :sessionId, :movementType, :sourceType, :sourceUuid, :amount, :description, :createdBy)
            RETURNING *
            """,
            params,
            movementMapper
        );
    }

    @Override
    public boolean existsMovement(String sourceType, UUID sourceUuid) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceType", sourceType)
                .addValue("sourceUuid", sourceUuid);

        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM cash_register_movements
                WHERE source_type = :sourceType
                  AND source_uuid = :sourceUuid
            )
            """,
            params,
            Boolean.class
        );

        return Boolean.TRUE.equals(exists);
    }

    private static final Map<String, String> CASH_MOVEMENT_SORT_ALLOWLIST = Map.of(
            "id", "id",
            "createdAt", "created_at",
            "amount", "amount",
            "movementType", "movement_type"
    );

    @Override
    public List<CashRegisterMovement> findMovements(Long sessionId) {
        MapSqlParameterSource params = new MapSqlParameterSource("sessionId", sessionId);

        return jdbcTemplate.query(
            """
            SELECT *
            FROM cash_register_movements
            WHERE session_id = :sessionId
            ORDER BY created_at, id
            LIMIT 501
            """,
            params,
            movementMapper
        );
    }

    @Override
    public List<CashRegisterMovement> findMovementsPaged(
            Long sessionId,
            int offset,
            int limit,
            String sortBy,
            String sortDir
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT *
            FROM cash_register_movements
            WHERE session_id = :sessionId
            """);
        MapSqlParameterSource params = new MapSqlParameterSource("sessionId", sessionId);

        String sortColumn = CASH_MOVEMENT_SORT_ALLOWLIST.getOrDefault(sortBy, "created_at");
        String direction = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"id".equals(sortColumn)) {
            sql.append(", id DESC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, movementMapper);
    }

    @Override
    public long countMovements(Long sessionId) {
        String sql = "SELECT COUNT(*) FROM cash_register_movements WHERE session_id = :sessionId";
        Long count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("sessionId", sessionId), Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public BigDecimal calculateTotalByType(
        Long sessionId,
        String movementType
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("movementType", movementType);

        return jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(amount), 0)
            FROM cash_register_movements
            WHERE session_id = :sessionId
              AND movement_type = :movementType
            """,
            params,
            BigDecimal.class
        );
    }

    @Override
    public Optional<CashRegisterSession> findLatestSession(
        UUID registerUuid
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource("registerUuid", registerUuid);

        return jdbcTemplate.query(
            SESSION_SELECT + """
                WHERE r.uuid = :registerUuid
                ORDER BY s.opened_at DESC
                LIMIT 1
                """,
            params,
            sessionMapper
        ).stream().findFirst();
    }
    
    @Override
    public Optional<CashRegisterSession> findOpenSessionForUserUpdate(
        Long userId
    ) {

        String sql = """
            SELECT
                s.id,
                s.uuid,
                s.cash_register_id,
                r.name AS register_name,
                s.opened_by,
                s.opened_at,
                s.opening_balance,
                s.closed_by,
                s.closed_at,
                s.expected_closing_balance,
                s.actual_closing_balance,
                s.difference_amount,
                s.status
            FROM cash_register_sessions s
            JOIN cash_registers r
                ON r.id = s.cash_register_id
            WHERE s.opened_by = :userId
              AND s.status = 'OPEN'
            ORDER BY s.opened_at DESC
            LIMIT 1
            FOR UPDATE OF s
            """;

        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);

        List<CashRegisterSession> sessions = jdbcTemplate.query(
            sql,
            params,
            (rs, rowNum) -> mapSession(rs)
        );

        return sessions.stream().findFirst();
    }
    
    private CashRegisterSession mapSession(ResultSet rs)
            throws SQLException {

        return new CashRegisterSession(
            rs.getObject("id", Long.class),
            rs.getObject("uuid", UUID.class),
            rs.getObject("cash_register_id", Long.class),
            rs.getString("register_name"),
            rs.getObject("opened_by", Long.class),
            rs.getObject("opened_at", OffsetDateTime.class),
            rs.getBigDecimal("opening_balance"),
            rs.getObject("closed_by", Long.class),
            rs.getObject("closed_at", OffsetDateTime.class),
            rs.getBigDecimal("expected_closing_balance"),
            rs.getBigDecimal("actual_closing_balance"),
            rs.getBigDecimal("difference_amount"),
            CashSessionStatus.valueOf(rs.getString("status"))
        );
    }
    
    private CashRegisterMovement mapMovement(ResultSet rs)
            throws SQLException {

        return new CashRegisterMovement(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("session_id"),
            CashMovementType.valueOf(rs.getString("movement_type")),
            CashMovementSourceType.valueOf(rs.getString("source_type")),
            rs.getObject("source_uuid", UUID.class),
            rs.getBigDecimal("amount"),
            rs.getString("description"),
            rs.getLong("created_by"),
            rs.getObject("created_at", OffsetDateTime.class)
        );
    }
    
    @Override
    public Optional<CashRegisterMovement> findMovementBySource(
        String sourceType,
        UUID sourceUuid
    ) {

        String sql = """
            SELECT
                id,
                uuid,
                session_id,
                movement_type,
                source_type,
                source_uuid,
                amount,
                description,
                created_by,
                created_at
            FROM cash_register_movements
            WHERE source_type = :sourceType
              AND source_uuid = :sourceUuid
            LIMIT 1
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceType", sourceType)
                .addValue("sourceUuid", sourceUuid);

        List<CashRegisterMovement> movements = jdbcTemplate.query(
            sql,
            params,
            (rs, rowNum) -> new CashRegisterMovement(
                rs.getObject("id", Long.class),
                rs.getObject("uuid", UUID.class),
                rs.getObject("session_id", Long.class),
                CashMovementType.valueOf(
                    rs.getString("movement_type")
                ),
                CashMovementSourceType.valueOf(
                    rs.getString("source_type")
                ),
                rs.getObject("source_uuid", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getObject("created_by", Long.class),
                rs.getObject("created_at", OffsetDateTime.class)
            )
        );

        return movements.stream().findFirst();
    }

    @Override
    public void updateSessionOpeningNotes(Long sessionId, String openingNotes) {
        jdbcTemplate.update(
            "UPDATE cash_register_sessions SET opening_notes = :openingNotes WHERE id = :sessionId",
            new MapSqlParameterSource().addValue("openingNotes", openingNotes).addValue("sessionId", sessionId)
        );
    }

    @Override
    public void updateSessionClosingMetadata(
            Long sessionId,
            int d500, int d200, int d100, int d50, int d20, int d10,
            BigDecimal coinsAmount,
            boolean denominationsUsed,
            String differenceReason
    ) {
        jdbcTemplate.update(
            """
            UPDATE cash_register_sessions
            SET denomination_500 = :d500,
                denomination_200 = :d200,
                denomination_100 = :d100,
                denomination_50 = :d50,
                denomination_20 = :d20,
                denomination_10 = :d10,
                coins_amount = :coinsAmount,
                denominations_used = :denominationsUsed,
                difference_reason = :differenceReason
            WHERE id = :sessionId
            """,
            new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("d500", d500)
                .addValue("d200", d200)
                .addValue("d100", d100)
                .addValue("d50", d50)
                .addValue("d20", d20)
                .addValue("d10", d10)
                .addValue("coinsAmount", coinsAmount != null ? coinsAmount : BigDecimal.ZERO)
                .addValue("denominationsUsed", denominationsUsed)
                .addValue("differenceReason", differenceReason)
        );
    }

    @Override
    public List<CashRegisterSession> findSessionsPaged(
            UUID registerUuid,
            String status,
            java.time.LocalDate fromDate,
            java.time.LocalDate toDate,
            int offset,
            int limit
    ) {
        StringBuilder sql = new StringBuilder(SESSION_SELECT).append(" WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        applySessionFilters(sql, params, registerUuid, status, fromDate, toDate);
        sql.append(" ORDER BY s.opened_at DESC, s.id DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));
        return jdbcTemplate.query(sql.toString(), params, sessionMapper);
    }

    @Override
    public long countSessionsPaged(
            UUID registerUuid,
            String status,
            java.time.LocalDate fromDate,
            java.time.LocalDate toDate
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM cash_register_sessions s
            JOIN cash_registers r ON r.id = s.cash_register_id
            WHERE 1=1
            """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        applySessionFilters(sql, params, registerUuid, status, fromDate, toDate);
        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void applySessionFilters(
            StringBuilder sql,
            MapSqlParameterSource params,
            UUID registerUuid,
            String status,
            java.time.LocalDate fromDate,
            java.time.LocalDate toDate
    ) {
        if (registerUuid != null) {
            sql.append(" AND r.uuid = :registerUuid");
            params.addValue("registerUuid", registerUuid);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }
        if (fromDate != null) {
            sql.append(" AND s.opened_at >= CAST(:fromDate AS DATE)");
            params.addValue("fromDate", fromDate);
        }
        if (toDate != null) {
            sql.append(" AND s.opened_at < CAST(:toDate AS DATE) + INTERVAL '1 day'");
            params.addValue("toDate", toDate);
        }
    }

    @Override
    public Optional<CashRegisterSession> findOpenSessionForUser(Long userId) {
        return jdbcTemplate.query(
            SESSION_SELECT + """
                WHERE s.opened_by = :userId
                  AND s.status = 'OPEN'
                ORDER BY s.opened_at DESC
                LIMIT 1
                """,
            new MapSqlParameterSource("userId", userId),
            sessionMapper
        ).stream().findFirst();
    }
}
