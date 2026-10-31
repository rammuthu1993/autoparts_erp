package com.autoparts.erp.sales.service;

import java.util.List;
import java.util.UUID;

import com.autoparts.erp.sales.dto.SalesCreateRequest;
import com.autoparts.erp.sales.dto.SalesResponse;

public interface SalesService {

    SalesResponse create(SalesCreateRequest request);

    SalesResponse complete(UUID saleUuid);

    SalesResponse findByUuid(UUID saleUuid);

    List<SalesResponse> findAll();

    SalesResponse cancel(UUID saleUuid);
}