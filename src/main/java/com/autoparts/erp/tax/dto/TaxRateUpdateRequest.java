
package com.autoparts.erp.tax.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaxRateUpdateRequest(

    @NotBlank
    @Size(max = 50)
    String taxName,

    @NotNull
    Boolean active

) {}
