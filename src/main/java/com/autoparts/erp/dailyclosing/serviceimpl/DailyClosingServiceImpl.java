
package com.autoparts.erp.dailyclosing.serviceimpl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.dailyclosing.dto.DailyClosingResponse;
import com.autoparts.erp.dailyclosing.repository.DailyClosingRepository;
import com.autoparts.erp.dailyclosing.service.DailyClosingService;

@Service
@Transactional(readOnly = true)
public class DailyClosingServiceImpl
        implements DailyClosingService {

    private final DailyClosingRepository repository;
    private final CurrentUserService currentUserService;
    private final ZoneId businessZone;

    public DailyClosingServiceImpl(
        DailyClosingRepository repository,
        CurrentUserService currentUserService,

        @Value("${app.business.time-zone:Asia/Kolkata}")
        String businessTimeZone
    ) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.businessZone = ZoneId.of(businessTimeZone);
    }

    @Override
    @Transactional
    public DailyClosingResponse closeBusinessDate(
        LocalDate businessDate,
        String notes
    ) {

        if (businessDate == null) {
            throw badRequest("Business date is required");
        }

        LocalDate today = LocalDate.now(businessZone);

        if (businessDate.isAfter(today)) {
            throw badRequest(
                "Future business dates cannot be closed"
            );
        }

        if (repository.existsByBusinessDate(businessDate)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Daily closing already exists for this date"
            );
        }

        OffsetDateTime start = businessDate
            .atStartOfDay(businessZone)
            .toOffsetDateTime();

        OffsetDateTime end = businessDate
            .plusDays(1)
            .atStartOfDay(businessZone)
            .toOffsetDateTime();

        if (repository.hasOpenSessions(start, end)) {
            throw badRequest(
                "Close all cash register sessions opened on this date first"
            );
        }

        Long userId = currentUserService.getCurrentUserId();

        String cleanNotes =
            notes == null || notes.isBlank()
                ? null
                : notes.trim();

        try {
            return repository.createClosing(
                businessDate,
                start,
                end,
                userId,
                cleanNotes
            );
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Daily closing already exists for this date"
            );
        }
    }

    @Override
    public DailyClosingResponse findByBusinessDate(
        LocalDate businessDate
    ) {

        return repository.findByBusinessDate(businessDate)
            .orElseThrow(() -> notFound(
                "Daily closing not found for date: " + businessDate
            ));
    }

    @Override
    public DailyClosingResponse findByUuid(UUID uuid) {

        return repository.findByUuid(uuid)
            .orElseThrow(() -> notFound(
                "Daily closing not found"
            ));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            message
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            message
        );
    }
}
