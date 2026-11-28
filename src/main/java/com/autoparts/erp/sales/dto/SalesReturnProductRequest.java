package com.autoparts.erp.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.autoparts.erp.sales.enums.ReturnDisposition;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalesReturnProductRequest(

    @NotNull(message = "Sales product UUID is required")
    UUID salesProductUuid,

    @NotNull(message = "Return quantity is required")
    @DecimalMin(
        value = "0.001",
        message = "Return quantity must be greater than zero"
    )
    BigDecimal quantity,

    ReturnDisposition disposition,

    @Size(max = 300, message = "Condition notes must not exceed 300 characters")
    String conditionNotes,

    @Size(max = 500, message = "Inspection notes must not exceed 500 characters")
    String inspectionNotes

) {
    public SalesReturnProductRequest(UUID salesProductUuid, BigDecimal quantity) {
        this(salesProductUuid, quantity, ReturnDisposition.RESTOCK, null, null);
    }
}
