-- =============================================================================
-- Migration: V39__create_permissions_and_role_permissions.sql
-- Description: Enterprise PBAC tables, constraints, indices, catalogue and initial seeds
-- =============================================================================

-- 1. PERMISSIONS MASTER TABLE
CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    module VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_permissions_module ON permissions(module);
CREATE INDEX IF NOT EXISTS idx_permissions_code ON permissions(code);
CREATE INDEX IF NOT EXISTS idx_permissions_enabled ON permissions(enabled);

-- 2. ROLE_PERMISSIONS MAPPING TABLE
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL
        REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL
        REFERENCES permissions(id) ON DELETE RESTRICT,
    assigned_by BIGINT NULL
        REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);

-- 3. INSERT FULL PERMISSION CATALOGUE
INSERT INTO permissions (code, name, description, module, action) VALUES
    -- Dashboard
    ('DASHBOARD.VIEW', 'View Dashboard', 'Access main operational dashboard statistics and metrics', 'DASHBOARD', 'VIEW'),

    -- Products
    ('PRODUCTS.VIEW', 'View Products', 'Search and inspect product catalog, stock status, and barcodes', 'PRODUCTS', 'VIEW'),
    ('PRODUCTS.CREATE', 'Create Products', 'Register new products in catalog', 'PRODUCTS', 'CREATE'),
    ('PRODUCTS.UPDATE', 'Update Products', 'Edit product details, pricing, and reorder levels', 'PRODUCTS', 'UPDATE'),
    ('PRODUCTS.DEACTIVATE', 'Deactivate Products', 'Deactivate or archive products', 'PRODUCTS', 'DEACTIVATE'),

    -- Categories
    ('CATEGORIES.VIEW', 'View Categories', 'View product categories', 'CATEGORIES', 'VIEW'),
    ('CATEGORIES.CREATE', 'Create Categories', 'Create product categories', 'CATEGORIES', 'CREATE'),
    ('CATEGORIES.UPDATE', 'Update Categories', 'Edit product categories', 'CATEGORIES', 'UPDATE'),
    ('CATEGORIES.DEACTIVATE', 'Deactivate Categories', 'Deactivate product categories', 'CATEGORIES', 'DEACTIVATE'),

    -- Brands
    ('BRANDS.VIEW', 'View Brands', 'View product brands', 'BRANDS', 'VIEW'),
    ('BRANDS.CREATE', 'Create Brands', 'Create product brands', 'BRANDS', 'CREATE'),
    ('BRANDS.UPDATE', 'Update Brands', 'Edit product brands', 'BRANDS', 'UPDATE'),
    ('BRANDS.DEACTIVATE', 'Deactivate Brands', 'Deactivate product brands', 'BRANDS', 'DEACTIVATE'),

    -- Customers
    ('CUSTOMERS.VIEW', 'View Customers', 'View and search customer profiles', 'CUSTOMERS', 'VIEW'),
    ('CUSTOMERS.CREATE', 'Create Customers', 'Register new customer accounts', 'CUSTOMERS', 'CREATE'),
    ('CUSTOMERS.UPDATE', 'Update Customers', 'Edit customer profiles and contact details', 'CUSTOMERS', 'UPDATE'),
    ('CUSTOMERS.DEACTIVATE', 'Deactivate Customers', 'Deactivate customer accounts', 'CUSTOMERS', 'DEACTIVATE'),
    ('CUSTOMERS.LEDGER_VIEW', 'View Customer Ledger', 'Inspect customer financial ledgers and statements', 'CUSTOMERS', 'LEDGER_VIEW'),

    -- Suppliers
    ('SUPPLIERS.VIEW', 'View Suppliers', 'View and search suppliers', 'SUPPLIERS', 'VIEW'),
    ('SUPPLIERS.CREATE', 'Create Suppliers', 'Register new suppliers', 'SUPPLIERS', 'CREATE'),
    ('SUPPLIERS.UPDATE', 'Update Suppliers', 'Edit supplier details and contacts', 'SUPPLIERS', 'UPDATE'),
    ('SUPPLIERS.DEACTIVATE', 'Deactivate Suppliers', 'Deactivate suppliers', 'SUPPLIERS', 'DEACTIVATE'),
    ('SUPPLIERS.LEDGER_VIEW', 'View Supplier Ledger', 'Inspect supplier financial ledgers and balances', 'SUPPLIERS', 'LEDGER_VIEW'),

    -- Purchases
    ('PURCHASES.VIEW', 'View Purchases', 'View purchase orders and order details', 'PURCHASES', 'VIEW'),
    ('PURCHASES.CREATE', 'Create Purchases', 'Create purchase orders', 'PURCHASES', 'CREATE'),
    ('PURCHASES.RECEIVE', 'Receive Purchases', 'Receive purchased goods into inventory', 'PURCHASES', 'RECEIVE'),
    ('PURCHASES.PAYMENT_RECORD', 'Record Purchase Payments', 'Record payments made to suppliers', 'PURCHASES', 'PAYMENT_RECORD'),
    ('PURCHASES.PAYMENT_VIEW', 'View Purchase Payments', 'Inspect purchase payment details', 'PURCHASES', 'PAYMENT_VIEW'),
    ('PURCHASES.LEDGER_VIEW', 'View Purchase Ledger', 'Inspect purchase accounting ledgers', 'PURCHASES', 'LEDGER_VIEW'),

    -- Purchase Returns
    ('PURCHASE_RETURNS.VIEW', 'View Purchase Returns', 'View purchase return records', 'PURCHASE_RETURNS', 'VIEW'),
    ('PURCHASE_RETURNS.CREATE', 'Create Purchase Returns', 'Return purchased items to supplier and debit credit notes', 'PURCHASE_RETURNS', 'CREATE'),

    -- Sales
    ('SALES.VIEW', 'View Sales', 'View sales orders and invoice history', 'SALES', 'VIEW'),
    ('SALES.CREATE', 'Create Sales', 'Initiate sales orders and add line items', 'SALES', 'CREATE'),
    ('SALES.COMPLETE', 'Complete Sales', 'Finalize billing, accept payments, and issue invoices', 'SALES', 'COMPLETE'),
    ('SALES.CANCEL', 'Cancel Sales', 'Void and cancel active sales invoices', 'SALES', 'CANCEL'),
    ('SALES.PAYMENT_RECORD', 'Record Sales Payments', 'Record payments against sales invoices', 'SALES', 'PAYMENT_RECORD'),
    ('SALES.LEDGER_VIEW', 'View Sales Ledger', 'Inspect sales transaction ledgers', 'SALES', 'LEDGER_VIEW'),

    -- Sales Returns
    ('SALES_RETURNS.VIEW', 'View Sales Returns', 'View customer sales returns', 'SALES_RETURNS', 'VIEW'),
    ('SALES_RETURNS.CREATE', 'Create Sales Returns', 'Initiate customer return and restock items', 'SALES_RETURNS', 'CREATE'),
    ('SALES_RETURNS.REFUND', 'Process Sales Refunds', 'Issue cash or credit refunds to customers', 'SALES_RETURNS', 'REFUND'),

    -- Inventory
    ('INVENTORY.VIEW', 'View Inventory', 'Inspect inventory stock levels, valuations, and movements', 'INVENTORY', 'VIEW'),
    ('INVENTORY.ADJUST', 'Adjust Inventory', 'Perform manual stock adjustments', 'INVENTORY', 'ADJUST'),

    -- Cash Registers
    ('CASH_REGISTER.VIEW', 'View Cash Registers', 'View cash register status and movements', 'CASH_REGISTER', 'VIEW'),
    ('CASH_REGISTER.CREATE', 'Create Cash Register', 'Create physical cash register terminals', 'CASH_REGISTER', 'CREATE'),
    ('CASH_REGISTER.OPEN', 'Open Cash Register', 'Open a new register shift session', 'CASH_REGISTER', 'OPEN'),
    ('CASH_REGISTER.CLOSE', 'Close Cash Register', 'Close register session and record end-of-shift counts', 'CASH_REGISTER', 'CLOSE'),

    -- Daily Closing
    ('DAILY_CLOSING.VIEW', 'View Daily Closings', 'View daily business closing summaries and history', 'DAILY_CLOSING', 'VIEW'),
    ('DAILY_CLOSING.PERFORM', 'Perform Daily Closing', 'Execute end-of-day reconciliation and closing', 'DAILY_CLOSING', 'PERFORM'),

    -- Expenses
    ('EXPENSES.VIEW', 'View Expenses', 'View operational expenses', 'EXPENSES', 'VIEW'),
    ('EXPENSES.CREATE', 'Create Expenses', 'Record new business expenses', 'EXPENSES', 'CREATE'),
    ('EXPENSES.UPDATE', 'Update Expenses', 'Edit expense vouchers', 'EXPENSES', 'UPDATE'),
    ('EXPENSES.CANCEL', 'Cancel Expenses', 'Cancel or void expense records', 'EXPENSES', 'CANCEL'),
    ('EXPENSES.LEDGER_VIEW', 'View Expense Ledger', 'Inspect expense ledger accounts', 'EXPENSES', 'LEDGER_VIEW'),

    -- Expense Categories
    ('EXPENSE_CATEGORIES.VIEW', 'View Expense Categories', 'View expense categories', 'EXPENSE_CATEGORIES', 'VIEW'),
    ('EXPENSE_CATEGORIES.CREATE', 'Create Expense Categories', 'Create expense categories', 'EXPENSE_CATEGORIES', 'CREATE'),
    ('EXPENSE_CATEGORIES.UPDATE', 'Update Expense Categories', 'Edit expense categories', 'EXPENSE_CATEGORIES', 'UPDATE'),
    ('EXPENSE_CATEGORIES.DEACTIVATE', 'Deactivate Expense Categories', 'Deactivate expense categories', 'EXPENSE_CATEGORIES', 'DEACTIVATE'),

    -- Reports
    ('REPORTS.VIEW', 'View Operational Reports', 'View sales summaries, daily reports, and inventory valuation', 'REPORTS', 'VIEW'),
    ('REPORTS.ADVANCED_VIEW', 'View Advanced Reports', 'Access reconciliation, data quality, and scenario reports', 'REPORTS', 'ADVANCED_VIEW'),
    ('REPORTS.EXPORT', 'Export Reports', 'Export reports to CSV/PDF files', 'REPORTS', 'EXPORT'),

    -- Tax Rates
    ('TAX_RATES.VIEW', 'View Tax Rates', 'View GST rates and product tax mappings', 'TAX_RATES', 'VIEW'),
    ('TAX_RATES.CREATE', 'Create Tax Rates', 'Create new GST tax rates', 'TAX_RATES', 'CREATE'),
    ('TAX_RATES.UPDATE', 'Update Tax Rates', 'Edit tax rates and product tax mappings', 'TAX_RATES', 'UPDATE'),

    -- User Administration
    ('USERS.VIEW', 'View Users', 'View user accounts, statuses, and profiles', 'USERS', 'VIEW'),
    ('USERS.CREATE', 'Create Users', 'Create new ERP user accounts', 'USERS', 'CREATE'),
    ('USERS.UPDATE', 'Update Users', 'Edit user profile and email', 'USERS', 'UPDATE'),
    ('USERS.STATUS_UPDATE', 'Update User Status', 'Enable or disable user accounts', 'USERS', 'STATUS_UPDATE'),
    ('USERS.ROLES_ASSIGN', 'Assign User Roles', 'Assign and update user roles', 'USERS', 'ROLES_ASSIGN'),
    ('USERS.RESET_PASSWORD', 'Reset User Password', 'Reset passwords for users according to role hierarchy', 'USERS', 'RESET_PASSWORD'),

    -- Role & Permission Administration
    ('ROLES.VIEW', 'View Roles', 'View system roles and descriptions', 'ROLES', 'VIEW'),
    ('ROLES.PERMISSIONS_ASSIGN', 'Assign Role Permissions', 'Assign and modify permissions for roles', 'ROLES', 'PERMISSIONS_ASSIGN'),
    ('PERMISSIONS.VIEW', 'View Permissions', 'View master permission catalogue', 'PERMISSIONS', 'VIEW'),

    -- Audit Logs
    ('AUDIT_LOGS.VIEW', 'View Audit Logs', 'Inspect immutable audit trail', 'AUDIT_LOGS', 'VIEW'),

    -- AI Assistant
    ('AI_ASSISTANT.USE', 'Use AI Assistant', 'Prompt the AI assistant and manage chat sessions', 'AI_ASSISTANT', 'USE'),
    ('AI_ASSISTANT.DOCUMENT_MANAGE', 'Manage Knowledge Documents', 'Approve and reject AI knowledge documents', 'AI_ASSISTANT', 'DOCUMENT_MANAGE'),
    ('AI_ASSISTANT.EVALUATION_RUN', 'Run AI Evaluations', 'Execute live grounded answer evaluation benchmarks', 'AI_ASSISTANT', 'EVALUATION_RUN')
