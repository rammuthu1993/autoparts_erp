package com.autoparts.erp.settings.repository;

import com.autoparts.erp.settings.dto.AuditRetentionSettingsDto;
import com.autoparts.erp.settings.dto.BusinessProfileSettingsDto;
import com.autoparts.erp.settings.dto.FinancialYearSettingsDto;
import com.autoparts.erp.settings.dto.InvoiceSettingsDto;

public interface AdminSettingsRepository {

    BusinessProfileSettingsDto getBusinessProfile();

    BusinessProfileSettingsDto updateBusinessProfile(BusinessProfileSettingsDto request);

    InvoiceSettingsDto getInvoiceSettings();

    InvoiceSettingsDto updateInvoiceSettings(InvoiceSettingsDto request);

    FinancialYearSettingsDto getFinancialYearSettings();

    FinancialYearSettingsDto updateFinancialYearSettings(FinancialYearSettingsDto request);

    AuditRetentionSettingsDto getAuditRetentionSettings();

    AuditRetentionSettingsDto updateAuditRetentionSettings(AuditRetentionSettingsDto request);

    boolean isDatabaseHealthy();
}
