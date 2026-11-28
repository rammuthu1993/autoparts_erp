package com.autoparts.erp.dailyclosing.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyClosingPreviewResponse(
    LocalDate businessDate,
    boolean alreadyClosed,
    boolean canClose,
    boolean hasOpenSessions,
    long openSessionCount,
    DailyClosingSummary summary,
    List<CashRegisterClosingResponse> cashRegisters,
    List<String> validationErrors
) {}
