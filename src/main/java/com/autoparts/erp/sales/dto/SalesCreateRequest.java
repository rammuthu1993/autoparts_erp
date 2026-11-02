package com.autoparts.erp.sales.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.autoparts.erp.sales.enums.SupplyType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SalesCreateRequest(

    @Size(max = 200)
    String customerName,

    @Size(max = 20)
    String customerPhone,

    UUID customerUuid,

    @NotNull(message = "Sale date is required")
    LocalDate saleDate,

    @NotNull(message = "Supply type is required")
    SupplyType supplyType,

    @NotEmpty(message = "At least one product is required")
    List<@Valid SalesProductRequest> products,

    List<@Valid SalesPaymentRequest> payments,

    @Size(max = 1000)
    String notes

) {}