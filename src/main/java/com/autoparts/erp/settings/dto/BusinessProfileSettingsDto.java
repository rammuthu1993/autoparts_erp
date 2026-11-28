package com.autoparts.erp.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record BusinessProfileSettingsDto(
        UUID uuid,

        @NotBlank(message = "Business name is required")
        @Size(max = 200, message = "Business name cannot exceed 200 characters")
        String businessName,

        @Size(max = 200, message = "Legal name cannot exceed 200 characters")
        String legalName,

        @Pattern(
                regexp = "^$|^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z]{1}[1-9A-Za-z]{1}[Zz][0-9A-Za-z]{1}$",
                message = "GSTIN must be a valid 15-character Indian GSTIN"
        )
        String gstin,

        @Pattern(
                regexp = "^$|^[A-Za-z]{5}[0-9]{4}[A-Za-z]{1}$",
                message = "PAN must be a valid 10-character Indian PAN"
        )
        String pan,

        @Size(max = 500, message = "Registered address cannot exceed 500 characters")
        String registeredAddress,

        @Size(max = 500, message = "Shop address cannot exceed 500 characters")
        String shopAddress,

        @Size(max = 100, message = "City cannot exceed 100 characters")
        String city,

        @Size(max = 100, message = "State cannot exceed 100 characters")
        String state,

        @Pattern(regexp = "^$|^[0-9]{2}$", message = "State code must be 2 digits")
        String stateCode,

        @Pattern(regexp = "^$|^[0-9]{6}$", message = "PIN code must be 6 digits")
        String pincode,

        @Pattern(regexp = "^$|^[0-9+\\- ()]{7,20}$", message = "Phone must be 7-20 digits or phone characters")
        String phone,

        @Email(message = "Invalid business email address")
        @Size(max = 150, message = "Email cannot exceed 150 characters")
        String email,

        @Size(max = 500, message = "Logo URL cannot exceed 500 characters")
        String logoUrl,

        @NotBlank(message = "Currency is required")
        @Size(max = 10, message = "Currency cannot exceed 10 characters")
        String currency,

        @NotBlank(message = "Time zone is required")
        @Size(max = 60, message = "Time zone cannot exceed 60 characters")
        String timeZone,

        Instant updatedAt
) {}
