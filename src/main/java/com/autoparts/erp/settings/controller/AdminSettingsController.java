package com.autoparts.erp.settings.controller;

import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.settings.dto.AuditRetentionSettingsDto;
import com.autoparts.erp.settings.dto.BusinessProfileSettingsDto;
import com.autoparts.erp.settings.dto.FinancialYearSettingsDto;
import com.autoparts.erp.settings.dto.InvoiceSettingsDto;
import com.autoparts.erp.settings.dto.RuntimeStatusResponse;
import com.autoparts.erp.settings.dto.SecurityPolicyResponse;
import com.autoparts.erp.settings.service.AdminSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
@AdminOnly
public class AdminSettingsController {

    private final AdminSettingsService service;

    public AdminSettingsController(AdminSettingsService service) {
        this.service = service;
    }

    @GetMapping("/business-profile")
    @RequiresPermission("SETTINGS.BUSINESS_PROFILE.VIEW")
    public ResponseEntity<BusinessProfileSettingsDto> getBusinessProfile() {
        return ResponseEntity.ok(service.getBusinessProfile());
    }

    @PutMapping("/business-profile")
    @RequiresPermission("SETTINGS.BUSINESS_PROFILE.UPDATE")
    public ResponseEntity<BusinessProfileSettingsDto> updateBusinessProfile(
            @Valid @RequestBody BusinessProfileSettingsDto request
    ) {
        return ResponseEntity.ok(service.updateBusinessProfile(request));
    }

    @GetMapping("/invoice")
    @RequiresPermission("SETTINGS.INVOICE.VIEW")
    public ResponseEntity<InvoiceSettingsDto> getInvoiceSettings() {
        return ResponseEntity.ok(service.getInvoiceSettings());
    }

    @PutMapping("/invoice")
    @RequiresPermission("SETTINGS.INVOICE.UPDATE")
    public ResponseEntity<InvoiceSettingsDto> updateInvoiceSettings(
            @Valid @RequestBody InvoiceSettingsDto request
    ) {
        return ResponseEntity.ok(service.updateInvoiceSettings(request));
    }

    @GetMapping("/financial-year")
    @RequiresPermission("SETTINGS.FINANCIAL_YEAR.VIEW")
    public ResponseEntity<FinancialYearSettingsDto> getFinancialYearSettings() {
        return ResponseEntity.ok(service.getFinancialYearSettings());
    }

    @PutMapping("/financial-year")
    @RequiresPermission("SETTINGS.FINANCIAL_YEAR.UPDATE")
    public ResponseEntity<FinancialYearSettingsDto> updateFinancialYearSettings(
            @Valid @RequestBody FinancialYearSettingsDto request
    ) {
        return ResponseEntity.ok(service.updateFinancialYearSettings(request));
    }

    @GetMapping("/audit-retention")
    @RequiresPermission("SETTINGS.AUDIT_RETENTION.VIEW")
    public ResponseEntity<AuditRetentionSettingsDto> getAuditRetentionSettings() {
        return ResponseEntity.ok(service.getAuditRetentionSettings());
    }

    @PutMapping("/audit-retention")
    @RequiresPermission("SETTINGS.AUDIT_RETENTION.UPDATE")
    public ResponseEntity<AuditRetentionSettingsDto> updateAuditRetentionSettings(
            @Valid @RequestBody AuditRetentionSettingsDto request
    ) {
        return ResponseEntity.ok(service.updateAuditRetentionSettings(request));
    }

    @GetMapping("/security-policy")
    @RequiresPermission("SETTINGS.SECURITY_POLICY.VIEW")
    public ResponseEntity<SecurityPolicyResponse> getSecurityPolicy() {
        return ResponseEntity.ok(service.getSecurityPolicy());
    }

    @GetMapping("/runtime-status")
    @RequiresPermission("SETTINGS.RUNTIME.VIEW")
    public ResponseEntity<RuntimeStatusResponse> getRuntimeStatus() {
        return ResponseEntity.ok(service.getRuntimeStatus());
    }
}
