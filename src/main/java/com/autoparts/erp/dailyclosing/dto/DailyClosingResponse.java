
package com.autoparts.erp.dailyclosing.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DailyClosingResponse(

    UUID uuid,
    LocalDate businessDate,

    Long closedBy,
    OffsetDateTime closedAt,

    String notes,

    DailyClosingSummary summary,

    List<CashRegisterClosingResponse> cashRegisters

) {}
