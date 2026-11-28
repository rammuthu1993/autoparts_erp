package com.autoparts.erp.supplier.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierResponse(
    Long id,
    UUID uuid,
    String supplierCode,
    String supplierName,
    String supplierType,
    String contactPerson,
    String phone,
    String alternatePhone,
    String email,
    String address,
    String city,
    String state,
    String pincode,
    String gstin,
    String gstRegistrationType,
    Integer paymentTermsDays,
    BigDecimal creditLimit,
    BigDecimal outstandingBalance,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public SupplierResponse(
        Long id,
        UUID uuid,
        String supplierName,
        String contactPerson,
        String phone,
        String email,
        String address,
        String city,
        String state,
        String pincode,
        String gstin,
        Integer paymentTermsDays,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
        this(
            id, uuid, null, supplierName, "DISTRIBUTOR", contactPerson,
            phone, null, email, address, city, state, pincode,
            gstin, null, paymentTermsDays, null, BigDecimal.ZERO,
            active, createdAt, updatedAt
        );
    }
}
