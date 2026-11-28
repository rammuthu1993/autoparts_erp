package com.autoparts.erp.cashregister.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.autoparts.erp.cashregister.model.CashRegister;
import com.autoparts.erp.cashregister.model.CashRegisterMovement;
import com.autoparts.erp.cashregister.model.CashRegisterSession;

public interface CashRegisterRepository {

    void lockRegisterSetup();

    long countActiveRegisters();

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
        BigDecimal differenceAmount
    );

    CashRegisterMovement createMovement(CashRegisterMovement movement);

    boolean existsMovement(
        String sourceType,
        UUID sourceUuid
    );

    List<CashRegisterMovement> findMovements(Long sessionId);

    List<CashRegisterMovement> findMovementsPaged(
        Long sessionId,
        int offset,
        int limit,
        String sortBy,
        String sortDir
    );

    long countMovements(Long sessionId);

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

    void updateSessionOpeningNotes(Long sessionId, String openingNotes);

    void updateSessionClosingMetadata(
        Long sessionId,
        int d500, int d200, int d100, int d50, int d20, int d10,
        BigDecimal coinsAmount,
        boolean denominationsUsed,
        String differenceReason
    );

    List<CashRegisterSession> findSessionsPaged(
        UUID registerUuid,
        String status,
        java.time.LocalDate fromDate,
        java.time.LocalDate toDate,
        int offset,
        int limit
    );

    long countSessionsPaged(
        UUID registerUuid,
        String status,
        java.time.LocalDate fromDate,
        java.time.LocalDate toDate
    );

    Optional<CashRegisterSession> findOpenSessionForUser(Long userId);
}