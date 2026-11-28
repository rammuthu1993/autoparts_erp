package com.autoparts.erp.settings.repositoryimpl;

import com.autoparts.erp.settings.dto.AuditRetentionSettingsDto;
import com.autoparts.erp.settings.dto.BusinessProfileSettingsDto;
import com.autoparts.erp.settings.dto.FinancialYearSettingsDto;
import com.autoparts.erp.settings.dto.InvoiceSettingsDto;
import com.autoparts.erp.settings.repository.AdminSettingsRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public class AdminSettingsRepositoryImpl implements AdminSettingsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminSettingsRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<BusinessProfileSettingsDto> BUSINESS_PROFILE_MAPPER = (rs, rowNum) ->
            new BusinessProfileSettingsDto(
                    rs.getObject("uuid", UUID.class),
                    rs.getString("business_name"),
                    rs.getString("legal_name"),
                    rs.getString("gstin"),
                    rs.getString("pan"),
                    rs.getString("registered_address"),
                    rs.getString("shop_address"),
                    rs.getString("city"),
                    rs.getString("state"),
                    rs.getString("state_code"),
                    rs.getString("pincode"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    rs.getString("logo_url"),
                    rs.getString("currency"),
                    rs.getString("time_zone"),
                    toInstant(rs.getTimestamp("updated_at"))
            );

    private static final RowMapper<InvoiceSettingsDto> INVOICE_MAPPER = (rs, rowNum) ->
            new InvoiceSettingsDto(
                    rs.getObject("uuid", UUID.class),
                    rs.getString("invoice_prefix"),
                    rs.getInt("number_padding"),
                    rs.getString("fy_reset_policy"),
                    rs.getString("print_page_size"),
                    rs.getInt("copy_count"),
                    rs.getBoolean("show_gst_breakdown"),
                    rs.getBoolean("show_hsn_sac"),
                    rs.getString("terms_and_conditions"),
                    rs.getString("footer_text"),
                    toInstant(rs.getTimestamp("updated_at"))
            );

    private static final RowMapper<FinancialYearSettingsDto> FY_MAPPER = (rs, rowNum) ->
            new FinancialYearSettingsDto(
                    rs.getObject("uuid", UUID.class),
                    rs.getString("fy_label"),
                    rs.getObject("start_date", LocalDate.class),
                    rs.getObject("end_date", LocalDate.class),
                    rs.getObject("period_lock_date", LocalDate.class),
                    rs.getBoolean("prevent_locked_period_edits"),
                    null,
                    rs.getString("last_reopen_reason"),
                    rs.getBoolean("is_current"),
                    toInstant(rs.getTimestamp("updated_at"))
            );

    private static final RowMapper<AuditRetentionSettingsDto> RETENTION_MAPPER = (rs, rowNum) ->
            new AuditRetentionSettingsDto(
                    rs.getObject("uuid", UUID.class),
                    rs.getInt("retention_days"),
                    rs.getString("archive_frequency"),
                    rs.getString("archive_destination"),
                    toInstant(rs.getTimestamp("last_archive_at")),
                    rs.getString("last_archive_status"),
                    rs.getString("last_archive_error"),
                    toInstant(rs.getTimestamp("updated_at"))
            );

    @Override
    public BusinessProfileSettingsDto getBusinessProfile() {
        return jdbcTemplate.query(
                "SELECT * FROM business_profile_settings ORDER BY id ASC LIMIT 1",
                new MapSqlParameterSource(),
                BUSINESS_PROFILE_MAPPER
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public BusinessProfileSettingsDto updateBusinessProfile(BusinessProfileSettingsDto request) {
        String sql = """
                UPDATE business_profile_settings
                SET business_name = :businessName,
                    legal_name = :legalName,
                    gstin = :gstin,
                    pan = :pan,
                    registered_address = :registeredAddress,
                    shop_address = :shopAddress,
                    city = :city,
                    state = :state,
                    state_code = :stateCode,
                    pincode = :pincode,
                    phone = :phone,
                    email = :email,
                    logo_url = :logoUrl,
                    currency = :currency,
                    time_zone = :timeZone,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = (SELECT id FROM business_profile_settings ORDER BY id ASC LIMIT 1)
                RETURNING *
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("businessName", request.businessName())
                .addValue("legalName", request.legalName())
                .addValue("gstin", request.gstin())
                .addValue("pan", request.pan())
                .addValue("registeredAddress", request.registeredAddress())
                .addValue("shopAddress", request.shopAddress())
                .addValue("city", request.city())
                .addValue("state", request.state())
                .addValue("stateCode", request.stateCode())
                .addValue("pincode", request.pincode())
                .addValue("phone", request.phone())
                .addValue("email", request.email())
                .addValue("logoUrl", request.logoUrl())
                .addValue("currency", request.currency())
                .addValue("timeZone", request.timeZone());

        return jdbcTemplate.queryForObject(sql, params, BUSINESS_PROFILE_MAPPER);
    }

    @Override
    public InvoiceSettingsDto getInvoiceSettings() {
        return jdbcTemplate.query(
                "SELECT * FROM invoice_document_settings ORDER BY id ASC LIMIT 1",
                new MapSqlParameterSource(),
                INVOICE_MAPPER
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public InvoiceSettingsDto updateInvoiceSettings(InvoiceSettingsDto request) {
        String sql = """
                UPDATE invoice_document_settings
                SET invoice_prefix = :invoicePrefix,
                    number_padding = :numberPadding,
                    fy_reset_policy = :fyResetPolicy,
                    print_page_size = :printPageSize,
                    copy_count = :copyCount,
                    show_gst_breakdown = :showGstBreakdown,
                    show_hsn_sac = :showHsnSac,
                    terms_and_conditions = :termsAndConditions,
                    footer_text = :footerText,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = (SELECT id FROM invoice_document_settings ORDER BY id ASC LIMIT 1)
                RETURNING *
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("invoicePrefix", request.invoicePrefix())
                .addValue("numberPadding", request.numberPadding())
                .addValue("fyResetPolicy", request.fyResetPolicy())
                .addValue("printPageSize", request.printPageSize())
                .addValue("copyCount", request.copyCount())
                .addValue("showGstBreakdown", request.showGstBreakdown())
                .addValue("showHsnSac", request.showHsnSac())
                .addValue("termsAndConditions", request.termsAndConditions())
                .addValue("footerText", request.footerText());

        return jdbcTemplate.queryForObject(sql, params, INVOICE_MAPPER);
    }

    @Override
    public FinancialYearSettingsDto getFinancialYearSettings() {
        return jdbcTemplate.query(
                "SELECT * FROM financial_year_settings ORDER BY id ASC LIMIT 1",
                new MapSqlParameterSource(),
                FY_MAPPER
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public FinancialYearSettingsDto updateFinancialYearSettings(FinancialYearSettingsDto request) {
        String sql = """
                UPDATE financial_year_settings
                SET fy_label = :fyLabel,
                    start_date = :startDate,
                    end_date = :endDate,
                    period_lock_date = :periodLockDate,
                    prevent_locked_period_edits = :preventLockedPeriodEdits,
                    last_reopen_reason = COALESCE(:reopenReason, last_reopen_reason),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = (SELECT id FROM financial_year_settings ORDER BY id ASC LIMIT 1)
                RETURNING *
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fyLabel", request.fyLabel())
                .addValue("startDate", request.startDate())
                .addValue("endDate", request.endDate())
                .addValue("periodLockDate", request.periodLockDate())
                .addValue("preventLockedPeriodEdits", request.preventLockedPeriodEdits())
                .addValue("reopenReason", request.reopenReason());

        return jdbcTemplate.queryForObject(sql, params, FY_MAPPER);
    }

    @Override
    public AuditRetentionSettingsDto getAuditRetentionSettings() {
        return jdbcTemplate.query(
                "SELECT * FROM audit_retention_settings ORDER BY id ASC LIMIT 1",
                new MapSqlParameterSource(),
                RETENTION_MAPPER
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public AuditRetentionSettingsDto updateAuditRetentionSettings(AuditRetentionSettingsDto request) {
        String sql = """
                UPDATE audit_retention_settings
                SET retention_days = :retentionDays,
                    archive_frequency = :archiveFrequency,
                    archive_destination = :archiveDestination,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = (SELECT id FROM audit_retention_settings ORDER BY id ASC LIMIT 1)
                RETURNING *
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("retentionDays", request.retentionDays())
                .addValue("archiveFrequency", request.archiveFrequency())
                .addValue("archiveDestination", request.archiveDestination());

        return jdbcTemplate.queryForObject(sql, params, RETENTION_MAPPER);
    }

    @Override
    public boolean isDatabaseHealthy() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", new MapSqlParameterSource(), Integer.class);
        return result != null && result == 1;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
