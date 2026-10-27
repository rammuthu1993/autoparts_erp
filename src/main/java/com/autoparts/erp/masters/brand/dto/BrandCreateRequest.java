package com.autoparts.erp.masters.brand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandCreateRequest(

        @NotBlank(message = "Brand name is required")
        @Size(max = 100, message = "Brand name cannot exceed 100 characters")
        String name,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description

) {}