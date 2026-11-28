package com.autoparts.erp.settings.serviceimpl;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.settings.dto.AuditRetentionSettingsDto;
import com.autoparts.erp.settings.dto.BusinessProfileSettingsDto;
import com.autoparts.erp.settings.dto.FinancialYearSettingsDto;
import com.autoparts.erp.settings.dto.InvoiceSettingsDto;
import com.autoparts.erp.settings.dto.RuntimeStatusResponse;
import com.autoparts.erp.settings.dto.SecurityPolicyResponse;
import com.autoparts.erp.settings.repository.AdminSettingsRepository;
import com.autoparts.erp.settings.service.AdminSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminSettingsServiceImpl implements AdminSettingsService {

    private final AdminSettingsRepository repository;
    private final AuditLogService auditLogService;
    private final Environment environment;

    private final int minPasswordLength;
    private final int maxPasswordLength;
    private final int maxFailedLoginAttempts;
    private final long lockDurationSeconds;
    private final long jwtExpirationSeconds;
    private final long refreshTokenExpirationSeconds;
    private final String applicationName;
    private final boolean mailEnabled;
    private final String mailHost;
    private final String businessTimeZone;

    public AdminSettingsServiceImpl(
            AdminSettingsRepository repository,
            AuditLogService auditLogService,
            Environment environment,
            @Value("${app.security.password.min-length:12}") int minPasswordLength,
            @Value("${app.security.password.max-length:128}") int maxPasswordLength,
            @Value("${app.security.login.max-failed-attempts:5}") int maxFailedLoginAttempts,
            @Value("${app.security.login.lock-duration-seconds:86400}") long lockDurationSeconds,
            @Value("${app.security.jwt.expiration-seconds:86400}") long jwtExpirationSeconds,
            @Value("${app.security.refresh-token.expiration-seconds:604800}") long refreshTokenExpirationSeconds,
            @Value("${spring.application.name:autoparts-erp}") String applicationName,
            @Value("${app.mail.enabled:true}") boolean mailEnabled,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.business.time-zone:Asia/Kolkata}") String businessTimeZone
    ) {
        this.repository = repository;
        this.auditLogService = auditLogService;
        this.environment = environment;
        this.minPasswordLength = minPasswordLength;
        this.maxPasswordLength = maxPasswordLength;
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
        this.lockDurationSeconds = lockDurationSeconds;
        this.jwtExpirationSeconds = jwtExpirationSeconds;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
        this.applicationName = applicationName;
        this.mailEnabled = mailEnabled;
        this.mailHost = mailHost;
        this.businessTimeZone = businessTimeZone;
    }

    @Override
    @RequiresPermission("SETTINGS.BUSINESS_PROFILE.VIEW")
    public BusinessProfileSettingsDto getBusinessProfile() {
        return repository.getBusinessProfile();
    }

    @Override
    @Transactional
    @RequiresPermission("SETTINGS.BUSINESS_PROFILE.UPDATE")
    public BusinessProfileSettingsDto updateBusinessProfile(BusinessProfileSettingsDto request) {
        BusinessProfileSettingsDto existing = repository.getBusinessProfile();
        BusinessProfileSettingsDto normalized = new BusinessProfileSettingsDto(
                existing.uuid(),
                cleanRequired(request.businessName()),
                clean(request.legalName()),
                upper(request.gstin()),
                upper(request.pan()),
                clean(request.registeredAddress()),
                clean(request.shopAddress()),
                clean(request.city()),
                clean(request.state()),
                clean(request.stateCode()),
                clean(request.pincode()),
                clean(request.phone()),
                lower(request.email()),
                clean(request.logoUrl()),
                upper(cleanRequired(request.currency())),
                cleanRequired(request.timeZone()),
                null
        );

        BusinessProfileSettingsDto saved = repository.updateBusinessProfile(normalized);

        auditLogService.log(
                "UPDATE",
                "SETTINGS_BUSINESS_PROFILE",
                saved.uuid().toString(),
                "Business profile settings updated",
                toBusinessAudit(existing),
                toBusinessAudit(saved)
        );
        return saved;
    }

    @Override
    @RequiresPermission("SETTINGS.INVOICE.VIEW")
    public InvoiceSettingsDto getInvoiceSettings() {
        return repository.getInvoiceSettings();
    }

    @Override
    @Transactional
    @RequiresPermission("SETTINGS.INVOICE.UPDATE")
    public InvoiceSettingsDto updateInvoiceSettings(InvoiceSettingsDto request) {
        InvoiceSettingsDto existing = repository.getInvoiceSettings();
        InvoiceSettingsDto normalized = new InvoiceSettingsDto(
                existing.uuid(),
                upper(cleanRequired(request.invoicePrefix())),
                request.numberPadding(),
                upper(cleanRequired(request.fyResetPolicy())),
                upper(cleanRequired(request.printPageSize())),
                request.copyCount(),
                request.showGstBreakdown(),
                request.showHsnSac(),
                clean(request.termsAndConditions()),
                clean(request.footerText()),
                null
        );

        InvoiceSettingsDto saved = repository.updateInvoiceSettings(normalized);

        auditLogService.log(
                "UPDATE",
                "SETTINGS_INVOICE",
                saved.uuid().toString(),
                "Invoice and document settings updated",
                toInvoiceAudit(existing),
                toInvoiceAudit(saved)
        );
        return saved;
    }

    @Override
    @RequiresPermission("SETTINGS.FINANCIAL_YEAR.VIEW")
    public FinancialYearSettingsDto getFinancialYearSettings() {
        return repository.getFinancialYearSettings();
    }

    @Override
    @Transactional
    @RequiresPermission("SETTINGS.FINANCIAL_YEAR.UPDATE")
    public FinancialYearSettingsDto updateFinancialYearSettings(FinancialYearSettingsDto request) {
        if (!request.startDate().isBefore(request.endDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Financial year start date must precede end date"
            );
        }

        FinancialYearSettingsDto existing = repository.getFinancialYearSettings();
        boolean reopeningPeriod = existing.periodLockDate() != null
                && (request.periodLockDate() == null || request.periodLockDate().isBefore(existing.periodLockDate()));
        String reopenReason = clean(request.reopenReason());

        if (reopeningPeriod && reopenReason == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A reason is required when reopening a locked accounting period"
            );
        }

        FinancialYearSettingsDto normalized = new FinancialYearSettingsDto(
                existing.uuid(),
                cleanRequired(request.fyLabel()),
                request.startDate(),
                request.endDate(),
                request.periodLockDate(),
                request.preventLockedPeriodEdits(),
                reopeningPeriod ? reopenReason : null,
                existing.lastReopenReason(),
                existing.current(),
                null
        );

        FinancialYearSettingsDto saved = repository.updateFinancialYearSettings(normalized);

        auditLogService.log(
                "UPDATE",
                "SETTINGS_FINANCIAL_YEAR",
                saved.uuid().toString(),
                reopeningPeriod ? "Financial year period reopened: " + reopenReason : "Financial year settings updated",
                toFyAudit(existing),
                toFyAudit(saved)
        );
        return saved;
    }

    @Override
    @RequiresPermission("SETTINGS.AUDIT_RETENTION.VIEW")
    public AuditRetentionSettingsDto getAuditRetentionSettings() {
        return repository.getAuditRetentionSettings();
    }

    @Override
    @Transactional
    @RequiresPermission("SETTINGS.AUDIT_RETENTION.UPDATE")
    public AuditRetentionSettingsDto updateAuditRetentionSettings(AuditRetentionSettingsDto request) {
        AuditRetentionSettingsDto existing = repository.getAuditRetentionSettings();
        AuditRetentionSettingsDto normalized = new AuditRetentionSettingsDto(
                existing.uuid(),
                request.retentionDays(),
                upper(cleanRequired(request.archiveFrequency())),
                cleanRequired(request.archiveDestination()),
                existing.lastArchiveAt(),
                existing.lastArchiveStatus(),
                existing.lastArchiveError(),
                null
        );

        AuditRetentionSettingsDto saved = repository.updateAuditRetentionSettings(normalized);

        auditLogService.log(
                "UPDATE",
                "SETTINGS_AUDIT_RETENTION",
                saved.uuid().toString(),
                "Audit retention settings updated",
                toRetentionAudit(existing),
                toRetentionAudit(saved)
        );
        return saved;
    }

    @Override
    @RequiresPermission("SETTINGS.SECURITY_POLICY.VIEW")
    public SecurityPolicyResponse getSecurityPolicy() {
        return new SecurityPolicyResponse(
                minPasswordLength,
                maxPasswordLength,
                maxFailedLoginAttempts,
                lockDurationSeconds,
                jwtExpirationSeconds,
                refreshTokenExpirationSeconds,
                true,
                "Enforced by Spring Security backend configuration."
        );
    }

    @Override
    @RequiresPermission("SETTINGS.RUNTIME.VIEW")
    public RuntimeStatusResponse getRuntimeStatus() {
        List<String> profiles = environment.getActiveProfiles().length > 0
                ? Arrays.asList(environment.getActiveProfiles())
                : Arrays.asList(environment.getDefaultProfiles());

        boolean dbUp = false;
        try {
            dbUp = repository.isDatabaseHealthy();
        } catch (Exception ignored) {
            dbUp = false;
        }

        return new RuntimeStatusResponse(
                applicationName,
                profiles,
                dbUp,
                mailEnabled,
                mailHost != null && !mailHost.isBlank(),
                businessTimeZone,
                Instant.now()
        );
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String cleanRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String upper(String value) {
        String c = clean(value);
        return c == null ? null : c.toUpperCase(Locale.ROOT);
    }

    private String lower(String value) {
        String c = clean(value);
        return c == null ? null : c.toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> toBusinessAudit(BusinessProfileSettingsDto dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessName", dto.businessName());
        m.put("legalName", dto.legalName());
        m.put("gstin", dto.gstin());
        m.put("city", dto.city());
        m.put("state", dto.state());
        m.put("currency", dto.currency());
        m.put("timeZone", dto.timeZone());
        return m;
    }

    private Map<String, Object> toInvoiceAudit(InvoiceSettingsDto dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invoicePrefix", dto.invoicePrefix());
        m.put("numberPadding", dto.numberPadding());
        m.put("fyResetPolicy", dto.fyResetPolicy());
        m.put("printPageSize", dto.printPageSize());
        m.put("copyCount", dto.copyCount());
        m.put("showGstBreakdown", dto.showGstBreakdown());
        m.put("showHsnSac", dto.showHsnSac());
        return m;
    }

    private Map<String, Object> toFyAudit(FinancialYearSettingsDto dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fyLabel", dto.fyLabel());
        m.put("startDate", dto.startDate() != null ? dto.startDate().toString() : null);
        m.put("endDate", dto.endDate() != null ? dto.endDate().toString() : null);
        m.put("periodLockDate", dto.periodLockDate() != null ? dto.periodLockDate().toString() : null);
        m.put("preventLockedPeriodEdits", dto.preventLockedPeriodEdits());
        return m;
    }

    private Map<String, Object> toRetentionAudit(AuditRetentionSettingsDto dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("retentionDays", dto.retentionDays());
        m.put("archiveFrequency", dto.archiveFrequency());
        m.put("archiveDestination", dto.archiveDestination());
        return m;
    }
}
