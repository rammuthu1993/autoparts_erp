package com.autoparts.erp.sales.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.model.SalesProduct;
import com.autoparts.erp.sales.model.SalesPayment;

public interface SalesRepository {

    Sales createSale(Sales sale);

    Sales updateSale(Sales sale);

    Optional<Sales> findByUuid(UUID uuid);

    Optional<Sales> findByUuidForUpdate(UUID uuid);

    List<Sales> findAll();

    List<SalesProduct> findProductsBySaleId(Long saleId);

    List<SalesPayment> findPaymentsBySaleId(Long saleId);

    void createSaleProduct(SalesProduct product);

    void createSalePayment(SalesPayment payment);

    Optional<Long> findActiveProductId(UUID productUuid);

    boolean existsInvoiceNumber(String invoiceNumber);

    String generateInvoiceNumber(LocalDate saleDate);
    
    Optional<UUID> findUuidById(Long saleId);
}