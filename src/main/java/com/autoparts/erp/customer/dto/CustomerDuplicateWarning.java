package com.autoparts.erp.customer.dto;

import java.util.List;
import java.util.UUID;

public record CustomerDuplicateWarning(
        UUID uuid,
        String customerCode,
        String customerName,
        String phone,
        String email,
        String gstin,
        boolean active,
        List<String> matchedFields
) {}
