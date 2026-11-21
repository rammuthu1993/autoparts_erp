-- Run as a PostgreSQL administrator using psql.
-- Example:
--   psql "$DB_ADMIN_URL" -f docs/sql/create-ai-readonly-role.sql
-- Then, inside the psql session, run: \password autoparts_ai_reader
--
-- This role is intentionally restricted to the exact tables/columns currently
-- queried by the AI tools. Do NOT grant SELECT ON ALL TABLES: that could expose
-- password hashes, refresh tokens, and future sensitive tables.
--
-- If a new AI tool needs another table, review its columns and explicitly add
-- that table/column to this allowlist after a security review.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'autoparts_ai_reader') THEN
        CREATE ROLE autoparts_ai_reader LOGIN;
    END IF;
END
$$;

ALTER ROLE autoparts_ai_reader
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT;
ALTER ROLE autoparts_ai_reader SET default_transaction_read_only = on;

GRANT CONNECT ON DATABASE autoparts_erp TO autoparts_ai_reader;
GRANT USAGE ON SCHEMA public TO autoparts_ai_reader;

-- Start from zero privileges for repeatable, least-privilege provisioning.
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM autoparts_ai_reader;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM autoparts_ai_reader;
REVOKE CREATE ON SCHEMA public FROM autoparts_ai_reader;

-- Also clear any prior column-level SELECT grants so re-running this script
-- cannot leave an old sensitive-column grant behind.
DO $$
DECLARE
    column_record RECORD;
BEGIN
    FOR column_record IN
        SELECT n.nspname, c.relname, a.attname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r', 'p')
          AND a.attnum > 0
          AND NOT a.attisdropped
    LOOP
        EXECUTE format(
            'REVOKE SELECT (%I) ON TABLE %I.%I FROM autoparts_ai_reader',
            column_record.attname, column_record.nspname, column_record.relname);
    END LOOP;
END
$$;

-- Explicit AI tool read allowlist. Review this list whenever tool SQL changes.
GRANT SELECT ON TABLE
    public.ai_tool_runs,
    public.audit_logs,
    public.cash_register_movements,
    public.cash_register_sessions,
    public.cash_registers,
    public.customers,
    public.daily_closings,
    public.expense_categories,
    public.expenses,
    public.inventory_adjustments,
    public.inventory_movements,
    public.products,
    public.purchase_products,
    public.purchase_return_products,
    public.purchase_returns,
    public.purchases,
    public.sales,
    public.sales_payments,
    public.sales_products,
    public.sales_refunds,
    public.sales_return_products,
    public.sales_returns,
    public.supplier_credit_applications,
    public.supplier_credits,
    public.supplier_refunds,
    public.suppliers,
    public.knowledge_documents,
    public.knowledge_chunks
TO autoparts_ai_reader;

-- FinancialLookupTool only needs the user identifier and username. Never grant
-- table-level SELECT on users (which would expose password hashes and tokens).
REVOKE ALL PRIVILEGES ON TABLE public.users FROM autoparts_ai_reader;
REVOKE SELECT (id, username) ON TABLE public.users FROM autoparts_ai_reader;
GRANT SELECT (id, username) ON TABLE public.users TO autoparts_ai_reader;

-- Do not use broad ALTER DEFAULT PRIVILEGES here. New tables must be reviewed
-- and explicitly added to the allowlist. The role must not inherit other roles.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_auth_members m
        JOIN pg_roles r ON r.oid = m.roleid
        JOIN pg_roles u ON u.oid = m.member
        WHERE u.rolname = 'autoparts_ai_reader'
    ) THEN
        RAISE EXCEPTION
            'autoparts_ai_reader is a member of another role; remove memberships before deployment';
    END IF;
END
$$;
