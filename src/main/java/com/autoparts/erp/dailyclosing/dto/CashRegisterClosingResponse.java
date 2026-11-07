
package com.autoparts.erp.dailyclosing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CashRegisterClosingResponse(

    UUID sessionUuid,
    String registerName,

    BigDecimal openingBalance,
    BigDecimal totalCashIn,
    BigDecimal totalCashOut,

    BigDecimal expectedClosingBalance,
    BigDecimal actualClosingBalance,
    BigDecimal differenceAmount

) {}
