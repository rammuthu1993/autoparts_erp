package com.autoparts.erp.sales.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record SalesReturnRequest(

    @NotBlank(message = "Return reason is required")
    @Size(
        max = 500,
        message = "Return reason cannot exceed 500 characters"
    )
    String reason,

    @Size(
        max = 1000,
        message = "Notes cannot exceed 1000 characters"
    )
    String notes,

    @NotEmpty(message = "At least one product must be returned")
    List<@Valid SalesReturnProductRequest> products

) {}