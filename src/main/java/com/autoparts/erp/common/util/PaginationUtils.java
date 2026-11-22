package com.autoparts.erp.common.util;

import com.autoparts.erp.common.exception.ClientValidationException;

/**
 * Shared utility for safe pagination parameter normalization and offset calculation.
 * Prevents 32-bit integer overflow when calculating SQL query offsets.
 */
public final class PaginationUtils {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PaginationUtils() {
    }

    /**
     * Normalizes page and size, calculates offset using 64-bit long arithmetic,
     * and validates that the resulting offset does not exceed {@link Integer#MAX_VALUE}.
     *
     * @param page raw page index requested by client (e.g. 0, 1, ..., or negative)
     * @param size raw page size requested by client (e.g. 20, or > 100)
     * @return 32-bit non-negative integer offset safe for SQL repository calls
     * @throws ClientValidationException if (long) safePage * safeSize exceeds Integer.MAX_VALUE
     */
    public static int calculateOffset(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = normalizeSize(size);
        return calculateOffsetForNormalized(safePage, safeSize);
    }

    /**
     * Normalizes page size with standard bounds (1 to 100, default 20 if non-positive).
     *
     * @param size raw page size
     * @return clamped page size between 1 and 100
     */
    public static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Calculates offset from already-normalized page and size using 64-bit long arithmetic,
     * and validates that the resulting offset does not exceed {@link Integer#MAX_VALUE}.
     *
     * @param safePage non-negative page index
     * @param safeSize positive page size in range [1, 100]
     * @return 32-bit non-negative integer offset
     * @throws ClientValidationException if (long) safePage * safeSize exceeds Integer.MAX_VALUE
     */
    public static int calculateOffsetForNormalized(int safePage, int safeSize) {
        long offset = (long) safePage * (long) safeSize;
        if (offset > Integer.MAX_VALUE) {
            throw new ClientValidationException(
                    "Pagination offset exceeds maximum supported limit (" + offset + " > " + Integer.MAX_VALUE + ")"
            );
        }
        return (int) offset;
    }
}
