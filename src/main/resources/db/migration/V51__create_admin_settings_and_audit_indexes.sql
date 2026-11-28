-- Add audit query indexes and domain-specific persisted settings tables

CREATE INDEX IF NOT EXISTS idx_audit_logs_username_lower
    ON audit_logs (LOWER(username));

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_id
    ON audit_logs (entity_id);

-- 1. Business Profile Settings (single-shop initial model)
CREATE TABLE IF NOT EXISTS business_profile_settings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    business_name VARCHAR(200) NOT NULL,
    legal_name VARCHAR(200),
    gstin VARCHAR(15),
    pan VARCHAR(10),
    registered_address VARCHAR(500),
    shop_address VARCHAR(500),
    city VARCHAR(100),
    state VARCHAR(100),
    state_code VARCHAR(10),
    pincode VARCHAR(10),
    phone VARCHAR(20),
    email VARCHAR(150),
    logo_url VARCHAR(500),
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    time_zone VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO business_profile_settings (
    business_name,
    legal_name,
    currency,
    time_zone
)
SELECT
    'Guhan Auto Spares',
    'Guhan Auto Spares',
    'INR',
    'Asia/Kolkata'
WHERE NOT EXISTS (SELECT 1 FROM business_profile_settings);

-- 2. Invoice & Document Settings
CREATE TABLE IF NOT EXISTS invoice_document_settings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    invoice_prefix VARCHAR(20) NOT NULL DEFAULT 'INV',
    number_padding INTEGER NOT NULL DEFAULT 6 CHECK (number_padding BETWEEN 4 AND 10),
    fy_reset_policy VARCHAR(30) NOT NULL DEFAULT 'ANNUAL_FY' CHECK (fy_reset_policy IN ('NONE', 'ANNUAL_FY')),
    print_page_size VARCHAR(30) NOT NULL DEFAULT 'A4' CHECK (print_page_size IN ('A4', 'A5', 'THERMAL_80MM')),
    copy_count INTEGER NOT NULL DEFAULT 1 CHECK (copy_count BETWEEN 1 AND 5),
    show_gst_breakdown BOOLEAN NOT NULL DEFAULT TRUE,
    show_hsn_sac BOOLEAN NOT NULL DEFAULT TRUE,
    terms_and_conditions VARCHAR(1000),
    footer_text VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO invoice_document_settings (
    invoice_prefix,
    number_padding,
    fy_reset_policy,
    print_page_size,
    copy_count,
    show_gst_breakdown,
    show_hsn_sac,
    footer_text
)
SELECT
    'INV',
    6,
    'ANNUAL_FY',
    'A4',
    1,
    TRUE,
    TRUE,
    'Thank you for your business!'
WHERE NOT EXISTS (SELECT 1 FROM invoice_document_settings);

-- 3. Financial Year Settings
CREATE TABLE IF NOT EXISTS financial_year_settings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    fy_label VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    period_lock_date DATE,
    prevent_locked_period_edits BOOLEAN NOT NULL DEFAULT TRUE,
    last_reopen_reason VARCHAR(500),
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_financial_year_dates CHECK (start_date < end_date)
);

INSERT INTO financial_year_settings (
    fy_label,
    start_date,
    end_date,
    prevent_locked_period_edits,
    is_current
)
SELECT
    'FY 2026-27',
    DATE '2026-04-01',
    DATE '2027-03-31',
    TRUE,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM financial_year_settings);

-- 4. Audit Retention Settings
CREATE TABLE IF NOT EXISTS audit_retention_settings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    retention_days INTEGER NOT NULL DEFAULT 365 CHECK (retention_days >= 30 AND retention_days <= 3650),
    archive_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY' CHECK (archive_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    archive_destination VARCHAR(255) NOT NULL DEFAULT 'server-managed-archive',
    last_archive_at TIMESTAMPTZ,
    last_archive_status VARCHAR(30) NOT NULL DEFAULT 'NOT_RUN',
    last_archive_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO audit_retention_settings (
    retention_days,
    archive_frequency,
    archive_destination,
    last_archive_status
)
SELECT
    365,
    'MONTHLY',
    'server-managed-archive',
    'NOT_RUN'
WHERE NOT EXISTS (SELECT 1 FROM audit_retention_settings);

-- 5. Settings Permissions (least privilege: ADMIN only)
INSERT INTO permissions (code, name, description, module, action) VALUES
    ('SETTINGS.BUSINESS_PROFILE.VIEW', 'View Business Profile Settings', 'Read business profile settings', 'SETTINGS', 'VIEW'),
    ('SETTINGS.BUSINESS_PROFILE.UPDATE', 'Update Business Profile Settings', 'Update business profile settings', 'SETTINGS', 'UPDATE'),
    ('SETTINGS.INVOICE.VIEW', 'View Invoice Settings', 'Read invoice and document settings', 'SETTINGS', 'VIEW'),
    ('SETTINGS.INVOICE.UPDATE', 'Update Invoice Settings', 'Update invoice and document settings', 'SETTINGS', 'UPDATE'),
    ('SETTINGS.FINANCIAL_YEAR.VIEW', 'View Financial Year Settings', 'Read financial year settings', 'SETTINGS', 'VIEW'),
    ('SETTINGS.FINANCIAL_YEAR.UPDATE', 'Update Financial Year Settings', 'Update financial year settings', 'SETTINGS', 'UPDATE'),
    ('SETTINGS.AUDIT_RETENTION.VIEW', 'View Audit Retention Settings', 'Read audit retention settings', 'SETTINGS', 'VIEW'),
    ('SETTINGS.AUDIT_RETENTION.UPDATE', 'Update Audit Retention Settings', 'Update audit retention settings', 'SETTINGS', 'UPDATE'),
    ('SETTINGS.SECURITY_POLICY.VIEW', 'View Security Policy', 'Read non-secret server security policy', 'SETTINGS', 'VIEW'),
    ('SETTINGS.RUNTIME.VIEW', 'View Runtime Status', 'Read non-secret runtime health status', 'SETTINGS', 'VIEW')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.module = 'SETTINGS'
ON CONFLICT (role_id, permission_id) DO NOTHING;
