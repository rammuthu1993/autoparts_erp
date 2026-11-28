package com.autoparts.erp.sales.enums;

/**
 * Invoice-level return status exposed by the Sales register.
 *
 * The first three values are retained for compatibility with existing
 * return workflows; the invoice summary values are used by SalesResponse
 * and the paginated Sales register.
 */
public enum SalesReturnStatus {
    ACCEPTED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    NO_RETURNS,
    PARTIALLY_RETURNED,
    FULLY_RETURNED
}
