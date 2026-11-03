package com.autoparts.erp.cashregister.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.cashregister.dto.*;
import com.autoparts.erp.cashregister.enums.*;
import com.autoparts.erp.cashregister.model.*;
import com.autoparts.erp.cashregister.repository.CashRegisterRepository;
import com.autoparts.erp.cashregister.service.CashRegisterService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.model.CashRegisterMovement;
import com.autoparts.erp.cashregister.model.CashRegisterSession;
import com.autoparts.erp.cashregister.repository.CashRegisterRepository;

@Service
@Transactional(readOnly = true)
public class CashRegisterServiceImpl implements CashRegisterService {

    private static final int MONEY_SCALE = 2;

    private final CashRegisterRepository repository;
    private final CurrentUserService currentUserService;
    private static final Logger logger =
    	    LoggerFactory.getLogger(CashRegisterServiceImpl.class);
    private final CashRegisterRepository cashRegisterRepository;

    public CashRegisterServiceImpl(
        CashRegisterRepository repository,
        CurrentUserService currentUserService,
        CashRegisterRepository cashRegisterRepository
    ) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.cashRegisterRepository = cashRegisterRepository;
    }

    @Override
    @Transactional
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
            CashRegister saved = repository.createRegister(register);
            return toRegisterResponse(saved);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Register code already exists"
            );
        }
    }

    @Override
    public List<CashRegisterResponse> findAllRegisters() {

        return repository.findAllRegisters()
            .stream()
            .map(this::toRegisterResponse)
            .toList();
    }

    @Override
    @Transactional
    public CashSessionResponse openSession(
        UUID registerUuid,
        OpenCashSessionRequest request
    ) {

        CashRegister register = repository
            .findRegisterByUuid(registerUuid)
            .orElseThrow(() -> notFound("Cash register not found"));

        if (!register.active()) {
            throw badRequest("Cash register is inactive");
        }

        if (request.openingBalance() == null ||
            request.openingBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw badRequest("Invalid opening balance");
        }

        if (repository.findOpenSessionForUpdate(registerUuid).isPresent()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This register already has an open session"
            );
        }

        CashRegisterSession session;

        try {
            session = repository.openSession(
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

        return toSessionResponse(session);
    }

    @Override
    @Transactional
    public CashSessionResponse closeSession(
        UUID sessionUuid,
        CloseCashSessionRequest request
    ) {

        CashRegisterSession session = repository
            .findSessionByUuidForUpdate(sessionUuid)
            .orElseThrow(() -> notFound("Cash session not found"));

        if (session.status() != CashSessionStatus.OPEN) {
            throw badRequest("Cash session is already closed");
        }

        BigDecimal cashIn = repository.calculateTotalByType(
            session.id(),
            CashMovementType.IN.name()
        );

        BigDecimal cashOut = repository.calculateTotalByType(
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

        boolean closed = repository.closeSession(
            session.id(),
            currentUserService.getCurrentUserId(),
            expected,
            actual,
            difference
        );

        if (!closed) {
            throw badRequest("Unable to close cash session");
        }

        return findLatestSessionForSession(sessionUuid);
    }

    @Override
    public CashSessionResponse findLatestSession(UUID registerUuid) {

        CashRegisterSession session = repository
            .findLatestSession(registerUuid)
            .orElseThrow(() -> notFound("No cash session found"));

        return toSessionResponse(session);
    }

    @Override
    @Transactional
    public CashMovementResponse createMovement(
        UUID sessionUuid,
        CreateCashMovementRequest request
    ) {

        CashRegisterSession session = repository
            .findSessionByUuidForUpdate(sessionUuid)
            .orElseThrow(() -> notFound("Cash session not found"));

        if (session.status() != CashSessionStatus.OPEN) {
            throw badRequest("Cannot add movement to a closed session");
        }

        if (request.amount() == null ||
            request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("Movement amount must be greater than zero");
        }

        if (request.sourceType() == CashMovementSourceType.MANUAL &&
            request.sourceUuid() != null) {
            throw badRequest("Manual movement cannot have a source UUID");
        }

        if (request.sourceType() != CashMovementSourceType.MANUAL &&
            request.sourceUuid() == null) {
            throw badRequest("Source UUID is required for transaction movements");
        }

        if (request.sourceType() != CashMovementSourceType.MANUAL &&
            request.sourceType() != CashMovementSourceType.ADJUSTMENT &&
            repository.existsMovement(
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
            return toMovementResponse(repository.createMovement(movement));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Duplicate cash movement"
            );
        }
    }

    @Override
    public List<CashMovementResponse> findMovements(UUID sessionUuid) {

        CashRegisterSession session = repository
            .findSessionByUuid(sessionUuid)
            .orElseThrow(() -> notFound("Cash session not found"));

        return repository.findMovements(session.id())
            .stream()
            .map(this::toMovementResponse)
            .toList();
    }

    private CashSessionResponse findLatestSessionForSession(UUID uuid) {

        CashRegisterSession session = repository
            .findSessionByUuid(uuid)
            .orElseThrow(() -> notFound("Cash session not found"));

        return toSessionResponse(session);
    }

    private CashSessionResponse toSessionResponse(
        CashRegisterSession session
    ) {

        BigDecimal cashIn = repository.calculateTotalByType(
            session.id(),
            CashMovementType.IN.name()
        );

        BigDecimal cashOut = repository.calculateTotalByType(
            session.id(),
            CashMovementType.OUT.name()
        );

        BigDecimal expected = session.expectedClosingBalance();

        if (expected == null) {
            expected = money(
                session.openingBalance().add(cashIn).subtract(cashOut)
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
            session.difference(),
            session.status(),
            session.openedAt(),
            session.closedAt()
        );
    }

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

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    
    @Override
    @Transactional
    public void recordTransactionMovement(
        CashMovementType movementType,
        CashMovementSourceType sourceType,
        UUID sourceUuid,
        BigDecimal amount,
        String description
    ) {

        // 1. Validate input
        if (movementType == null || sourceType == null || sourceUuid == null) {
            throw new IllegalArgumentException(
                "Movement type, source type and source UUID are required"
            );
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
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
            2,
            RoundingMode.HALF_UP
        );

        // 2. Get authenticated user
        Long userId = currentUserService.getCurrentUserId();

        // 3. Find and lock the user's open cash session
        CashRegisterSession session = cashRegisterRepository
            .findOpenSessionForUserUpdate(userId)
            .orElseThrow(() ->
                new IllegalStateException(
                    "No open cash register session found for the current user"
                )
            );

        // 4. Check duplicate transaction
        boolean alreadyRecorded = cashRegisterRepository
            .findMovementBySource(sourceType.name(), sourceUuid)
            .isPresent();

        if (alreadyRecorded) {
            // Prevent duplicate cash movement for the same source transaction.
            return;
        }

        // 5. Create cash movement
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

        // 6. Save movement
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