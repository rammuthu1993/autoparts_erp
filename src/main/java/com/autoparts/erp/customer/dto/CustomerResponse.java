package com.autoparts.erp.customer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID uuid,
        String customerCode,
        String customerName,
        String phone,
        String email,
        String address,
        String city,
        String state,
        String pincode,
        String gstin,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}