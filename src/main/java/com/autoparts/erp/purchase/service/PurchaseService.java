
package com.autoparts.erp.purchase.service;

import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.purchase.dto.PurchaseCreateRequest;
import com.autoparts.erp.purchase.dto.PurchaseResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PurchaseService {

    PurchaseResponse create(PurchaseCreateRequest request);

    PurchaseResponse updateDraft(UUID uuid, PurchaseCreateRequest request);

    PurchaseResponse cancelDraft(UUID uuid, String reason);

    List<PurchaseResponse> findAll();

    PageResponse<PurchaseResponse> findPaged(
        int page,
        int size,
        String search,
        String status,
        String sortBy,
        String sortDir
    );

    PageResponse<PurchaseResponse> findPaged(
        int page,
        int size,
        String search,
        String status,
        String paymentStatus,
        UUID supplierUuid,
        LocalDate fromDate,
        LocalDate toDate,
        boolean onlyOutstanding,
        String sortBy,
        String sortDir
    );

    PurchaseResponse findByUuid(UUID uuid);
    
    PurchaseResponse receive(UUID uuid);
}
