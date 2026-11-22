package com.autoparts.erp.common.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int safeSize = Math.max(1, size);
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);
        boolean first = page <= 0;
        boolean last = totalPages == 0 || page >= totalPages - 1;
        return new PageResponse<>(content, page, safeSize, totalElements, totalPages, first, last);
    }
}
