package com.autoparts.erp.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductUpdateRequest(

        @NotBlank(message = "Part number is required")
        @Size(max = 100, message = "Part number cannot exceed 100 characters")
        String partNo,

        @NotBlank(message = "Product name is required")
        @Size(max = 200, message = "Product name cannot exceed 200 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9 .,\\*#@&()]+$",
                message = "Product name can contain only letters, numbers, spaces, and these special characters: . , * # @ & ( )"
        )
        String name,

        @Size(max = 1000, message = "Description cannot exceed 1000 characters")
        String description,

        @NotNull(message = "Category is required")
        @Positive(message = "Category ID must be greater than zero")
        Long categoryId,

        @NotNull(message = "Brand is required")
        @Positive(message = "Brand ID must be greater than zero")
        Long brandId,

        @NotNull(message = "Purchase price is required")
        @DecimalMin(value = "0.00", message = "Purchase price cannot be negative")
        @Digits(integer = 10, fraction = 2)
        BigDecimal purchasePrice,

        @NotNull(message = "Selling price is required")
        @DecimalMin(value = "0.00", message = "Selling price cannot be negative")
        @Digits(integer = 10, fraction = 2)
        BigDecimal sellingPrice,

        @NotNull(message = "Minimum stock is required")
        @DecimalMin(value = "0.000", message = "Minimum stock cannot be negative")
        @Digits(integer = 9, fraction = 3)
        BigDecimal minimumStock,

        @NotBlank(message = "Unit is required")
        @Size(max = 20, message = "Unit cannot exceed 20 characters")
        String unit

) {}