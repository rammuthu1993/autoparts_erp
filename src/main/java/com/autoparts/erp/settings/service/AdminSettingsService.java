package com.autoparts.erp.settings.service;

import com.autoparts.erp.settings.dto.AuditRetentionSettingsDto;
import com.autoparts.erp.settings.dto.BusinessProfileSettingsDto;
import com.autoparts.erp.settings.dto.FinancialYearSettingsDto;
import com.autoparts.erp.settings.dto.InvoiceSettingsDto;
import com.autoparts.erp.settings.dto.RuntimeStatusResponse;
import com.autoparts.erp.settings.dto.SecurityPolicyResponse;

public interface AdminSettingsService {

    BusinessProfileSettingsDto getBusinessProfile();

    BusinessProfileSettingsDto updateBusinessProfile(BusinessProfileSettingsDto request);

    InvoiceSettingsDto getInvoiceSettings();

    InvoiceSettingsDto updateInvoiceSettings(InvoiceSettingsDto request);

    FinancialYearSettingsDto getFinancialYearSettings();

    FinancialYearSettingsDto updateFinancialYearSettings(FinancialYearSettingsDto request);

    AuditRetentionSettingsDto getAuditRetentionSettings();

    AuditRetentionSettingsDto updateAuditRetentionSettings(AuditRetentionSettingsDto request);

    SecurityPolicyResponse getSecurityPolicy();

    RuntimeStatusResponse getRuntimeStatus();
}
