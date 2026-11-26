package com.autoparts.erp.sales.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.model.SalesProduct;
import com.autoparts.erp.sales.model.SalesPayment;
import com.autoparts.erp.sales.dto.SalesStatusOption;

public interface SalesRepository {

    List<SalesStatusOption> findSalesStatuses();

    List<SalesStatusOption> findSalesPaymentStatuses();

    boolean isSalesStatusConfigured(String code);

    boolean isSalesPaymentStatusConfigured(String code);

    Sales createSale(Sales sale);

    Sales updateSale(Sales sale);

    Optional<Sales> findByUuid(UUID uuid);

    Optional<Sales> findByUuidForUpdate(UUID uuid);

    List<Sales> findAll();

    List<Sales> findPaged(
        int offset,
        int limit,
        String search,
        String status,
        String paymentStatus,
        String sortBy,
        String sortDir
    );

    long countPaged(
        String search,
        String status,
        String paymentStatus
    );

    List<SalesProduct> findProductsBySaleId(Long saleId);

    List<SalesPayment> findPaymentsBySaleId(Long saleId);

    void createSaleProduct(SalesProduct product);

    void createSalePayment(SalesPayment payment);

    Optional<Long> findActiveProductId(UUID productUuid);

    boolean existsInvoiceNumber(String invoiceNumber);

    String generateInvoiceNumber(LocalDate saleDate);
    
    Optional<UUID> findUuidById(Long saleId);

    void saveCancellationReason(Long saleId, String reason);

    Optional<String> findCancellationReason(Long saleId);
}