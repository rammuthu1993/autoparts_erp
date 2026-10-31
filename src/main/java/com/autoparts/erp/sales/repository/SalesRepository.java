package com.autoparts.erp.sales.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.autoparts.erp.sales.model.Sale;
import com.autoparts.erp.sales.model.SaleProduct;
import com.autoparts.erp.sales.model.SalePayment;

public interface SalesRepository {

    Sale createSale(Sale sale);

    Sale updateSale(Sale sale);

    Optional<Sale> findByUuid(UUID uuid);

    Optional<Sale> findByUuidForUpdate(UUID uuid);

    List<Sale> findAll();

    List<SaleProduct> findProductsBySaleId(Long saleId);

    List<SalePayment> findPaymentsBySaleId(Long saleId);

    void createSaleItem(SaleProduct product);

    void createSalePayment(SalePayment payment);

    Optional<Long> findActiveProductId(UUID productUuid);

    Optional<ProductStock> findProductStockForUpdate(Long productId);

    void updateProductStock(Long productId, BigDecimal newQuantity);

    void createInventoryMovement(
        Long productId,
        BigDecimal quantity,
        BigDecimal beforeQuantity,
        BigDecimal afterQuantity,
        UUID saleUuid
    );

    boolean existsInvoiceNumber(String invoiceNumber);

    String generateInvoiceNumber(LocalDate saleDate);
}