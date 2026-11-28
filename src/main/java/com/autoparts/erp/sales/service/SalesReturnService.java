package com.autoparts.erp.sales.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.autoparts.erp.common.dto.PageResponse;
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

    PageResponse<SalesReturnResponse> findReturnsPaged(
            String search,
            String status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    );

    SalesReturnResponse findReturnByUuid(
            UUID returnUuid
    );

    SalesReturnResponse recordRefund(
            UUID returnUuid,
            SalesRefundRequest request
    );
}