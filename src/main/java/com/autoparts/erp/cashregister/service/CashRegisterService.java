package com.autoparts.erp.cashregister.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.autoparts.erp.cashregister.dto.CashMovementResponse;
import com.autoparts.erp.cashregister.dto.CashRegisterResponse;
import com.autoparts.erp.cashregister.dto.CashSessionResponse;
import com.autoparts.erp.cashregister.dto.CloseCashSessionRequest;
import com.autoparts.erp.cashregister.dto.CreateCashMovementRequest;
import com.autoparts.erp.cashregister.dto.CreateCashRegisterRequest;
import com.autoparts.erp.cashregister.dto.OpenCashSessionRequest;
import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.common.dto.PageResponse;

public interface CashRegisterService {

    CashRegisterResponse createRegister(CreateCashRegisterRequest request);

    List<CashRegisterResponse> findAllRegisters();

    CashSessionResponse openSession(
        UUID registerUuid,
        OpenCashSessionRequest request
    );

    CashSessionResponse closeSession(
        UUID sessionUuid,
        CloseCashSessionRequest request
    );

    CashSessionResponse findLatestSession(UUID registerUuid);

    CashMovementResponse createMovement(
        UUID sessionUuid,
        CreateCashMovementRequest request
    );

    List<CashMovementResponse> findMovements(UUID sessionUuid);

    PageResponse<CashMovementResponse> findMovementsPaged(
        UUID sessionUuid,
        int page,
        int size,
        String sortBy,
        String sortDir
    );
    
    void recordTransactionMovement(
    	    CashMovementType movementType,
    	    CashMovementSourceType sourceType,
    	    UUID sourceUuid,
    	    BigDecimal amount,
    	    String description
    	);
}