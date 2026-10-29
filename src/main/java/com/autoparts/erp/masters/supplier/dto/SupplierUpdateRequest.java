
package com.autoparts.erp.masters.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierUpdateRequest(

    @NotBlank
    @Size(max = 200)
    String supplierName,

    @Size(max = 150)
    String contactPerson,

    @NotBlank
    @Size(max = 20)
    @Pattern(
        regexp = "^[+0-9() -]+$",
        message = "Phone contains invalid characters"
    )
    String phone,

    @Email
    @Size(max = 254)
    String email,

    @Size(max = 500)
    String address,

    @Size(max = 100)
    String city,

    @Size(max = 100)
    String state,

    @Size(max = 10)
    String pincode,

    @Pattern(
        regexp = "(?i)^[0-9A-Z]{2}[0-9A-Z]{10}[0-9A-Z]{3}$|^$",
        message = "GSTIN must contain 15 alphanumeric characters"
    )
    String gstin,

    @Min(0)
    @Max(3650)
    Integer paymentTermsDays

) {}
