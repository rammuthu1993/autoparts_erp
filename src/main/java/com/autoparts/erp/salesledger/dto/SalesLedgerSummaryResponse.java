package com.autoparts.erp.salesledger.dto;

import java.math.BigDecimal;

public record SalesLedgerSummaryResponse(
    BigDecimal openingBalance,
    BigDecimal totalDebit,
    BigDecimal totalCredit,
    BigDecimal closingBalance
) {}