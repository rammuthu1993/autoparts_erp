package com.autoparts.erp.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(

        @NotBlank(message = "Customer name is required")
        @Size(max = 200, message = "Customer name cannot exceed 200 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9 .,*#@&()\\-]+$",
                message = "Customer name contains invalid characters"
        )
        String customerName,

        @Pattern(
                regexp = "^[0-9+\\- ]{7,20}$",
                message = "Invalid phone number"
        )
        String phone,

        @Email(message = "Invalid email address")
        @Size(max = 150, message = "Email cannot exceed 150 characters")
        String email,

        @Size(max = 500, message = "Address cannot exceed 500 characters")
        String address,

        @Size(max = 100, message = "City cannot exceed 100 characters")
        String city,

        @Size(max = 100, message = "State cannot exceed 100 characters")
        String state,

        @Pattern(
                regexp = "^[0-9]{6}$",
                message = "Pincode must contain exactly 6 digits"
        )
        String pincode,

        @Pattern(
                regexp = "^[0-9A-Za-z]{15}$",
                message = "GSTIN must contain 15 characters"
        )
        String gstin
) {
}