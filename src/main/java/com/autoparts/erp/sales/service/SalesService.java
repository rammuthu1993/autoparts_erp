package com.autoparts.erp.sales.service;

import java.util.List;
import java.util.UUID;

import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.sales.dto.SalesCancelRequest;
import com.autoparts.erp.sales.dto.SalesCancellationPreviewResponse;
import com.autoparts.erp.sales.dto.SalesCancellationRefundConfirmRequest;
import com.autoparts.erp.sales.dto.SalesCancellationResponse;
import com.autoparts.erp.sales.dto.SalesCreateRequest;
import com.autoparts.erp.sales.dto.SalesResponse;
import com.autoparts.erp.sales.dto.SalesPaymentRequest;

public interface SalesService {

    SalesResponse create(SalesCreateRequest request);

    SalesResponse complete(UUID saleUuid);

    SalesResponse findByUuid(UUID saleUuid);

    List<SalesResponse> findAll();

    PageResponse<SalesResponse> findPaged(
        int page,
        int size,
        String search,
        String status,
        String paymentStatus,
        String sortBy,
        String sortDir
    );

    SalesResponse cancel(UUID saleUuid, String reason);

    SalesResponse cancel(UUID saleUuid, SalesCancelRequest request);

    SalesCancellationPreviewResponse getCancellationPreview(UUID saleUuid);

    SalesCancellationResponse getCancellation(UUID saleUuid);

    SalesCancellationResponse confirmCancellationRefund(
        UUID saleUuid,
        UUID refundUuid,
        SalesCancellationRefundConfirmRequest request
    );
    
    SalesResponse recordPayment(
    	    UUID saleUuid,
    	    SalesPaymentRequest request
    	);
}