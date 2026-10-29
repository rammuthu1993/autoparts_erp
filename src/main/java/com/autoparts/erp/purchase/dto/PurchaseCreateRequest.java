
package com.autoparts.erp.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PurchaseCreateRequest(

    @NotNull
    @jakarta.validation.constraints.Positive
    Long supplierId,

    @NotBlank
    @Size(max = 100)
    String supplierInvoiceNo,

    @NotNull
    @PastOrPresent
    LocalDate purchaseDate,

    @Size(max = 1000)
    String notes,

    @NotEmpty
    @Size(max = 500)
    List<@Valid PurchaseProductRequest> products

) {}