ON CONFLICT (code) DO NOTHING;

-- 4. ASSIGN INITIAL PERMISSIONS TO ROLES

-- A. ADMIN: Receives all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- B. MANAGER: Receives operational management permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'MANAGER'
  AND p.code IN (
      'DASHBOARD.VIEW',
      'PRODUCTS.VIEW', 'PRODUCTS.CREATE', 'PRODUCTS.UPDATE', 'PRODUCTS.DEACTIVATE',
      'CATEGORIES.VIEW', 'CATEGORIES.CREATE', 'CATEGORIES.UPDATE', 'CATEGORIES.DEACTIVATE',
      'BRANDS.VIEW', 'BRANDS.CREATE', 'BRANDS.UPDATE', 'BRANDS.DEACTIVATE',
      'CUSTOMERS.VIEW', 'CUSTOMERS.CREATE', 'CUSTOMERS.UPDATE', 'CUSTOMERS.DEACTIVATE', 'CUSTOMERS.LEDGER_VIEW',
      'SUPPLIERS.VIEW', 'SUPPLIERS.CREATE', 'SUPPLIERS.UPDATE', 'SUPPLIERS.DEACTIVATE', 'SUPPLIERS.LEDGER_VIEW',
      'PURCHASES.VIEW', 'PURCHASES.CREATE', 'PURCHASES.RECEIVE', 'PURCHASES.PAYMENT_RECORD', 'PURCHASES.PAYMENT_VIEW', 'PURCHASES.LEDGER_VIEW',
      'PURCHASE_RETURNS.VIEW', 'PURCHASE_RETURNS.CREATE',
      'SALES.VIEW', 'SALES.CREATE', 'SALES.COMPLETE', 'SALES.CANCEL', 'SALES.PAYMENT_RECORD', 'SALES.LEDGER_VIEW',
      'SALES_RETURNS.VIEW', 'SALES_RETURNS.CREATE', 'SALES_RETURNS.REFUND',
      'INVENTORY.VIEW', 'INVENTORY.ADJUST',
      'CASH_REGISTER.VIEW', 'CASH_REGISTER.CREATE', 'CASH_REGISTER.OPEN', 'CASH_REGISTER.CLOSE',
      'DAILY_CLOSING.VIEW', 'DAILY_CLOSING.PERFORM',
      'EXPENSES.VIEW', 'EXPENSES.CREATE', 'EXPENSES.UPDATE', 'EXPENSES.CANCEL', 'EXPENSES.LEDGER_VIEW',
      'EXPENSE_CATEGORIES.VIEW', 'EXPENSE_CATEGORIES.CREATE', 'EXPENSE_CATEGORIES.UPDATE', 'EXPENSE_CATEGORIES.DEACTIVATE',
      'REPORTS.VIEW',
      'TAX_RATES.VIEW',
      'USERS.RESET_PASSWORD',
      'AI_ASSISTANT.USE', 'AI_ASSISTANT.DOCUMENT_MANAGE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- C. CASHIER: Receives billing and counter-level operations
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'CASHIER'
  AND p.code IN (
      'DASHBOARD.VIEW',
      'PRODUCTS.VIEW',
      'CATEGORIES.VIEW',
      'BRANDS.VIEW',
      'CUSTOMERS.VIEW', 'CUSTOMERS.CREATE',
      'SALES.VIEW', 'SALES.CREATE', 'SALES.COMPLETE', 'SALES.PAYMENT_RECORD',
      'SALES_RETURNS.VIEW', 'SALES_RETURNS.CREATE',
      'INVENTORY.VIEW',
      'CASH_REGISTER.VIEW', 'CASH_REGISTER.OPEN', 'CASH_REGISTER.CLOSE',
      'TAX_RATES.VIEW'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
