package com.autoparts.erp.masters.brand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BrandCreateRequest(

        @NotBlank(message = "Brand name is required")
        @Size(max = 100, message = "Brand name cannot exceed 100 characters")
        String name,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        @Size(max = 30, message = "Brand code cannot exceed 30 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Brand code may only contain letters, numbers, hyphens, and underscores")
        String code,

        @Size(max = 100, message = "Country of origin cannot exceed 100 characters")
        String countryOfOrigin,

        @Size(max = 255, message = "Website cannot exceed 255 characters")
        @Pattern(regexp = "^$|^(https?://).+$", message = "Website must start with http:// or https://")
        String website

) {
    public BrandCreateRequest(String name, String description) {
        this(name, description, null, null, null);
    }
}
