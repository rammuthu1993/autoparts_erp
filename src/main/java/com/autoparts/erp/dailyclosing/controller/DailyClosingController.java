
package com.autoparts.erp.dailyclosing.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.dailyclosing.dto.DailyClosingResponse;
import com.autoparts.erp.dailyclosing.service.DailyClosingService;

import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/daily-closings")
public class DailyClosingController {

    private final DailyClosingService service;

    public DailyClosingController(
        DailyClosingService service
    ) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DailyClosingResponse> closeBusinessDate(

        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate businessDate,

        @RequestParam(required = false)
        @Size(max = 1000)
        String notes

    ) {

        DailyClosingResponse response =
            service.closeBusinessDate(businessDate, notes);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    @GetMapping("/date/{businessDate}")
    public ResponseEntity<DailyClosingResponse> findByBusinessDate(

        @PathVariable
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate businessDate

    ) {

        return ResponseEntity.ok(
            service.findByBusinessDate(businessDate)
        );
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<DailyClosingResponse> findByUuid(
        @PathVariable UUID uuid
    ) {

        return ResponseEntity.ok(
            service.findByUuid(uuid)
        );
    }
}
