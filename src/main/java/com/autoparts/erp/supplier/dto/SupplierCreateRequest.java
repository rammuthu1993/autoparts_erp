package com.autoparts.erp.supplier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SupplierCreateRequest(

    @NotBlank(message = "Supplier name is required")
    @Size(max = 200, message = "Supplier name cannot exceed 200 characters")
    String supplierName,

    @Size(max = 150, message = "Contact person cannot exceed 150 characters")
    String contactPerson,

    @NotBlank(message = "Phone number is required")
    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    @Pattern(
        regexp = "^[0-9+\\- ()]{7,20}$",
        message = "Phone number must be 7-20 digits or phone characters"
    )
    String phone,

    @Email(message = "Invalid email address")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    String email,

    @Size(max = 500, message = "Address cannot exceed 500 characters")
    String address,

    @Size(max = 100, message = "City cannot exceed 100 characters")
    String city,

    @Size(max = 100, message = "State cannot exceed 100 characters")
    String state,

    @Pattern(
        regexp = "^$|^[0-9]{6}$",
        message = "PIN code must contain exactly 6 digits"
    )
    String pincode,

    @Pattern(
        regexp = "^$|^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z]{1}[1-9A-Za-z]{1}[Zz][0-9A-Za-z]{1}$",
        message = "GSTIN must be a valid 15-character Indian GSTIN"
    )
    String gstin,

    @Min(value = 0, message = "Payment terms cannot be negative")
    @Max(value = 3650, message = "Payment terms cannot exceed 3650 days")
    Integer paymentTermsDays,

    @Size(max = 30, message = "Supplier type cannot exceed 30 characters")
    String supplierType,

    @Pattern(
        regexp = "^$|^[0-9+\\- ()]{7,20}$",
        message = "Alternate phone must be 7-20 digits or phone characters"
    )
    String alternatePhone,

    @Size(max = 30, message = "GST registration type cannot exceed 30 characters")
    String gstRegistrationType,

    @DecimalMin(value = "0.00", message = "Credit limit cannot be negative")
    BigDecimal creditLimit

) {
    public SupplierCreateRequest(
        String supplierName,
        String contactPerson,
        String phone,
        String email,
        String address,
        String city,
        String state,
        String pincode,
        String gstin,
        Integer paymentTermsDays
    ) {
        this(supplierName, contactPerson, phone, email, address, city, state, pincode, gstin,
            paymentTermsDays, "DISTRIBUTOR", null, null, null);
    }
}
