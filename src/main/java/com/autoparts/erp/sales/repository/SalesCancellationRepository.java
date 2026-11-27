package com.autoparts.erp.sales.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.autoparts.erp.sales.dto.SalesCancellationResponse;
import com.autoparts.erp.sales.model.SalesCancellationRefund;
import com.autoparts.erp.sales.model.SalesInvoiceCancellation;
import com.autoparts.erp.sales.model.SalesInvoiceCancellationItem;

public interface SalesCancellationRepository {

    SalesInvoiceCancellation createCancellation(SalesInvoiceCancellation cancellation);

    SalesInvoiceCancellation updateCancellation(SalesInvoiceCancellation cancellation);

    SalesInvoiceCancellationItem createCancellationItem(SalesInvoiceCancellationItem item);

    SalesCancellationRefund createCancellationRefund(SalesCancellationRefund refund);

    SalesCancellationRefund updateCancellationRefund(SalesCancellationRefund refund);

    Optional<SalesInvoiceCancellation> findBySaleId(Long saleId);

    Optional<SalesInvoiceCancellation> findByIdempotencyKey(String idempotencyKey);

    Optional<SalesCancellationRefund> findRefundByUuidForUpdate(UUID refundUuid);

    List<SalesInvoiceCancellationItem> findItemsByCancellationId(Long cancellationId);

    List<SalesCancellationRefund> findRefundsByCancellationId(Long cancellationId);

    Optional<SalesCancellationResponse> findDetailedBySaleId(Long saleId);
}
