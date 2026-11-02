package com.autoparts.erp.sales.service;

import java.util.List;
import java.util.UUID;

import com.autoparts.erp.sales.dto.SalesRefundRequest;
import com.autoparts.erp.sales.dto.SalesReturnRequest;
import com.autoparts.erp.sales.dto.SalesReturnResponse;

public interface SalesReturnService {

    SalesReturnResponse createReturn(
            UUID saleUuid,
            SalesReturnRequest request
    );

    List<SalesReturnResponse> findReturnsBySaleUuid(
            UUID saleUuid
    );

    SalesReturnResponse findReturnByUuid(
            UUID returnUuid
    );

    SalesReturnResponse recordRefund(
            UUID returnUuid,
            SalesRefundRequest request
    );
}