
package com.autoparts.erp.dailyclosing.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DailyClosing(

    Long id,
    UUID uuid,
    LocalDate businessDate,
    Long closedBy,
    OffsetDateTime closedAt,
    String notes

) {}
