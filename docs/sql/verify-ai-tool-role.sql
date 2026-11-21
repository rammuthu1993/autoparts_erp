-- Run this script while connected as autoparts_ai_reader to the target database.
-- It raises an exception if the role has inherited privileges, table-level write
-- access, schema CREATE access, or table-level SELECT on users.

DO $$
DECLARE
    current_role_name TEXT := current_user;
    has_bad_membership BOOLEAN;
    has_bad_table_privilege BOOLEAN;
    has_users_table_select BOOLEAN;
    has_users_sensitive_column_select BOOLEAN;
    required_table TEXT;
    required_tables TEXT[] := ARRAY[
        'ai_tool_runs', 'audit_logs', 'cash_register_movements',
        'cash_register_sessions', 'cash_registers', 'customers',
        'daily_closings', 'expense_categories', 'expenses',
        'inventory_adjustments', 'inventory_movements', 'products',
        'purchase_products', 'purchase_return_products', 'purchase_returns',
        'purchases', 'sales', 'sales_payments', 'sales_products',
        'sales_refunds', 'sales_return_products', 'sales_returns',
        'supplier_credit_applications', 'supplier_credits', 'supplier_refunds',
        'suppliers', 'knowledge_documents', 'knowledge_chunks'
    ];
BEGIN
    IF current_role_name <> 'autoparts_ai_reader' THEN
        RAISE EXCEPTION 'Connect as autoparts_ai_reader; current_user is %', current_role_name;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_roles
        WHERE rolname = current_role_name
          AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'AI reader role has elevated role attributes';
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM pg_auth_members m
        JOIN pg_roles member_role ON member_role.oid = m.member
        WHERE member_role.rolname = current_role_name
    ) INTO has_bad_membership;

    IF has_bad_membership THEN
        RAISE EXCEPTION 'AI reader role inherits another role';
    END IF;

    IF has_schema_privilege(current_role_name, 'public', 'CREATE') THEN
        RAISE EXCEPTION 'AI reader role has CREATE on public schema';
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM information_schema.role_table_grants
        WHERE grantee = current_role_name
          AND table_schema = 'public'
          AND privilege_type IN ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER')
    ) INTO has_bad_table_privilege;

    IF has_bad_table_privilege THEN
        RAISE EXCEPTION 'AI reader role has one or more table write privileges';
    END IF;

    SELECT has_table_privilege(current_role_name, 'public.users', 'SELECT')
      INTO has_users_table_select;
    IF has_users_table_select THEN
        RAISE EXCEPTION 'AI reader role has table-level SELECT on users';
    END IF;

    -- Sensitive columns are any users columns other than id and username.
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.table_name = 'users'
          AND c.column_name NOT IN ('id', 'username')
          AND has_column_privilege(current_role_name,
                                   format('%I.%I', c.table_schema, c.table_name),
                                   c.column_name, 'SELECT')
    ) INTO has_users_sensitive_column_select;

    IF has_users_sensitive_column_select THEN
        RAISE EXCEPTION 'AI reader role can SELECT sensitive users columns';
    END IF;

    FOREACH required_table IN ARRAY required_tables LOOP
        IF to_regclass(format('public.%I', required_table)) IS NULL THEN
            RAISE EXCEPTION 'Required AI reader table public.% is missing', required_table;
        END IF;
        IF NOT has_table_privilege(current_role_name,
                                   format('public.%I', required_table), 'SELECT') THEN
            RAISE EXCEPTION 'AI reader role is missing SELECT on public.%', required_table;
        END IF;
    END LOOP;

    IF NOT has_column_privilege(current_role_name, 'public.users', 'id', 'SELECT')
       OR NOT has_column_privilege(current_role_name, 'public.users', 'username', 'SELECT') THEN
        RAISE EXCEPTION 'AI reader role is missing required users(id, username) column privileges';
    END IF;

    RAISE NOTICE 'PASS: AI reader role is non-privileged, non-inheriting, read-only, and users access is column-restricted.';
END
$$;

-- Human-readable evidence for the deployment record.
SELECT current_user AS verified_role,
       current_database() AS verified_database,
       has_schema_privilege(current_user, 'public', 'USAGE') AS public_schema_usage,
       has_schema_privilege(current_user, 'public', 'CREATE') AS public_schema_create,
       has_table_privilege(current_user, 'public.users', 'SELECT') AS users_table_select,
       has_column_privilege(current_user, 'public.users', 'id', 'SELECT') AS users_id_select,
       has_column_privilege(current_user, 'public.users', 'username', 'SELECT') AS users_username_select;
