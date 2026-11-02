package com.autoparts.erp.customer.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Customer(
        Long id,
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