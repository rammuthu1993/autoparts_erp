
package com.autoparts.erp.masters.supplier.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierResponse(
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
) {}
