-- Execute as AI_TOOL_DB_USERNAME against the production database.
-- This is a read-only privilege audit. Do not run as postgres/application owner.

SELECT current_user AS connected_role,
       session_user AS session_role,
       current_setting('transaction_read_only') AS transaction_read_only,
       current_setting('default_transaction_read_only') AS default_transaction_read_only;

SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolinherit
FROM pg_roles
WHERE rolname = current_user;

SELECT r.rolname AS member_role, parent.rolname AS inherited_role
FROM pg_auth_members m
JOIN pg_roles parent ON parent.oid = m.roleid
JOIN pg_roles r ON r.oid = m.member
WHERE r.rolname = current_user;

SELECT schemaname, tablename,
       has_table_privilege(current_user, format('%I.%I', schemaname, tablename), 'SELECT') AS can_select,
       has_table_privilege(current_user, format('%I.%I', schemaname, tablename), 'INSERT') AS can_insert,
       has_table_privilege(current_user, format('%I.%I', schemaname, tablename), 'UPDATE') AS can_update,
       has_table_privilege(current_user, format('%I.%I', schemaname, tablename), 'DELETE') AS can_delete,
       has_table_privilege(current_user, format('%I.%I', schemaname, tablename), 'TRUNCATE') AS can_truncate
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY tablename;

-- users must NOT have table-level SELECT. Only these columns are required by
-- FinancialLookupTool. The two column checks should be true.
SELECT has_column_privilege(current_user, 'public.users', 'id', 'SELECT') AS can_read_user_id,
       has_column_privilege(current_user, 'public.users', 'username', 'SELECT') AS can_read_username,
       has_table_privilege(current_user, 'public.users', 'SELECT') AS can_read_all_user_columns;

-- Expected: transaction_read_only/default_transaction_read_only=on;
-- rolsuper/rolcreatedb/rolcreaterole/rolreplication/rolinherit=false;
-- no inherited roles; no write privileges; users table-level SELECT=false.
-- Check every SELECT grant against docs/sql/create-ai-readonly-role.sql.
-- This does not prove tenant isolation or row-level security.
