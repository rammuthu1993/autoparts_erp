package com.autoparts.erp.cashregister.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.autoparts.erp.cashregister.model.CashRegister;
import com.autoparts.erp.cashregister.model.CashRegisterMovement;
import com.autoparts.erp.cashregister.model.CashRegisterSession;

public interface CashRegisterRepository {

    CashRegister createRegister(CashRegister register);

    Optional<CashRegister> findRegisterByUuid(UUID uuid);

    List<CashRegister> findAllRegisters();

    Optional<CashRegisterSession> findOpenSessionForUpdate(UUID registerUuid);

    CashRegisterSession openSession(
        UUID sessionUuid,
        Long registerId,
        Long userId,
        BigDecimal openingBalance
    );

    Optional<CashRegisterSession> findSessionByUuid(UUID sessionUuid);

    Optional<CashRegisterSession> findSessionByUuidForUpdate(UUID sessionUuid);

    boolean closeSession(
        Long sessionId,
        Long userId,
        BigDecimal expectedBalance,
        BigDecimal actualBalance,
        BigDecimal difference
    );

    CashRegisterMovement createMovement(CashRegisterMovement movement);

    boolean existsMovement(
        String sourceType,
        UUID sourceUuid
    );

    List<CashRegisterMovement> findMovements(Long sessionId);

    BigDecimal calculateTotalByType(
        Long sessionId,
        String movementType
    );

    Optional<CashRegisterSession> findLatestSession(UUID registerUuid);
    
    Optional<CashRegisterSession> findOpenSessionForUserUpdate(Long userId);

    Optional<CashRegisterMovement> findMovementBySource(
        String sourceType,
        UUID sourceUuid
    );
}