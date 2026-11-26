-- =============================================================================
-- Migration: V40__align_manager_role_permissions.sql
-- Description: Align MANAGER role permissions per approved enterprise PBAC matrix
--              (Revokes SALES_RETURNS.REFUND and AI_ASSISTANT.DOCUMENT_MANAGE from MANAGER)
-- =============================================================================

DELETE FROM role_permissions
WHERE role_id IN (SELECT id FROM roles WHERE name = 'MANAGER')
  AND permission_id IN (
      SELECT id FROM permissions WHERE code IN ('SALES_RETURNS.REFUND', 'AI_ASSISTANT.DOCUMENT_MANAGE')
  );
