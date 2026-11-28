package com.autoparts.erp.customer.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Customer(
        Long id,
        UUID uuid,
        String customerCode,
        String customerName,
        String customerType,
        String contactPerson,
        String phone,
        String alternatePhone,
        String email,
        String address,
        String shippingAddress,
        String city,
        String state,
        String pincode,
        String gstin,
        String gstRegistrationType,
        BigDecimal creditLimit,
        Integer creditPeriodDays,
        BigDecimal outstandingBalance,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public Customer(
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
        this(
                id, uuid, customerCode, customerName, "RETAIL", null,
                phone, null, email, address, null, city, state, pincode,
                gstin, null, null, null, BigDecimal.ZERO, active, createdAt, updatedAt
        );
    }
}
