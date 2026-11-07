
package com.autoparts.erp.dailyclosing.service;

import java.time.LocalDate;
import java.util.UUID;

import com.autoparts.erp.dailyclosing.dto.DailyClosingResponse;

public interface DailyClosingService {

    DailyClosingResponse closeBusinessDate(
        LocalDate businessDate,
        String notes
    );

    DailyClosingResponse findByBusinessDate(
        LocalDate businessDate
    );

    DailyClosingResponse findByUuid(UUID uuid);
}
