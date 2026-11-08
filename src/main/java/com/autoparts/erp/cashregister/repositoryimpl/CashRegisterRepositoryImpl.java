package com.autoparts.erp.cashregister.repositoryimpl;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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

    private final JdbcTemplate jdbcTemplate;

    public CashRegisterRepositoryImpl(JdbcTemplate jdbcTemplate) {
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
    public CashRegister createRegister(CashRegister register) {

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO cash_registers (
                uuid, register_code, name, description
            )
            VALUES (?, ?, ?, ?)
            RETURNING *
            """,
            registerMapper,
            register.uuid(),
            register.registerCode(),
            register.name(),
            register.description()
        );
    }

    @Override
    public Optional<CashRegister> findRegisterByUuid(UUID uuid) {

        return jdbcTemplate.query(
            """
            SELECT *
            FROM cash_registers
            WHERE uuid = ?
            """,
            registerMapper,
            uuid
        ).stream().findFirst();
    }

    @Override
    public List<CashRegister> findAllRegisters() {

        return jdbcTemplate.query(
            """
            SELECT *
            FROM cash_registers
            ORDER BY created_at DESC
            """,
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

        return jdbcTemplate.query(
            SESSION_SELECT + """
                WHERE r.uuid = ?
                  AND s.status = 'OPEN'
                FOR UPDATE OF s
                """,
            sessionMapper,
            registerUuid
        ).stream().findFirst();
    }

    @Override
    public CashRegisterSession openSession(
        UUID sessionUuid,
        Long registerId,
        Long userId,
        BigDecimal openingBalance
    ) {

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO cash_register_sessions (
                uuid,
                cash_register_id,
                opened_by,
                opening_balance
            )
            VALUES (?, ?, ?, ?)
            RETURNING
                id, uuid, cash_register_id, opened_by,
                opened_at, opening_balance, closed_by,
                closed_at, expected_closing_balance,
                actual_closing_balance, difference, status,
                (SELECT name FROM cash_registers WHERE id = cash_register_id)
                    AS register_name
            """,
            sessionMapper,
            sessionUuid,
            registerId,
            userId,
            openingBalance
        );
    }

    @Override
    public Optional<CashRegisterSession> findSessionByUuid(UUID uuid) {

        return jdbcTemplate.query(
            SESSION_SELECT + " WHERE s.uuid = ?",
            sessionMapper,
            uuid
        ).stream().findFirst();
    }

    @Override
    public Optional<CashRegisterSession> findSessionByUuidForUpdate(
        UUID uuid
    ) {

        return jdbcTemplate.query(
            SESSION_SELECT + """
                WHERE s.uuid = ?
                FOR UPDATE OF s
                """,
            sessionMapper,
            uuid
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

        return jdbcTemplate.update(
            """
            UPDATE cash_register_sessions
            SET status = 'CLOSED',
                closed_by = ?,
                closed_at = CURRENT_TIMESTAMP,
                expected_closing_balance = ?,
                actual_closing_balance = ?,
                differenceAmount = ?
            WHERE id = ?
              AND status = 'OPEN'
            """,
            userId,
            expectedBalance,
            actualBalance,
            differenceAmount,
            sessionId
        ) == 1;
    }

    @Override
    public CashRegisterMovement createMovement(
        CashRegisterMovement movement
    ) {

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
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
            """,
            movementMapper,
            movement.uuid(),
            movement.sessionId(),
            movement.movementType().name(),
            movement.sourceType().name(),
            movement.sourceUuid(),
            movement.amount(),
            movement.description(),
            movement.createdBy()
        );
    }

    @Override
    public boolean existsMovement(String sourceType, UUID sourceUuid) {

        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM cash_register_movements
                WHERE source_type = ?
                  AND source_uuid = ?
            )
            """,
            Boolean.class,
            sourceType,
            sourceUuid
        );

        return Boolean.TRUE.equals(exists);
    }

    @Override
    public List<CashRegisterMovement> findMovements(Long sessionId) {

        return jdbcTemplate.query(
            """
            SELECT *
            FROM cash_register_movements
            WHERE session_id = ?
            ORDER BY created_at, id
            """,
            movementMapper,
            sessionId
        );
    }

    @Override
    public BigDecimal calculateTotalByType(
        Long sessionId,
        String movementType
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(amount), 0)
            FROM cash_register_movements
            WHERE session_id = ?
              AND movement_type = ?
            """,
            BigDecimal.class,
            sessionId,
            movementType
        );
    }

    @Override
    public Optional<CashRegisterSession> findLatestSession(
        UUID registerUuid
    ) {

        return jdbcTemplate.query(
            SESSION_SELECT + """
                WHERE r.uuid = ?
                ORDER BY s.opened_at DESC
                LIMIT 1
                """,
            sessionMapper,
            registerUuid
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
				WHERE s.opened_by = ?
				  AND s.status = 'OPEN'
				ORDER BY s.opened_at DESC
				LIMIT 1
				FOR UPDATE OF s
				            """;

        List<CashRegisterSession> sessions = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> mapSession(rs),
            userId
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
            WHERE source_type = ?
              AND source_uuid = ?
            LIMIT 1
            """;

        List<CashRegisterMovement> movements = jdbcTemplate.query(
            sql,
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
            ),
            sourceType,
            sourceUuid
        );

        return movements.stream().findFirst();
    }
}