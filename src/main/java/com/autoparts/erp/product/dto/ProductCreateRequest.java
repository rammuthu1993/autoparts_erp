
package com.autoparts.erp.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductCreateRequest(

        @NotBlank
        @Size(max = 100)
        String partNo,

        @NotBlank
        @Size(max = 200)
        @Pattern(
                regexp = "^[a-zA-Z0-9 .,\\*#@&()]+$",
                message = "Product name can contain only letters, numbers, spaces, and these special characters: . , * # @ & ( )"
        )
        String name,

        @Size(max = 1000)
        String description,

        @NotNull
        @Positive
        Long categoryId,

        @NotNull
        @Positive
        Long brandId,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal purchasePrice,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal sellingPrice,

        @NotNull
        @DecimalMin("0.000")
        @Digits(integer = 9, fraction = 3)
        BigDecimal minimumStock,
        
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        int qty,

        @NotBlank
        @Size(max = 20)
        String unit

) {}
