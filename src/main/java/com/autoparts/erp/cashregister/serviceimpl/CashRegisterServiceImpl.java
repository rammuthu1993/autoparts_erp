
package com.autoparts.erp.cashregister.serviceimpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.cashregister.dto.*;
import com.autoparts.erp.cashregister.enums.*;
import com.autoparts.erp.cashregister.model.*;
import com.autoparts.erp.cashregister.repository.CashRegisterRepository;
import com.autoparts.erp.cashregister.service.CashRegisterService;

@Service
@Transactional(readOnly = true)
public class CashRegisterServiceImpl implements CashRegisterService {

    private static final Logger logger =
            LoggerFactory.getLogger(CashRegisterServiceImpl.class);

    private static final int MONEY_SCALE = 2;

    private final CurrentUserService currentUserService;
    private final CashRegisterRepository cashRegisterRepository;
    private final AuditLogService auditLogService;

    public CashRegisterServiceImpl(
            CurrentUserService currentUserService,
            CashRegisterRepository cashRegisterRepository,
            AuditLogService auditLogService
    ) {
        this.currentUserService = currentUserService;
        this.cashRegisterRepository = cashRegisterRepository;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------
    // CREATE CASH REGISTER
    // ADMIN ONLY
    // ---------------------------------------------------------

    @Override
    @Transactional
    @AdminOnly
    public CashRegisterResponse createRegister(
            CreateCashRegisterRequest request
    ) {

        CashRegister register = new CashRegister(
                null,
                UUID.randomUUID(),
                request.registerCode().trim().toUpperCase(),
                request.name().trim(),
                clean(request.description()),
                true,
                null,
                null
        );

        try {

            CashRegister saved =
                    cashRegisterRepository.createRegister(register);

            Map<String, Object> auditData = new HashMap<>();

            auditData.put("registerUuid", saved.uuid().toString());
            auditData.put("registerCode", saved.registerCode());
            auditData.put("name", saved.name());
            auditData.put("active", saved.active());

            auditLogService.log(
                    "CREATE",
                    "CASH_REGISTER",
                    saved.uuid().toString(),
                    "Cash register created",
                    null,
                    auditData
            );

            return toRegisterResponse(saved);

        } catch (DuplicateKeyException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Register code already exists"
            );
        }
    }

    // ---------------------------------------------------------
    // FIND ALL CASH REGISTERS
    // ADMIN / MANAGER / CASHIER
    // ---------------------------------------------------------

    @Override
    @SalesAccess
    public List<CashRegisterResponse> findAllRegisters() {

        return cashRegisterRepository.findAllRegisters()
                .stream()
                .map(this::toRegisterResponse)
                .toList();
    }

    // ---------------------------------------------------------
    // OPEN CASH REGISTER SESSION
    // ADMIN / MANAGER / CASHIER
    // ---------------------------------------------------------

    @Override
    @Transactional
    @SalesAccess
    public CashSessionResponse openSession(
            UUID registerUuid,
            OpenCashSessionRequest request
    ) {

        CashRegister register = cashRegisterRepository
                .findRegisterByUuid(registerUuid)
                .orElseThrow(() ->
                        notFound("Cash register not found")
                );

        if (!register.active()) {
            throw badRequest("Cash register is inactive");
        }

        if (request.openingBalance() == null ||
                request.openingBalance().compareTo(BigDecimal.ZERO) < 0) {

            throw badRequest("Invalid opening balance");
        }

        if (cashRegisterRepository
                .findOpenSessionForUpdate(registerUuid)
                .isPresent()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This register already has an open session"
            );
        }

        CashRegisterSession session;

