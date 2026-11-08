
package com.autoparts.erp.dailyclosing.serviceimpl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.AuthenticatedUser;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.dailyclosing.dto.DailyClosingResponse;
import com.autoparts.erp.dailyclosing.repository.DailyClosingRepository;
import com.autoparts.erp.dailyclosing.service.DailyClosingService;

@Service
@Transactional(readOnly = true)
public class DailyClosingServiceImpl
        implements DailyClosingService {

    private final DailyClosingRepository repository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final ZoneId businessZone;

    public DailyClosingServiceImpl(
            DailyClosingRepository repository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            @Value("${app.business.time-zone:Asia/Kolkata}")
            String businessTimeZone
    ) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.businessZone = ZoneId.of(businessTimeZone);
    }

    // ---------------------------------------------------------
    // CLOSE BUSINESS DATE
    // ADMIN / MANAGER ONLY
    // ---------------------------------------------------------

    @Override
    @Transactional
    @AdminOrManager
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

        // Calculate business-day boundaries using configured timezone.
        OffsetDateTime start = businessDate
                .atStartOfDay(businessZone)
                .toOffsetDateTime();

        OffsetDateTime end = businessDate
                .plusDays(1)
                .atStartOfDay(businessZone)
                .toOffsetDateTime();

        // Ensure all cash-register sessions are closed first.
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

            // Create daily closing.
            DailyClosingResponse closing =
                    repository.createClosing(
                            businessDate,
                            start,
                            end,
                            userId,
                            cleanNotes
                    );

            // Prepare audit information.
            Map<String, Object> auditData = new HashMap<>();

            auditData.put(
                    "businessDate",
                    businessDate.toString()
            );

            auditData.put(
                    "startDateTime",
                    start.toString()
            );

            auditData.put(
                    "endDateTime",
                    end.toString()
            );

            auditData.put(
                    "closedByUserId",
                    userId
            );

            auditData.put(
                    "notes",
                    cleanNotes
            );

            // Record audit log.
            auditLogService.log(
                    "CREATE",
                    "DAILY_CLOSING",
                    businessDate.toString(),
                    "Daily business closing created",
                    null,
                    auditData
            );

            return closing;

        } catch (DuplicateKeyException ex) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Daily closing already exists for this date"
            );
        }
    }

    // ---------------------------------------------------------
    // FIND DAILY CLOSING BY BUSINESS DATE
    // ADMIN / MANAGER / CASHIER
    // ---------------------------------------------------------

    @Override
    @AuthenticatedUser
    public DailyClosingResponse findByBusinessDate(
            LocalDate businessDate
    ) {

        if (businessDate == null) {
            throw badRequest("Business date is required");
        }

        return repository.findByBusinessDate(businessDate)
                .orElseThrow(() ->
                        notFound(
                                "Daily closing not found for date: "
                                        + businessDate
                        )
                );
    }

    // ---------------------------------------------------------
    // FIND DAILY CLOSING BY UUID
    // ADMIN / MANAGER / CASHIER
    // ---------------------------------------------------------

    @Override
    @AuthenticatedUser
    public DailyClosingResponse findByUuid(UUID uuid) {

        if (uuid == null) {
            throw badRequest("Daily closing UUID is required");
        }

        return repository.findByUuid(uuid)
                .orElseThrow(() ->
                        notFound("Daily closing not found")
                );
    }

    // ---------------------------------------------------------
    // BAD REQUEST EXCEPTION
    // ---------------------------------------------------------

    private ResponseStatusException badRequest(String message) {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    // ---------------------------------------------------------
    // NOT FOUND EXCEPTION
    // ---------------------------------------------------------

    private ResponseStatusException notFound(String message) {

        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }
}
