package com.autoparts.erp.inventory.dto;

import java.util.List;

public record InventoryPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {}