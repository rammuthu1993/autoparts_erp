package com.autoparts.erp.purchase.returns.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PurchaseReturnRequest(

    @NotBlank(message = "Return reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    String reason,

    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    String notes,

    @NotEmpty(message = "At least one product is required")
    List<@Valid PurchaseReturnProductRequest> products

) {}