        try {

            session = cashRegisterRepository.openSession(
                    UUID.randomUUID(),
                    register.id(),
                    currentUserService.getCurrentUserId(),
                    money(request.openingBalance())
            );

        } catch (DuplicateKeyException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This register already has an open session"
            );
        }

        // Audit opening session.
        auditLogService.log(
                "CREATE",
                "CASH_REGISTER_SESSION",
                session.uuid().toString(),
                "Cash register session opened",
                null,
                Map.of(
                        "sessionUuid", session.uuid().toString(),
                        "registerUuid", registerUuid.toString(),
                        "registerCode", register.registerCode(),
                        "openingBalance", money(request.openingBalance()),
                        "status", session.status().name()
                )
        );

        return toSessionResponse(session);
    }

    // ---------------------------------------------------------
    // CLOSE CASH REGISTER SESSION
    // ADMIN / MANAGER / CASHIER
    // ---------------------------------------------------------

    @Override
    @Transactional
    @SalesAccess
    public CashSessionResponse closeSession(
            UUID sessionUuid,
            CloseCashSessionRequest request
    ) {

        CashRegisterSession session = cashRegisterRepository
                .findSessionByUuidForUpdate(sessionUuid)
                .orElseThrow(() ->
                        notFound("Cash session not found")
                );

        if (session.status() != CashSessionStatus.OPEN) {
            throw badRequest("Cash session is already closed");
        }

        if (request.actualClosingBalance() == null) {
            throw badRequest("Actual closing balance is required");
        }

        BigDecimal cashIn = cashRegisterRepository.calculateTotalByType(
                session.id(),
                CashMovementType.IN.name()
        );

        BigDecimal cashOut = cashRegisterRepository.calculateTotalByType(
                session.id(),
                CashMovementType.OUT.name()
        );

        BigDecimal expected = money(
                session.openingBalance()
                        .add(cashIn)
                        .subtract(cashOut)
        );

        BigDecimal actual = money(request.actualClosingBalance());

        if (actual.compareTo(BigDecimal.ZERO) < 0) {
            throw badRequest("Closing balance cannot be negative");
        }

        BigDecimal difference = money(actual.subtract(expected));

        boolean closed = cashRegisterRepository.closeSession(
                session.id(),
                currentUserService.getCurrentUserId(),
                expected,
                actual,
                difference
        );

        if (!closed) {
            throw badRequest("Unable to close cash session");
        }

        // Audit closing session.
        auditLogService.log(
                "UPDATE",
                "CASH_REGISTER_SESSION",
                session.uuid().toString(),
                "Cash register session closed",
                Map.of(
                        "status", session.status().name(),
                        "openingBalance", session.openingBalance()
                ),
                Map.of(
                        "status", CashSessionStatus.CLOSED.name(),
                        "expectedClosingBalance", expected,
                        "actualClosingBalance", actual,
                        "differenceAmount", difference,
                        "cashIn", money(cashIn),
                        "cashOut", money(cashOut)
                )
        );

        return findLatestSessionForSession(sessionUuid);
    }

    // ---------------------------------------------------------
    // FIND LATEST SESSION
    // ADMIN / MANAGER / CASHIER
    // ---------------------------------------------------------

    @Override
    @SalesAccess
    public CashSessionResponse findLatestSession(UUID registerUuid) {

        CashRegisterSession session = cashRegisterRepository
                .findLatestSession(registerUuid)
                .orElseThrow(() ->
                        notFound("No cash session found")
                );

        return toSessionResponse(session);
    }

    // ---------------------------------------------------------
    // CREATE MANUAL CASH MOVEMENT
    // ADMIN / MANAGER ONLY
    // ---------------------------------------------------------

    @Override
    @Transactional
    @AdminOrManager
    public CashMovementResponse createMovement(
            UUID sessionUuid,
            CreateCashMovementRequest request
    ) {

        CashRegisterSession session = cashRegisterRepository
                .findSessionByUuidForUpdate(sessionUuid)
                .orElseThrow(() ->
                        notFound("Cash session not found")
                );

        if (session.status() != CashSessionStatus.OPEN) {
            throw badRequest("Cannot add movement to a closed session");
        }

        if (request.amount() == null ||
                request.amount().compareTo(BigDecimal.ZERO) <= 0) {

            throw badRequest(
                    "Movement amount must be greater than zero"
            );
        }

        if (request.movementType() == null ||
                request.sourceType() == null) {

            throw badRequest(
                    "Movement type and source type are required"
            );
        }

        if (request.sourceType() == CashMovementSourceType.MANUAL &&
                request.sourceUuid() != null) {

            throw badRequest(
                    "Manual movement cannot have a source UUID"
            );
        }

        if (request.sourceType() != CashMovementSourceType.MANUAL &&
                request.sourceUuid() == null) {

            throw badRequest(
                    "Source UUID is required for transaction movements"
            );
        }

        if (request.sourceType() != CashMovementSourceType.MANUAL &&
                request.sourceType() != CashMovementSourceType.ADJUSTMENT &&
                cashRegisterRepository.existsMovement(
                        request.sourceType().name(),
                        request.sourceUuid()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This transaction has already been posted to Cash Register"
            );
        }

        CashRegisterMovement movement = new CashRegisterMovement(
                null,
                UUID.randomUUID(),
                session.id(),
                request.movementType(),
                request.sourceType(),
                request.sourceUuid(),
                money(request.amount()),
                clean(request.description()),
                currentUserService.getCurrentUserId(),
                null
        );

        try {

            CashRegisterMovement saved =
                    cashRegisterRepository.createMovement(movement);

            // Audit manual cash IN/OUT.
            Map<String, Object> auditData = new HashMap<>();

            auditData.put("movementUuid", saved.uuid().toString());
            auditData.put("sessionUuid", session.uuid().toString());
            auditData.put("movementType", saved.movementType().name());
            auditData.put("sourceType", saved.sourceType().name());
            auditData.put("amount", saved.amount());
            auditData.put("description", saved.description());

            auditLogService.log(
                    "ADJUST",
                    "CASH_REGISTER_MOVEMENT",
                    saved.uuid().toString(),
                    "Manual cash register movement recorded",
                    null,
                    auditData
            );

            return toMovementResponse(saved);

        } catch (DuplicateKeyException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Duplicate cash movement"
            );
        }
    }

    // ---------------------------------------------------------
    // FIND CASH MOVEMENTS
    // ADMIN / MANAGER / CASHIER
    // ---------------------------------------------------------

    @Override
    @SalesAccess
    public List<CashMovementResponse> findMovements(UUID sessionUuid) {

        CashRegisterSession session = cashRegisterRepository
                .findSessionByUuid(sessionUuid)
                .orElseThrow(() ->
                        notFound("Cash session not found")
                );

        return cashRegisterRepository.findMovements(session.id())
                .stream()
                .map(this::toMovementResponse)
                .toList();
    }

    // ---------------------------------------------------------
    // FIND LATEST SESSION AFTER CLOSING
    // ---------------------------------------------------------

    private CashSessionResponse findLatestSessionForSession(UUID uuid) {

        CashRegisterSession session = cashRegisterRepository
                .findSessionByUuid(uuid)
                .orElseThrow(() ->
                        notFound("Cash session not found")
                );

        return toSessionResponse(session);
    }

    // ---------------------------------------------------------
    // BUILD SESSION RESPONSE
    // ---------------------------------------------------------

    private CashSessionResponse toSessionResponse(
            CashRegisterSession session
    ) {

        BigDecimal cashIn = cashRegisterRepository.calculateTotalByType(
                session.id(),
                CashMovementType.IN.name()
        );

        BigDecimal cashOut = cashRegisterRepository.calculateTotalByType(
                session.id(),
                CashMovementType.OUT.name()
        );

        BigDecimal expected = session.expectedClosingBalance();

        if (expected == null) {

            expected = money(
                    session.openingBalance()
                            .add(cashIn)
                            .subtract(cashOut)
            );
        }

        return new CashSessionResponse(
                session.uuid(),
                null,
                session.registerName(),
                session.openingBalance(),
                money(cashIn),
                money(cashOut),
                expected,
                session.actualClosingBalance(),
                session.differenceAmount(),
                session.status(),
                session.openedAt(),
                session.closedAt()
        );
    }

    // ---------------------------------------------------------
    // BUILD REGISTER RESPONSE
    // ---------------------------------------------------------

    private CashRegisterResponse toRegisterResponse(
            CashRegister register
    ) {

        return new CashRegisterResponse(
                register.uuid(),
                register.registerCode(),
                register.name(),
                register.description(),
                register.active(),
                register.createdAt()
        );
    }

    // ---------------------------------------------------------
    // BUILD MOVEMENT RESPONSE
    // ---------------------------------------------------------

    private CashMovementResponse toMovementResponse(
            CashRegisterMovement movement
    ) {

        return new CashMovementResponse(
                movement.uuid(),
                movement.movementType(),
                movement.sourceType(),
                movement.sourceUuid(),
                movement.amount(),
                movement.description(),
                movement.createdAt()
        );
    }

    // ---------------------------------------------------------
    // MONEY ROUNDING
    // ---------------------------------------------------------

    private BigDecimal money(BigDecimal amount) {

        return amount.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    // ---------------------------------------------------------
    // CLEAN STRING
    // ---------------------------------------------------------

    private String clean(String value) {

        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    // ---------------------------------------------------------
    // NOT FOUND EXCEPTION
    // ---------------------------------------------------------

    private ResponseStatusException notFound(String message) {

        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }

    // ---------------------------------------------------------
    // BAD REQUEST EXCEPTION
    // ---------------------------------------------------------

    private ResponseStatusException badRequest(String message) {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    // ---------------------------------------------------------
    // AUTOMATIC TRANSACTION MOVEMENT
    // ADMIN / MANAGER / CASHIER
    //
    // Used internally by Sales and Refund workflows.
    // Do not restrict this to ADMIN/MANAGER.
    // ---------------------------------------------------------

    @Override
    @Transactional
    @SalesAccess
    public void recordTransactionMovement(
            CashMovementType movementType,
            CashMovementSourceType sourceType,
            UUID sourceUuid,
            BigDecimal amount,
            String description
    ) {

        // 1. Validate input.
        if (movementType == null ||
                sourceType == null ||
                sourceUuid == null) {

            throw new IllegalArgumentException(
                    "Movement type, source type and source UUID are required"
            );
        }

        if (amount == null ||
                amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Cash movement amount must be greater than zero"
            );
        }

        if (sourceType == CashMovementSourceType.MANUAL) {

            throw new IllegalArgumentException(
                    "MANUAL movements must use the manual cash movement workflow"
            );
        }

        BigDecimal movementAmount = amount.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );

        // 2. Get authenticated user.
        Long userId = currentUserService.getCurrentUserId();

        // 3. Find and lock the user's open cash session.
        CashRegisterSession session = cashRegisterRepository
                .findOpenSessionForUserUpdate(userId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No open cash register session found for the current user"
                        )
                );

        // 4. Check duplicate transaction.
        boolean alreadyRecorded = cashRegisterRepository
                .findMovementBySource(
                        sourceType.name(),
                        sourceUuid
                )
                .isPresent();

        if (alreadyRecorded) {

            logger.info(
                    "Cash movement already recorded. Source: {}, UUID: {}",
                    sourceType,
                    sourceUuid
            );

            return;
        }

        // 5. Create cash movement.
        CashRegisterMovement movement = new CashRegisterMovement(
                null,
                UUID.randomUUID(),
                session.id(),
                movementType,
                sourceType,
                sourceUuid,
                movementAmount,
                description,
                userId,
                null
        );

        // 6. Save movement.
        cashRegisterRepository.createMovement(movement);

        logger.info(
                "Cash movement recorded. Session ID: {}, Type: {}, Source: {}, Amount: {}",
                session.id(),
                movementType,
                sourceType,
                movementAmount
        );
    }
}
