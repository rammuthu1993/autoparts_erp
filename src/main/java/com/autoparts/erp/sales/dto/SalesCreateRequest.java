package com.autoparts.erp.sales.dto;

import com.autoparts.erp.sales.enums.SupplyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesCreateRequest(

    @Size(max = 200)
    String customerName,

    @Size(max = 20)
    String customerPhone,

    UUID customerUuid,

    @Size(max = 15)
    String customerGstin,

    @NotNull(message = "Sale date is required")
    LocalDate saleDate,

    @NotNull(message = "Supply type is required")
    SupplyType supplyType,

    @Size(max = 100)
    String placeOfSupply,

    String billingAddress,

    String shippingAddress,

    @Size(max = 100)
    String customerReference,

    @Size(max = 100)
    String paymentTerms,

    LocalDate dueDate,

    @NotEmpty(message = "At least one product is required")
    List<@Valid SalesProductRequest> products,

    List<@Valid SalesPaymentRequest> payments,

    @Size(max = 1000)
    String notes

) {
    public SalesCreateRequest(
        String customerName,
        String customerPhone,
        UUID customerUuid,
        LocalDate saleDate,
        SupplyType supplyType,
        List<SalesProductRequest> products,
        List<SalesPaymentRequest> payments,
        String notes
    ) {
        this(
            customerName, customerPhone, customerUuid, null, saleDate, supplyType,
            null, null, null, null, null, null, products, payments, notes
        );
    }
}
