package com.autoparts.erp.cashregister.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCashRegisterRequest(

    @NotBlank(message = "Register code is required")
    @Size(max = 30)
    String registerCode,

    @NotBlank(message = "Register name is required")
    @Size(max = 100)
    String name,

    @Size(max = 300)
    String description
) {}