
package com.autoparts.erp.dailyclosing.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.autoparts.erp.dailyclosing.dto.DailyClosingResponse;

public interface DailyClosingRepository {

    boolean existsByBusinessDate(LocalDate businessDate);

    boolean hasOpenSessions(
        OffsetDateTime start,
        OffsetDateTime end
    );

    DailyClosingResponse createClosing(
        LocalDate businessDate,
        OffsetDateTime start,
        OffsetDateTime end,
        Long closedBy,
        String notes
    );

    Optional<DailyClosingResponse> findByBusinessDate(
        LocalDate businessDate
    );

    Optional<DailyClosingResponse> findByUuid(UUID uuid);

    List<DailyClosingResponse> findPaged(
        int offset,
        int limit,
        LocalDate fromDate,
        LocalDate toDate,
        String sortBy,
        String sortDir
    );

    long countPaged(
        LocalDate fromDate,
        LocalDate toDate
    );
}
