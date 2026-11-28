package com.autoparts.erp.masters.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CategoryUpdateRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name cannot exceed 100 characters")
        String name,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        @Size(max = 30, message = "Category code cannot exceed 30 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Category code may only contain letters, numbers, hyphens, and underscores")
        String code,

        UUID parentUuid

) {
    public CategoryUpdateRequest(String name, String description) {
        this(name, description, null, null);
    }
}
