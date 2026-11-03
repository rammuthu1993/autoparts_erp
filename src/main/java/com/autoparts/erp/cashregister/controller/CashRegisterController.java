package com.autoparts.erp.cashregister.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.cashregister.dto.*;
import com.autoparts.erp.cashregister.service.CashRegisterService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/cash-registers")
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;

    public CashRegisterController(
        CashRegisterService cashRegisterService
    ) {
        this.cashRegisterService = cashRegisterService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CashRegisterResponse createRegister(
        @Valid @RequestBody CreateCashRegisterRequest request
    ) {
        return cashRegisterService.createRegister(request);
    }

    @GetMapping
    public List<CashRegisterResponse> findAllRegisters() {
        return cashRegisterService.findAllRegisters();
    }

    @PostMapping("/{registerUuid}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public CashSessionResponse openSession(
        @PathVariable UUID registerUuid,
        @Valid @RequestBody OpenCashSessionRequest request
    ) {
        return cashRegisterService.openSession(registerUuid, request);
    }

    @PostMapping("/sessions/{sessionUuid}/close")
    public CashSessionResponse closeSession(
        @PathVariable UUID sessionUuid,
        @Valid @RequestBody CloseCashSessionRequest request
    ) {
        return cashRegisterService.closeSession(sessionUuid, request);
    }

    @GetMapping("/{registerUuid}/sessions/latest")
    public CashSessionResponse findLatestSession(
        @PathVariable UUID registerUuid
    ) {
        return cashRegisterService.findLatestSession(registerUuid);
    }

    @PostMapping("/sessions/{sessionUuid}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public CashMovementResponse createMovement(
        @PathVariable UUID sessionUuid,
        @Valid @RequestBody CreateCashMovementRequest request
    ) {
        return cashRegisterService.createMovement(sessionUuid, request);
    }

    @GetMapping("/sessions/{sessionUuid}/movements")
    public List<CashMovementResponse> findMovements(
        @PathVariable UUID sessionUuid
    ) {
        return cashRegisterService.findMovements(sessionUuid);
    }
}