
package com.autoparts.erp.purchase.service;

import com.autoparts.erp.purchase.dto.PurchaseCreateRequest;
import com.autoparts.erp.purchase.dto.PurchaseResponse;

import java.util.List;
import java.util.UUID;

public interface PurchaseService {

    PurchaseResponse create(PurchaseCreateRequest request);

    List<PurchaseResponse> findAll();

    PurchaseResponse findByUuid(UUID uuid);
    
    PurchaseResponse receive(UUID uuid);
}
