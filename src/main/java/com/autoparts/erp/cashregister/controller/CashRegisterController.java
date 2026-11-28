package com.autoparts.erp.cashregister.controller;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.cashregister.dto.*;
import com.autoparts.erp.cashregister.service.CashRegisterService;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cash-registers")
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;

    public CashRegisterController(
        CashRegisterService cashRegisterService
    ) {
        this.cashRegisterService = cashRegisterService;
    }

    @AdminOrManager
    @RequiresPermission("CASH_REGISTER.CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CashRegisterResponse createRegister(
        @Valid @RequestBody CreateCashRegisterRequest request
    ) {
        return cashRegisterService.createRegister(request);
    }

    @RequiresPermission("CASH_REGISTER.VIEW")
    @GetMapping
    public List<CashRegisterResponse> findAllRegisters() {
        return cashRegisterService.findAllRegisters();
    }

    @SalesAccess
    @RequiresPermission("CASH_REGISTER.OPEN")
    @PostMapping("/{registerUuid}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public CashSessionResponse openSession(
        @PathVariable UUID registerUuid,
        @Valid @RequestBody OpenCashSessionRequest request
    ) {
        return cashRegisterService.openSession(registerUuid, request);
    }

    @SalesAccess
    @RequiresPermission("CASH_REGISTER.CLOSE")
    @PostMapping("/sessions/{sessionUuid}/close")
    public CashSessionResponse closeSession(
        @PathVariable UUID sessionUuid,
        @Valid @RequestBody CloseCashSessionRequest request
    ) {
        return cashRegisterService.closeSession(sessionUuid, request);
    }

    @RequiresPermission("CASH_REGISTER.VIEW")
    @GetMapping("/{registerUuid}/sessions/latest")
    public ResponseEntity<CashSessionResponse> findLatestSession(
        @PathVariable UUID registerUuid
    ) {
        return cashRegisterService.findLatestSession(registerUuid)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @RequiresPermission("CASH_REGISTER.OPEN")
    @PostMapping("/sessions/{sessionUuid}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public CashMovementResponse createMovement(
        @PathVariable UUID sessionUuid,
        @Valid @RequestBody CreateCashMovementRequest request
    ) {
        return cashRegisterService.createMovement(sessionUuid, request);
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/cash-registers/sessions/{sessionUuid}/movements/paged", deprecationDate = "2026-11-22T00:00:00Z")
    @RequiresPermission("CASH_REGISTER.VIEW")
    @GetMapping("/sessions/{sessionUuid}/movements")
    public List<CashMovementResponse> findMovements(
        @PathVariable UUID sessionUuid
    ) {
        return cashRegisterService.findMovements(sessionUuid);
    }

    @RequiresPermission("CASH_REGISTER.VIEW")
    @GetMapping("/sessions/{sessionUuid}/movements/paged")
    public ResponseEntity<PageResponse<CashMovementResponse>> findMovementsPaged(
            @PathVariable UUID sessionUuid,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return ResponseEntity.ok(cashRegisterService.findMovementsPaged(sessionUuid, page, size, sortBy, sortDir));
    }

    @RequiresPermission("CASH_REGISTER.VIEW")
    @GetMapping("/sessions/paged")
    public ResponseEntity<PageResponse<CashSessionResponse>> findSessionsPaged(
            @RequestParam(required = false) UUID registerUuid,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fromDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
            cashRegisterService.findSessionsPaged(registerUuid, status, fromDate, toDate, page, size)
        );
    }

    @RequiresPermission("CASH_REGISTER.VIEW")
    @GetMapping("/sessions/active/me")
    public ResponseEntity<CashSessionResponse> findMyActiveSession() {
        return cashRegisterService.findMyActiveSession()
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}