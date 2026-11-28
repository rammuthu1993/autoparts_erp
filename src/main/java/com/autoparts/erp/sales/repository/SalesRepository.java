package com.autoparts.erp.sales.repository;

import com.autoparts.erp.sales.dto.SalesStatusOption;
import com.autoparts.erp.sales.dto.SalesSummaryResponse;
import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.model.SalesPayment;
import com.autoparts.erp.sales.model.SalesProduct;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SalesRepository {

    record SaleReturnAggregate(
        BigDecimal returnedAmount,
        BigDecimal refundedAmount,
        long returnedQuantityLines,
        long fullyReturnedLines,
        long totalLines
    ) {}

    List<SalesStatusOption> findSalesStatuses();

    List<SalesStatusOption> findSalesPaymentStatuses();

    boolean isSalesStatusConfigured(String code);

    boolean isSalesPaymentStatusConfigured(String code);

    Sales createSale(Sales sale);

    Sales updateSale(Sales sale);

    Optional<Sales> findByUuid(UUID uuid);

    Optional<Sales> findByUuidForUpdate(UUID uuid);

    List<Sales> findAll();

    default List<Sales> findPaged(
        int offset,
        int limit,
        String search,
        String status,
        String paymentStatus,
        String sortBy,
        String sortDir
    ) {
        return findPaged(offset, limit, search, status, paymentStatus, null, null, null, null, null, null, null, sortBy, sortDir);
    }

    List<Sales> findPaged(
        int offset,
        int limit,
        String search,
        String status,
        String paymentStatus,
        LocalDate fromDate,
        LocalDate toDate,
        UUID customerUuid,
        String paymentMethod,
        String returnStatus,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String sortBy,
        String sortDir
    );

    default long countPaged(String search, String status, String paymentStatus) {
        return countPaged(search, status, paymentStatus, null, null, null, null, null, null, null);
    }

    long countPaged(
        String search,
        String status,
        String paymentStatus,
        LocalDate fromDate,
        LocalDate toDate,
        UUID customerUuid,
        String paymentMethod,
        String returnStatus,
        BigDecimal minAmount,
        BigDecimal maxAmount
    );

    List<SalesProduct> findProductsBySaleId(Long saleId);

    Map<Long, List<SalesProduct>> findProductsBySaleIds(List<Long> saleIds);

    List<SalesPayment> findPaymentsBySaleId(Long saleId);

    Map<Long, List<SalesPayment>> findPaymentsBySaleIds(List<Long> saleIds);

    Map<Long, String> findCancellationReasonsBySaleIds(List<Long> saleIds);

    Map<Long, UUID> findCustomerUuidsByIds(List<Long> customerIds);

    Map<Long, SaleReturnAggregate> findReturnAggregatesBySaleIds(List<Long> saleIds);

    void createSaleProduct(SalesProduct product);

    void createSalePayment(SalesPayment payment);

    void deleteDraftProducts(Long saleId);

    void deleteDraftPayments(Long saleId);

    Optional<Long> findActiveProductId(UUID productUuid);

    boolean existsInvoiceNumber(String invoiceNumber);

    String generateInvoiceNumber(LocalDate saleDate);

    Optional<UUID> findUuidById(Long saleId);

    void saveCancellationReason(Long saleId, String reason);

    Optional<String> findCancellationReason(Long saleId);

    SalesSummaryResponse getSummary(LocalDate fromDate, LocalDate toDate);
}
