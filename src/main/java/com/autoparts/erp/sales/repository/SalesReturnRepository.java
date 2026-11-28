package com.autoparts.erp.sales.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.autoparts.erp.sales.model.SalesReturn;
import com.autoparts.erp.sales.model.SalesReturnProduct;
import com.autoparts.erp.sales.model.SalesRefund;

public interface SalesReturnRepository {

    SalesReturn createReturn(SalesReturn salesReturn);

    void createReturnProduct(
        SalesReturnProduct product
    );

    SalesRefund createRefund(SalesRefund refund);

    SalesReturn updateReturn(SalesReturn salesReturn);

    Optional<SalesReturn> findReturnByUuid(UUID uuid);

    Optional<SalesReturn> findReturnByUuidForUpdate(UUID uuid);

    List<SalesReturn> findReturnsBySaleId(Long saleId);

    List<SalesReturnProduct> findProductsByReturnId(Long returnId);

    List<SalesRefund> findRefundsByReturnId(Long returnId);

    List<SalesReturnProduct> findReturnedProductsBySaleProductId(
        Long salesProductId
    );

    BigDecimal getRefundedAmount(Long returnId);

    BigDecimal getTotalRefundedAmountBySaleId(Long saleId);

    void updateReturnProductDisposition(
        Long returnId,
        Long salesProductId,
        String disposition,
        String conditionNotes,
        String inspectionNotes
    );

    List<SalesReturn> findReturnsPaged(
        String search,
        String status,
        java.time.LocalDate fromDate,
        java.time.LocalDate toDate,
        int offset,
        int limit
    );

    long countReturnsPaged(
        String search,
        String status,
        java.time.LocalDate fromDate,
        java.time.LocalDate toDate
    );
}