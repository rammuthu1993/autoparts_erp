package com.autoparts.erp.reports.repositoryimpl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.autoparts.erp.reports.dto.DataQualityReportResponse.Issue;
import com.autoparts.erp.reports.dto.CustomerAnalyticsResponse.CustomerMetric;
import com.autoparts.erp.reports.dto.FinancialReconciliationResponse.Discrepancy;
import com.autoparts.erp.reports.dto.InventoryAnalyticsResponse.ProductMetric;
import com.autoparts.erp.reports.dto.ReportTargetRequest;
import com.autoparts.erp.reports.dto.SupplierAnalyticsResponse.SupplierMetric;
import com.autoparts.erp.reports.repository.AdvancedReportRepository.TargetActualData;
import com.autoparts.erp.reports.repository.AdvancedReportRepository;

@Repository
public class AdvancedReportRepositoryImpl implements AdvancedReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AdvancedReportRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Issue> findDataQualityIssues(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp,
            String businessTimeZone) {
        var params = dateTimeParams(
                fromDate, toDate, fromTimestamp, toTimestamp, businessTimeZone);
        String sql = """
                WITH issues AS (
                    SELECT
                        'PRODUCT_INFORMATION' AS category,
                        'HIGH' AS severity,
                        'PRODUCT' AS entity_type,
                        p.id::text AS record_id,
                        p.product_code AS reference,
                        'Product name, product code, or part number is missing.' AS description,
                        'PRODUCT_INFORMATION:PRODUCT:' || p.id AS issue_key
                    FROM products p
                    WHERE COALESCE(NULLIF(TRIM(p.name), ''), NULL) IS NULL
                       OR COALESCE(NULLIF(TRIM(p.product_code), ''), NULL) IS NULL
                       OR COALESCE(NULLIF(TRIM(p.part_no), ''), NULL) IS NULL

                    UNION ALL

                    SELECT 'MISSING_COST_INFORMATION', 'MEDIUM', 'PRODUCT',
                           p.id::text, p.product_code,
                           'Purchase price is zero; cost-based metrics for this product are unavailable.',
                           'MISSING_COST_INFORMATION:PRODUCT:' || p.id
                    FROM products p
                    WHERE p.purchase_price = 0

                    UNION ALL

                    SELECT 'CUSTOMER_COMPLETENESS', 'LOW', 'CUSTOMER',
                           c.id::text, c.customer_code,
                           'Customer has no phone, email, or address.',
                           'CUSTOMER_COMPLETENESS:CUSTOMER:' || c.id
                    FROM customers c
                    WHERE NULLIF(TRIM(c.phone), '') IS NULL
                      AND NULLIF(TRIM(c.email), '') IS NULL
                      AND NULLIF(TRIM(c.address), '') IS NULL

                    UNION ALL

                    SELECT 'SUPPLIER_COMPLETENESS', 'LOW', 'SUPPLIER',
                           s.id::text, s.supplier_name,
                           'Supplier has no contact person, email, or address.',
                           'SUPPLIER_COMPLETENESS:SUPPLIER:' || s.id
                    FROM suppliers s
                    WHERE NULLIF(TRIM(s.contact_person), '') IS NULL
                      AND NULLIF(TRIM(s.email), '') IS NULL
                      AND NULLIF(TRIM(s.address), '') IS NULL

                    UNION ALL

                    SELECT 'INVALID_TRANSACTION_DATE', 'HIGH', 'SALE',
                           s.id::text, s.invoice_number,
                           'Sale date is after today.',
                           'INVALID_TRANSACTION_DATE:SALE:' || s.id
                    FROM sales s
                    WHERE s.sale_date >
                              CAST(CURRENT_TIMESTAMP AT TIME ZONE :businessTimeZone AS date)
                      AND s.sale_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'INVALID_TRANSACTION_DATE', 'HIGH', 'PURCHASE',
                           p.id::text, p.purchase_number,
                           'Purchase date is after today.',
                           'INVALID_TRANSACTION_DATE:PURCHASE:' || p.id
                    FROM purchases p
                    WHERE p.purchase_date >
                              CAST(CURRENT_TIMESTAMP AT TIME ZONE :businessTimeZone AS date)
                      AND p.purchase_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'INVALID_TRANSACTION_DATE', 'MEDIUM', 'SALE_PAYMENT',
                           sp.id::text, s.invoice_number,
                           'Payment date is after today or before its sale date.',
                           'INVALID_TRANSACTION_DATE:SALE_PAYMENT:' || sp.id
                    FROM sales_payments sp
                    JOIN sales s ON s.id = sp.sale_id
                    WHERE (CAST(sp.payment_date AT TIME ZONE :businessTimeZone AS date)
                               > CAST(CURRENT_TIMESTAMP AT TIME ZONE :businessTimeZone AS date)
                       OR CAST(sp.payment_date AT TIME ZONE :businessTimeZone AS date)
                               < s.sale_date)
                      AND s.sale_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'INVALID_TRANSACTION_DATE', 'MEDIUM', 'PURCHASE_PAYMENT',
                           pp.id::text, p.purchase_number,
                           'Payment date is after today or before its purchase date.',
                           'INVALID_TRANSACTION_DATE:PURCHASE_PAYMENT:' || pp.id
                    FROM purchase_payments pp
                    JOIN purchases p ON p.id = pp.purchase_id
                    WHERE (CAST(pp.payment_date AT TIME ZONE :businessTimeZone AS date)
                               > CAST(CURRENT_TIMESTAMP AT TIME ZONE :businessTimeZone AS date)
                       OR CAST(pp.payment_date AT TIME ZONE :businessTimeZone AS date)
                               < p.purchase_date)
                      AND p.purchase_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'SALES_PAYMENT_MISMATCH', 'HIGH', 'SALE',
                           s.id::text, s.invoice_number,
                           'Recorded paid amount or balance differs from posted sales payments.',
                           'SALES_PAYMENT_MISMATCH:SALE:' || s.id
                    FROM sales s
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(sp.amount), 0) AS payment_total
                        FROM sales_payments sp
                        WHERE sp.sale_id = s.id
                    ) payments ON TRUE
                    WHERE s.status = 'COMPLETED'
                      AND (ABS(s.paid_amount - payments.payment_total) > 0.01
                        OR ABS(s.balance_amount - (s.grand_total - s.paid_amount)) > 0.01)
                      AND s.sale_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'INVENTORY_INCONSISTENCY', 'HIGH', 'PRODUCT',
                           p.id::text, p.product_code,
                           CASE WHEN p.qty < 0
                                THEN 'On-hand inventory quantity is negative.'
                                ELSE 'On-hand quantity differs from the latest inventory movement.' END,
                           'INVENTORY_INCONSISTENCY:PRODUCT:' || p.id
                    FROM products p
                    LEFT JOIN LATERAL (
                        SELECT im.quantity_after
                        FROM inventory_movements im
                        WHERE im.product_id = p.id
                        ORDER BY im.created_at DESC, im.id DESC
                        LIMIT 1
                    ) latest ON TRUE
                    WHERE p.qty < 0
                       OR (latest.quantity_after IS NOT NULL
                           AND ABS(p.qty - latest.quantity_after) > 0.001)

                    UNION ALL

                    SELECT 'INVALID_FINANCIAL_TOTAL', 'HIGH', 'SALE',
                           s.id::text, s.invoice_number,
                           'Sale total does not reconcile to taxable amount and tax components.',
                           'INVALID_FINANCIAL_TOTAL:SALE:' || s.id
                    FROM sales s
                    WHERE (ABS(s.subtotal - s.discount_amount - s.taxable_amount) > 0.01
                        OR ABS(s.cgst_amount + s.sgst_amount + s.igst_amount
                               - s.total_tax_amount) > 0.01
                        OR ABS(s.taxable_amount + s.total_tax_amount
                               - s.grand_total) > 0.01)
                      AND s.sale_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'INVALID_FINANCIAL_TOTAL', 'HIGH', 'PURCHASE',
                           p.id::text, p.purchase_number,
                           'Purchase total does not reconcile to subtotal, discount, and tax.',
                           'INVALID_FINANCIAL_TOTAL:PURCHASE:' || p.id
                    FROM purchases p
                    WHERE ABS(p.subtotal - p.discount_total + p.tax_total
                              - p.grand_total) > 0.01
                      AND p.purchase_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'POSSIBLE_DUPLICATE_SALE', 'MEDIUM', 'SALE',
                           s.id::text, s.invoice_number,
                           'Another sale has the same customer, date, and grand total; verify before taking action.',
                           'POSSIBLE_DUPLICATE_SALE:SALE:' || s.id
                    FROM sales s
                    JOIN (
                        SELECT customer_id, sale_date, grand_total
                        FROM sales
                        WHERE customer_id IS NOT NULL
                          AND status = 'COMPLETED'
                        GROUP BY customer_id, sale_date, grand_total
                        HAVING COUNT(*) > 1
                    ) duplicates
                      ON duplicates.customer_id = s.customer_id
                     AND duplicates.sale_date = s.sale_date
                     AND duplicates.grand_total = s.grand_total
                    WHERE s.status = 'COMPLETED'
                      AND s.sale_date BETWEEN :fromDate AND :toDate
                )
                SELECT i.issue_key, i.category, i.severity, i.entity_type,
                       i.record_id, i.reference, i.description,
                       COALESCE(r.status, 'OPEN') AS resolution_status,
                       r.notes AS resolution_notes
                FROM issues i
                LEFT JOIN report_issue_resolutions r
                    ON r.issue_key = i.issue_key
                ORDER BY CASE i.severity
                             WHEN 'HIGH' THEN 1
                             WHEN 'MEDIUM' THEN 2
                             ELSE 3
                         END,
                         i.category, i.entity_type, i.record_id
                """;
        return jdbc.query(sql, params, (rs, rowNum) -> new Issue(
                rs.getString("issue_key"),
                rs.getString("category"),
                rs.getString("severity"),
                rs.getString("entity_type"),
                rs.getString("record_id"),
                rs.getString("reference"),
                rs.getString("description"),
                rs.getString("resolution_status"),
                rs.getString("resolution_notes")));
    }

    @Override
    public long countRecordsChecked(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp) {
        var params = dateTimeParams(
                fromDate, toDate, fromTimestamp, toTimestamp, null);
        Long count = jdbc.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM products)
                  + (SELECT COUNT(*) FROM customers)
                  + (SELECT COUNT(*) FROM suppliers)
                  + (SELECT COUNT(*) FROM sales
                     WHERE sale_date BETWEEN :fromDate AND :toDate)
                  + (SELECT COUNT(*) FROM sales_payments sp
                     JOIN sales s ON s.id = sp.sale_id
                     WHERE s.sale_date BETWEEN :fromDate AND :toDate)
                  + (SELECT COUNT(*) FROM purchases
                     WHERE purchase_date BETWEEN :fromDate AND :toDate)
                  + (SELECT COUNT(*) FROM purchase_payments pp
                     JOIN purchases p ON p.id = pp.purchase_id
                     WHERE p.purchase_date BETWEEN :fromDate AND :toDate)
                  + (SELECT COUNT(*) FROM sales_returns sr
                     WHERE sr.return_date >= :fromTimestamp
                       AND sr.return_date < :toTimestamp)
                  + (SELECT COUNT(*) FROM purchase_returns pr
                     WHERE pr.return_date >= :fromTimestamp
                       AND pr.return_date < :toTimestamp)
                """, params, Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public void updateIssueResolution(
            String issueKey,
            String status,
            String notes,
            String username) {
        int updated = jdbc.update("""
                INSERT INTO report_issue_resolutions (
                    issue_key, status, notes, updated_by
                )
                SELECT :issueKey, :status, :notes, u.id
                FROM users u
                WHERE LOWER(u.username) = LOWER(:username)
                ON CONFLICT (issue_key)
                DO UPDATE SET status = EXCLUDED.status,
                              notes = EXCLUDED.notes,
                              updated_by = EXCLUDED.updated_by,
                              updated_at = CURRENT_TIMESTAMP
                """, new MapSqlParameterSource()
                .addValue("issueKey", issueKey)
                .addValue("status", status)
                .addValue("notes", notes)
                .addValue("username", username));
        if (updated != 1) {
            throw new IllegalArgumentException(
                    "Authenticated user could not be resolved");
        }
    }

    @Override
    public List<Discrepancy> findReconciliationDiscrepancies(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp,
            String businessTimeZone) {
        var params = dateTimeParams(
                fromDate, toDate, fromTimestamp, toTimestamp, businessTimeZone);
        String sql = """
                WITH discrepancies AS (
                    SELECT 'SALES_INVOICE_VS_PAYMENTS' AS category,
                           'HIGH' AS severity, 'SALE' AS entity_type,
                           s.id::text AS record_id, s.invoice_number AS reference,
                           s.paid_amount AS source_amount,
                           payments.payment_total AS comparison_amount,
                           s.paid_amount - payments.payment_total AS difference,
                           'Invoice paid amount differs from its posted payment records.'
                               AS description
                    FROM sales s
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(sp.amount), 0) AS payment_total
                        FROM sales_payments sp WHERE sp.sale_id = s.id
                    ) payments ON TRUE
                    WHERE s.status = 'COMPLETED'
                      AND ABS(s.paid_amount - payments.payment_total) > 0.01
                      AND s.sale_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'PURCHASE_INVOICE_VS_PAYMENTS', 'HIGH', 'PURCHASE',
                           p.id::text, p.purchase_number,
                           payments.payment_total,
                           GREATEST(p.grand_total - returns.return_total
                                    - credits.applied_total, 0),
                           payments.payment_total
                             - GREATEST(p.grand_total - returns.return_total
                                        - credits.applied_total, 0),
                           'Payments exceed the received invoice balance after returns and applied credits.'
                    FROM purchases p
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(pp.amount), 0) AS payment_total
                        FROM purchase_payments pp WHERE pp.purchase_id = p.id
                    ) payments ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(pr.total_return_amount), 0) AS return_total
                        FROM purchase_returns pr WHERE pr.purchase_id = p.id
                    ) returns ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(sca.amount), 0) AS applied_total
                        FROM supplier_credit_applications sca
                        WHERE sca.purchase_id = p.id
                    ) credits ON TRUE
                    WHERE p.status = 'RECEIVED'
                      AND payments.payment_total >
                          GREATEST(p.grand_total - returns.return_total
                                   - credits.applied_total, 0) + 0.01
                      AND p.purchase_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'CUSTOMER_LEDGER_VS_OUTSTANDING', 'HIGH', 'CUSTOMER',
                           c.id::text, c.customer_code,
                           ledger.ledger_balance,
                           outstanding.posted_outstanding,
                           ledger.ledger_balance - outstanding.posted_outstanding,
                           'Customer ledger balance differs from invoice outstanding after sales returns.'
                    FROM customers c
                    JOIN LATERAL (
                        SELECT COALESCE(SUM(s.grand_total), 0)
                             - COALESCE(SUM(payments.payment_total), 0)
                             - COALESCE(SUM(returns.return_total), 0) AS ledger_balance
                        FROM sales s
                        LEFT JOIN LATERAL (
                            SELECT SUM(sp.amount) AS payment_total
                            FROM sales_payments sp WHERE sp.sale_id = s.id
                        ) payments ON TRUE
                        LEFT JOIN LATERAL (
                            SELECT SUM(sr.total_return_amount) AS return_total
                            FROM sales_returns sr
                            WHERE sr.sale_id = s.id
                              AND sr.status IN ('ACCEPTED', 'PARTIALLY_REFUNDED', 'REFUNDED')
                        ) returns ON TRUE
                        WHERE s.customer_id = c.id
                          AND s.status = 'COMPLETED'
                          AND s.sale_date BETWEEN :fromDate AND :toDate
                    ) ledger ON TRUE
                    JOIN LATERAL (
                        SELECT COALESCE(SUM(s.balance_amount), 0)
                             - COALESCE(SUM(returns.return_total), 0)
                             AS posted_outstanding
                        FROM sales s
                        LEFT JOIN LATERAL (
                            SELECT SUM(sr.total_return_amount) AS return_total
                            FROM sales_returns sr
                            WHERE sr.sale_id = s.id
                              AND sr.status IN ('ACCEPTED', 'PARTIALLY_REFUNDED', 'REFUNDED')
                        ) returns ON TRUE
                        WHERE s.customer_id = c.id
                          AND s.status = 'COMPLETED'
                          AND s.sale_date BETWEEN :fromDate AND :toDate
                    ) outstanding ON TRUE
                    WHERE ABS(ledger.ledger_balance - outstanding.posted_outstanding) > 0.01

                    UNION ALL

                    SELECT 'CASH_REGISTER_BALANCE', 'HIGH', 'CASH_SESSION',
                           crs.id::text, cr.register_code,
                           crs.expected_closing_balance,
                           crs.actual_closing_balance,
                           crs.actual_closing_balance - crs.expected_closing_balance,
                           'Actual closing balance differs from expected closing balance.'
                    FROM cash_register_sessions crs
                    JOIN cash_registers cr ON cr.id = crs.cash_register_id
                    WHERE crs.status = 'CLOSED'
                      AND ABS(crs.actual_closing_balance
                              - crs.expected_closing_balance) > 0.01
                      AND crs.closed_at >= :fromTimestamp
                      AND crs.closed_at < :toTimestamp

                    UNION ALL

                    SELECT 'CASH_REGISTER_MOVEMENTS_VS_SOURCES', 'HIGH',
                           'CASH_MOVEMENT', m.id::text, m.source_type,
                           m.amount,
                           COALESCE(source.amount, 0),
                           m.amount - COALESCE(source.amount, 0),
                           'Cash movement has no matching source transaction, amount, or expected direction.'
                    FROM cash_register_movements m
                    LEFT JOIN LATERAL (
                        SELECT sp.amount, 'IN' AS movement_type
                        FROM sales_payments sp
                        WHERE m.source_type IN ('SALE_PAYMENT', 'CUSTOMER_COLLECTION')
                          AND sp.uuid = m.source_uuid
                        UNION ALL
                        SELECT pp.amount, 'OUT'
                        FROM purchase_payments pp
                        WHERE m.source_type = 'PURCHASE_PAYMENT'
                          AND pp.uuid = m.source_uuid
                        UNION ALL
                        SELECT e.amount, 'OUT'
                        FROM expenses e
                        WHERE m.source_type = 'EXPENSE'
                          AND e.uuid = m.source_uuid
                        UNION ALL
                        SELECT sr.amount, 'OUT'
                        FROM sales_refunds sr
                        WHERE m.source_type = 'SALES_REFUND'
                          AND sr.uuid = m.source_uuid
                        UNION ALL
                        SELECT sr.amount, 'IN'
                        FROM supplier_refunds sr
                        WHERE m.source_type = 'SUPPLIER_REFUND'
                          AND sr.uuid = m.source_uuid
                    ) source ON TRUE
                    WHERE m.source_type <> 'MANUAL'
                      AND m.source_type <> 'ADJUSTMENT'
                      AND (source.amount IS NULL
                           OR ABS(m.amount - source.amount) > 0.01
                           OR m.movement_type <> source.movement_type)
                      AND m.created_at >= :fromTimestamp
                      AND m.created_at < :toTimestamp

                    UNION ALL

                    SELECT 'SALES_RETURNS_VS_REFUNDS', 'MEDIUM', 'SALES_RETURN',
                           sr.id::text, s.invoice_number,
                           sr.total_return_amount, refunds.refund_total,
                           refunds.refund_total - sr.total_return_amount,
                           'Refund total or return refund status does not reconcile to the return amount.'
                    FROM sales_returns sr
                    JOIN sales s ON s.id = sr.sale_id
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(r.amount), 0) AS refund_total
                        FROM sales_refunds r WHERE r.sales_return_id = sr.id
                    ) refunds ON TRUE
                    WHERE (
                            refunds.refund_total
                                > sr.total_return_amount + 0.01
                         OR (sr.status = 'REFUNDED'
                             AND ABS(refunds.refund_total
                                     - sr.total_return_amount) > 0.01)
                         OR (sr.status = 'PARTIALLY_REFUNDED'
                             AND refunds.refund_total
                                 >= sr.total_return_amount - 0.01)
                         OR (sr.status = 'ACCEPTED'
                             AND refunds.refund_total > 0.01)
                    )
                      AND sr.return_date >= :fromTimestamp
                      AND sr.return_date < :toTimestamp

                    UNION ALL

                    SELECT 'PURCHASE_RETURNS_VS_CREDITS', 'HIGH', 'PURCHASE_RETURN',
                           pr.id::text, pr.return_number,
                           pr.total_return_amount,
                           COALESCE(sc.amount, 0),
                           COALESCE(sc.amount, 0) - pr.total_return_amount,
                           'Supplier credit is missing or differs from the purchase return amount.'
                    FROM purchase_returns pr
                    LEFT JOIN supplier_credits sc
                        ON sc.purchase_return_id = pr.id
                    WHERE (sc.id IS NULL
                       OR ABS(sc.amount - pr.total_return_amount) > 0.01)
                      AND pr.return_date >= :fromTimestamp
                      AND pr.return_date < :toTimestamp

                    UNION ALL

                    SELECT 'PURCHASE_RETURNS_VS_CREDITS', 'HIGH', 'PURCHASE_RETURN',
                           pr.id::text, pr.return_number, sc.amount,
                           credit_usage.used_amount,
                           credit_usage.used_amount - sc.amount,
                           'Supplier credit applications plus refunds exceed the credit amount.'
                    FROM purchase_returns pr
                    JOIN supplier_credits sc
                        ON sc.purchase_return_id = pr.id
                    CROSS JOIN LATERAL (
                        SELECT
                            (SELECT COALESCE(SUM(sca.amount), 0)
                             FROM supplier_credit_applications sca
                             WHERE sca.supplier_credit_id = sc.id)
                          + (SELECT COALESCE(SUM(sr.amount), 0)
                             FROM supplier_refunds sr
                             WHERE sr.supplier_credit_id = sc.id) AS used_amount
                    ) credit_usage
                    WHERE credit_usage.used_amount > sc.amount + 0.01
                      AND pr.return_date >= :fromTimestamp
                      AND pr.return_date < :toTimestamp

                    UNION ALL

                    SELECT 'SUPPLIER_CREDIT_REAPPLIED_TO_SOURCE_PURCHASE',
                           'HIGH', 'PURCHASE_RETURN', pr.id::text,
                           pr.return_number, sc.amount, sca.amount,
                           sca.amount,
                           'A return already reduces its source purchase balance; applying its credit to that same purchase counts the return twice.'
                    FROM purchase_returns pr
                    JOIN supplier_credits sc
                        ON sc.purchase_return_id = pr.id
                    JOIN supplier_credit_applications sca
                        ON sca.supplier_credit_id = sc.id
                       AND sca.purchase_id = pr.purchase_id
                    WHERE pr.return_date >= :fromTimestamp
                      AND pr.return_date < :toTimestamp

                    UNION ALL

                    SELECT 'DAILY_CLOSING_VS_SOURCES', 'HIGH', 'DAILY_CLOSING',
                           dc.id::text, dc.business_date::text,
                           dc.sales_total,
                           source.sales_total,
                           dc.sales_total - source.sales_total,
                           'Daily closing sales total differs from completed sales for the business date.'
                    FROM daily_closings dc
                    JOIN LATERAL (
                        SELECT COALESCE(SUM(s.grand_total), 0) AS sales_total
                        FROM sales s
                        WHERE s.status = 'COMPLETED'
                          AND s.sale_date = dc.business_date
                    ) source ON TRUE
                    WHERE ABS(dc.sales_total - source.sales_total) > 0.01
                      AND dc.business_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'DAILY_CLOSING_VS_SOURCES', 'HIGH', 'DAILY_CLOSING',
                           dc.id::text, dc.business_date::text,
                           dc.purchase_total, source.purchase_total,
                           dc.purchase_total - source.purchase_total,
                           'Daily closing purchase total differs from received purchases for the business date.'
                    FROM daily_closings dc
                    JOIN LATERAL (
                        SELECT COALESCE(SUM(p.grand_total), 0) AS purchase_total
                        FROM purchases p
                        WHERE p.status = 'RECEIVED'
                          AND p.purchase_date = dc.business_date
                    ) source ON TRUE
                    WHERE ABS(dc.purchase_total - source.purchase_total) > 0.01
                      AND dc.business_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'DAILY_CLOSING_VS_SOURCES', 'HIGH', 'DAILY_CLOSING',
                           dc.id::text, dc.business_date::text,
                           dc.expense_total, source.expense_total,
                           dc.expense_total - source.expense_total,
                           'Daily closing expense total differs from active expenses for the business date.'
                    FROM daily_closings dc
                    JOIN LATERAL (
                        SELECT COALESCE(SUM(e.amount), 0) AS expense_total
                        FROM expenses e
                        WHERE e.status = 'ACTIVE'
                          AND e.expense_date = dc.business_date
                    ) source ON TRUE
                    WHERE ABS(dc.expense_total - source.expense_total) > 0.01
                      AND dc.business_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'DAILY_CLOSING_VS_SOURCES', 'HIGH', 'DAILY_CLOSING',
                           dc.id::text, dc.business_date::text,
                           dc.sales_return_total, source.return_total,
                           dc.sales_return_total - source.return_total,
                           'Daily closing sales returns differ from accepted returns for the business date.'
                    FROM daily_closings dc
                    JOIN LATERAL (
                        SELECT COALESCE(SUM(sr.total_return_amount), 0) AS return_total
                        FROM sales_returns sr
                        WHERE sr.status IN ('ACCEPTED', 'PARTIALLY_REFUNDED', 'REFUNDED')
                          AND sr.return_date >=
                              (dc.business_date::timestamp AT TIME ZONE :businessTimeZone)
                          AND sr.return_date <
                              ((dc.business_date + 1)::timestamp AT TIME ZONE :businessTimeZone)
                    ) source ON TRUE
                    WHERE ABS(dc.sales_return_total - source.return_total) > 0.01
                      AND dc.business_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'DAILY_CLOSING_VS_SOURCES', 'HIGH', 'DAILY_CLOSING',
                           dc.id::text, dc.business_date::text,
                           dc.sales_refund_total, source.refund_total,
                           dc.sales_refund_total - source.refund_total,
                           'Daily closing sales refunds differ from posted refunds for the business date.'
                    FROM daily_closings dc
                    JOIN LATERAL (
                        SELECT COALESCE(SUM(sr.amount), 0) AS refund_total
                        FROM sales_refunds sr
                        WHERE sr.refund_date >=
                              (dc.business_date::timestamp AT TIME ZONE :businessTimeZone)
                          AND sr.refund_date <
                              ((dc.business_date + 1)::timestamp AT TIME ZONE :businessTimeZone)
                    ) source ON TRUE
                    WHERE ABS(dc.sales_refund_total - source.refund_total) > 0.01
                      AND dc.business_date BETWEEN :fromDate AND :toDate

                    UNION ALL

                    SELECT 'DAILY_CLOSING_VS_SOURCES', 'HIGH', 'DAILY_CLOSING',
                           dc.id::text, dc.business_date::text,
                           dc.cash_expected_total, source.expected_total,
                           dc.cash_expected_total - source.expected_total,
                           'Daily closing expected cash differs from its closed cash-register sessions.'
                    FROM daily_closings dc
                    JOIN LATERAL (
                        SELECT COALESCE(SUM(dcr.expected_closing_balance), 0)
                            AS expected_total
                        FROM daily_closing_cash_registers dcr
                        WHERE dcr.daily_closing_id = dc.id
                    ) source ON TRUE
                    WHERE ABS(dc.cash_expected_total - source.expected_total) > 0.01
                      AND dc.business_date BETWEEN :fromDate AND :toDate
                )
                SELECT category, severity, entity_type, record_id, reference,
                       source_amount, comparison_amount, difference, description
                FROM discrepancies
                ORDER BY category, entity_type, record_id
                """;
        return jdbc.query(sql, params, (rs, rowNum) -> new Discrepancy(
                rs.getString("category"),
                rs.getString("severity"),
                rs.getString("entity_type"),
                rs.getString("record_id"),
                rs.getString("reference"),
                rs.getBigDecimal("source_amount"),
                rs.getBigDecimal("comparison_amount"),
                rs.getBigDecimal("difference"),
                rs.getString("description")));
    }

    @Override
    public List<ProductMetric> findInventoryMetrics(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp,
            String businessTimeZone) {
        var params = dateTimeParams(
                fromDate, toDate, fromTimestamp, toTimestamp, businessTimeZone);
        String sql = """
                WITH months AS (
                    SELECT generate_series(
                        date_trunc('month', CAST(:fromDate AS date)),
                        date_trunc('month', CAST(:toDate AS date)),
                        interval '1 month'
                    )::date AS month_start
                ),
                monthly_sales AS (
                    SELECT p.id AS product_id, months.month_start,
                           GREATEST(0, COALESCE(sold.quantity, 0)
                               - COALESCE(returned.quantity, 0)) AS units_sold,
                           GREATEST(0, COALESCE(sold.quantity, 0)
                               - COALESCE(returned.quantity, 0))
                               * 30.4375
                               / GREATEST(
                                   1,
                                   LEAST(
                                       (months.month_start + interval '1 month')::date,
                                       :toDate + 1
                                   ) - GREATEST(months.month_start, :fromDate)
                               ) AS quantity
                    FROM products p
                    CROSS JOIN months
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(sp.quantity), 0) AS quantity
                        FROM sales_products sp
                        JOIN sales s ON s.id = sp.sale_id
                        WHERE sp.product_id = p.id
                          AND s.status = 'COMPLETED'
                          AND s.sale_date >= months.month_start
                          AND s.sale_date < months.month_start + interval '1 month'
                          AND s.sale_date BETWEEN :fromDate AND :toDate
                    ) sold ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(srp.quantity), 0) AS quantity
                        FROM sales_return_products srp
                        JOIN sales_returns sr ON sr.id = srp.sales_return_id
                        JOIN sales s ON s.id = sr.sale_id
                        WHERE srp.product_id = p.id
                          AND sr.status IN ('ACCEPTED', 'PARTIALLY_REFUNDED', 'REFUNDED')
                          AND sr.return_date >=
                              (months.month_start::timestamp AT TIME ZONE :businessTimeZone)
                          AND sr.return_date <
                              ((months.month_start + interval '1 month')
                                  AT TIME ZONE :businessTimeZone)
                          AND sr.return_date >= :fromTimestamp
                          AND sr.return_date < :toTimestamp
                          AND s.status = 'COMPLETED'
                    ) returned ON TRUE
                ),
                product_demand AS (
                    SELECT product_id,
                           COALESCE(SUM(units_sold), 0) AS units_sold,
                           AVG(quantity) AS average_monthly_demand,
                           CASE WHEN AVG(quantity) = 0 THEN NULL
                                ELSE STDDEV_POP(quantity) / AVG(quantity)
                           END AS demand_cv
                    FROM monthly_sales
                    GROUP BY product_id
                ),
                product_revenue AS (
                    SELECT p.id AS product_id,
                           COALESCE(sales.revenue, 0)
                             - COALESCE(returns.revenue, 0) AS revenue
                    FROM products p
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(sp.taxable_amount), 0) AS revenue
                        FROM sales_products sp
                        JOIN sales s ON s.id = sp.sale_id
                        WHERE sp.product_id = p.id
                          AND s.status = 'COMPLETED'
                          AND s.sale_date BETWEEN :fromDate AND :toDate
                    ) sales ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(srp.taxable_amount), 0) AS revenue
                        FROM sales_return_products srp
                        JOIN sales_returns sr ON sr.id = srp.sales_return_id
                        JOIN sales s ON s.id = sr.sale_id
                        WHERE srp.product_id = p.id
                          AND sr.status IN ('ACCEPTED', 'PARTIALLY_REFUNDED', 'REFUNDED')
                          AND sr.return_date >= :fromTimestamp
                          AND sr.return_date < :toTimestamp
                          AND s.status = 'COMPLETED'
                    ) returns ON TRUE
                ),
                ranked AS (
                    SELECT p.id AS product_id,
                           GREATEST(COALESCE(r.revenue, 0), 0) AS revenue,
                           SUM(GREATEST(COALESCE(r.revenue, 0), 0)) OVER (
                               ORDER BY GREATEST(COALESCE(r.revenue, 0), 0) DESC, p.id
                           ) AS cumulative_revenue,
                           SUM(GREATEST(COALESCE(r.revenue, 0), 0)) OVER () AS total_revenue
                    FROM products p
                    LEFT JOIN product_revenue r ON r.product_id = p.id
                    WHERE p.active = TRUE
                )
                SELECT p.id AS product_id, p.product_code, p.name AS product_name,
                       pc.name AS category, pb.name AS brand, p.unit,
                       p.qty AS on_hand_quantity,
                       COALESCE(d.units_sold, 0) AS units_sold,
                       COALESCE(r.revenue, 0) AS sales_revenue,
                       COALESCE(d.average_monthly_demand, 0)
                           AS average_monthly_demand,
                       d.demand_cv AS demand_coefficient_of_variation,
                       CASE WHEN ranked.total_revenue = 0 THEN 'C'
                            WHEN ranked.cumulative_revenue / ranked.total_revenue <= 0.80
                                THEN 'A'
                            WHEN ranked.cumulative_revenue / ranked.total_revenue <= 0.95
                                THEN 'B'
                            ELSE 'C' END AS abc_class,
                       CASE WHEN d.demand_cv IS NULL THEN 'Z'
                            WHEN d.demand_cv <= 0.50 THEN 'X'
                            WHEN d.demand_cv <= 1.00 THEN 'Y'
                            ELSE 'Z' END AS xyz_class,
                       CAST(latest.created_at AT TIME ZONE :businessTimeZone AS date)
                           AS last_movement_date,
                       CASE WHEN latest.created_at IS NULL THEN NULL
                            ELSE (:toDate - CAST(
                                latest.created_at AT TIME ZONE :businessTimeZone AS date)
                                  )::bigint
                       END AS days_since_last_movement,
                       recent_sales.last_sale_date,
                       CASE WHEN recent_sales.last_sale_date IS NULL THEN NULL
                            ELSE (:toDate - recent_sales.last_sale_date)::bigint
                       END AS days_since_last_sale,
                       CASE WHEN p.qty <= 0 THEN 'OUT_OF_STOCK'
                            WHEN recent_sales.last_sale_date IS NULL THEN 'NO_SALES_IN_PERIOD'
                            WHEN :toDate - recent_sales.last_sale_date <= 30
                                THEN 'FAST_MOVING'
                            WHEN :toDate - recent_sales.last_sale_date > 90
                                THEN 'SLOW_MOVING'
                            ELSE 'NORMAL' END AS movement_class
                FROM products p
                JOIN product_categories pc ON pc.id = p.category_id
                JOIN product_brands pb ON pb.id = p.brand_id
                LEFT JOIN product_demand d ON d.product_id = p.id
                LEFT JOIN product_revenue r ON r.product_id = p.id
                JOIN ranked ON ranked.product_id = p.id
                LEFT JOIN LATERAL (
                    SELECT im.created_at
                    FROM inventory_movements im
                    WHERE im.product_id = p.id
                      AND im.created_at < :toTimestamp
                    ORDER BY im.created_at DESC, im.id DESC
                    LIMIT 1
                ) latest ON TRUE
                LEFT JOIN LATERAL (
                    SELECT MAX(s.sale_date) AS last_sale_date
                    FROM sales_products sp
                    JOIN sales s ON s.id = sp.sale_id
                    WHERE sp.product_id = p.id
                      AND s.status = 'COMPLETED'
                      AND s.sale_date BETWEEN :fromDate AND :toDate
                ) recent_sales ON TRUE
                WHERE p.active = TRUE
                ORDER BY GREATEST(COALESCE(r.revenue, 0), 0) DESC,
                         p.product_code
                """;
        return jdbc.query(sql, params, (rs, rowNum) -> new ProductMetric(
                rs.getLong("product_id"),
                rs.getString("product_code"),
                rs.getString("product_name"),
                rs.getString("category"),
                rs.getString("brand"),
                rs.getString("unit"),
                rs.getBigDecimal("on_hand_quantity"),
                rs.getBigDecimal("units_sold"),
                rs.getBigDecimal("sales_revenue"),
                rs.getBigDecimal("average_monthly_demand"),
                rs.getBigDecimal("demand_coefficient_of_variation"),
                rs.getString("abc_class"),
                rs.getString("xyz_class"),
                rs.getObject("last_movement_date", LocalDate.class),
                rs.getObject("days_since_last_movement", Long.class),
                rs.getObject("last_sale_date", LocalDate.class),
                rs.getObject("days_since_last_sale", Long.class),
                rs.getString("movement_class")));
    }

    @Override
    public UUID createTarget(ReportTargetRequest request, String username) {
        return jdbc.queryForObject("""
                INSERT INTO report_targets (
                    uuid, metric_key, period_start, period_end,
                    product_id, category_id, target_value, created_by
                )
                SELECT :uuid, :metricKey, :periodStart, :periodEnd,
                       :productId, :categoryId, :targetValue, u.id
                FROM users u
                WHERE LOWER(u.username) = LOWER(:username)
                RETURNING uuid
                """, new MapSqlParameterSource()
                .addValue("uuid", UUID.randomUUID())
                .addValue("metricKey", request.metricKey())
                .addValue("periodStart", request.periodStart())
                .addValue("periodEnd", request.periodEnd())
                .addValue("productId", request.productId())
                .addValue("categoryId", request.categoryId())
                .addValue("targetValue", request.targetValue())
                .addValue("username", username), UUID.class);
    }

    @Override
    public List<TargetActualData> findTargetActuals(
            LocalDate fromDate,
            LocalDate toDate) {
        String sql = """
                SELECT t.uuid AS target_id,
                       t.metric_key,
                       CASE WHEN t.metric_key = 'PRODUCT_SALES' THEN p.name
                            WHEN t.metric_key = 'CATEGORY_REVENUE' THEN pc.name
                            WHEN t.metric_key = 'MONTHLY_SALES' THEN 'All sales'
                            ELSE 'All expenses' END AS dimension_name,
                       t.period_start, t.period_end, t.target_value AS target,
                       CASE t.metric_key
                           WHEN 'MONTHLY_SALES' THEN (
                               SELECT COALESCE(SUM(s.taxable_amount), 0)
                               FROM sales s
                               WHERE s.status = 'COMPLETED'
                                 AND s.sale_date BETWEEN t.period_start
                                                     AND LEAST(t.period_end, CURRENT_DATE)
                           )
                           WHEN 'PRODUCT_SALES' THEN (
                               SELECT COALESCE(SUM(sp.taxable_amount), 0)
                               FROM sales_products sp
                               JOIN sales s ON s.id = sp.sale_id
                               WHERE s.status = 'COMPLETED'
                                 AND sp.product_id = t.product_id
                                 AND s.sale_date BETWEEN t.period_start
                                                     AND LEAST(t.period_end, CURRENT_DATE)
                           )
                           WHEN 'CATEGORY_REVENUE' THEN (
                               SELECT COALESCE(SUM(sp.taxable_amount), 0)
                               FROM sales_products sp
                               JOIN sales s ON s.id = sp.sale_id
                               JOIN products product ON product.id = sp.product_id
                               WHERE s.status = 'COMPLETED'
                                 AND product.category_id = t.category_id
                                 AND s.sale_date BETWEEN t.period_start
                                                     AND LEAST(t.period_end, CURRENT_DATE)
                           )
                           WHEN 'EXPENSE_BUDGET' THEN (
                               SELECT COALESCE(SUM(e.amount), 0)
                               FROM expenses e
                               WHERE e.status = 'ACTIVE'
                                 AND e.expense_date BETWEEN t.period_start
                                                        AND LEAST(t.period_end, CURRENT_DATE)
                           )
                       END AS actual
                FROM report_targets t
                LEFT JOIN products p ON p.id = t.product_id
                LEFT JOIN product_categories pc ON pc.id = t.category_id
                WHERE t.active = TRUE
                  AND t.period_start <= :toDate
                  AND t.period_end >= :fromDate
                ORDER BY t.period_start, t.metric_key, dimension_name
                """;
        return jdbc.query(sql, dateParams(fromDate, toDate),
                (rs, rowNum) -> new TargetActualData(
                        rs.getObject("target_id", UUID.class),
                        rs.getString("metric_key"),
                        rs.getString("dimension_name"),
                        rs.getObject("period_start", LocalDate.class),
                        rs.getObject("period_end", LocalDate.class),
                        rs.getBigDecimal("target"),
                        rs.getBigDecimal("actual")));
    }

    @Override
    public List<CustomerMetric> findCustomerMetrics(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp) {
        String sql = """
                WITH customer_sales AS (
                    SELECT c.id AS customer_id, c.customer_code,
                           c.customer_name,
                           COUNT(DISTINCT s.id) AS purchase_frequency,
                           COALESCE(SUM(s.taxable_amount), 0) AS sales_amount,
                           COUNT(DISTINCT date_trunc('month', s.sale_date))
                               AS active_months,
                           MAX(s.sale_date) AS last_purchase_date,
                           (SELECT date_trunc('month', MIN(all_sales.sale_date))::date
                            FROM sales all_sales
                            WHERE all_sales.customer_id = c.id
                              AND all_sales.status = 'COMPLETED') AS cohort_month
                    FROM customers c
                    JOIN sales s ON s.customer_id = c.id
                    WHERE s.status = 'COMPLETED'
                      AND s.sale_date BETWEEN :fromDate AND :toDate
                    GROUP BY c.id, c.customer_code, c.customer_name
                ),
                customer_returns AS (
                    SELECT s.customer_id,
                           COALESCE(SUM(sr.taxable_amount), 0) AS return_amount
                    FROM sales_returns sr
                    JOIN sales s ON s.id = sr.sale_id
                    WHERE s.customer_id IS NOT NULL
                      AND s.status = 'COMPLETED'
                      AND sr.status IN ('ACCEPTED', 'PARTIALLY_REFUNDED', 'REFUNDED')
                      AND sr.return_date >= :fromTimestamp
                      AND sr.return_date < :toTimestamp
                    GROUP BY s.customer_id
                ),
                customer_metrics AS (
                    SELECT customer_sales.*,
                           customer_sales.sales_amount
                             - COALESCE(customer_returns.return_amount, 0)
                               AS monetary_value
                    FROM customer_sales
                    LEFT JOIN customer_returns
                        ON customer_returns.customer_id = customer_sales.customer_id
                ),
                ranked AS (
                    SELECT customer_metrics.*,
                           6 - NTILE(5) OVER (
                               ORDER BY last_purchase_date DESC, customer_id
                           ) AS recency_score,
                           6 - NTILE(5) OVER (
                               ORDER BY purchase_frequency DESC, customer_id
                           ) AS frequency_score,
                           6 - NTILE(5) OVER (
                               ORDER BY monetary_value DESC, customer_id
                           ) AS monetary_score,
                           (:toDate - last_purchase_date) AS recency_days
                    FROM customer_metrics
                )
                SELECT customer_id, customer_code, customer_name, cohort_month,
                       recency_days, purchase_frequency, monetary_value,
                       CASE WHEN purchase_frequency = 0 THEN 0
                            ELSE monetary_value / purchase_frequency END
                            AS average_order_value,
                       active_months, recency_score, frequency_score,
                       monetary_score,
                       CASE WHEN recency_score >= 4
                                  AND frequency_score >= 4
                                  AND monetary_score >= 4 THEN 'CHAMPIONS'
                            WHEN recency_score <= 2
                                  AND frequency_score <= 2 THEN 'AT_RISK'
                            WHEN recency_score >= 4
                                  AND frequency_score <= 2 THEN 'NEW_CUSTOMERS'
                            WHEN frequency_score >= 4
                                  AND monetary_score >= 4 THEN 'LOYAL'
                            ELSE 'REGULAR' END AS segment
                FROM ranked
                ORDER BY monetary_value DESC, customer_name
                """;
        return jdbc.query(sql, dateTimeParams(
                fromDate, toDate, fromTimestamp, toTimestamp, null),
                (rs, rowNum) -> new CustomerMetric(
                        rs.getLong("customer_id"),
                        rs.getString("customer_code"),
                        rs.getString("customer_name"),
                        rs.getObject("cohort_month", LocalDate.class),
                        rs.getObject("recency_days", Long.class),
                        rs.getObject("purchase_frequency", Long.class),
                        rs.getBigDecimal("monetary_value"),
                        rs.getBigDecimal("average_order_value"),
                        rs.getObject("active_months", Long.class),
                        rs.getObject("recency_score", Integer.class),
                        rs.getObject("frequency_score", Integer.class),
                        rs.getObject("monetary_score", Integer.class),
                        rs.getString("segment")));
    }

    @Override
    public List<SupplierMetric> findSupplierMetrics(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime toTimestamp,
            String businessTimeZone) {
        String sql = """
                WITH purchase_metrics AS (
                    SELECT p.supplier_id,
                           COUNT(*) AS received_purchase_count,
                           COALESCE(SUM(p.grand_total), 0) AS purchase_total,
                           COALESCE(SUM(payments.payment_total), 0) AS payment_total,
                           COALESCE(SUM(credits.credit_total), 0) AS credit_applied_total,
                           COALESCE(SUM(returns.return_total), 0) AS returned_amount,
                           AVG(first_payment.days_to_first_payment)
                               AS average_days_to_first_payment,
                           AVG(CAST(
                                   p.received_at AT TIME ZONE :businessTimeZone
                               AS date) - p.purchase_date)
                               AS average_lead_time_days,
                           STDDEV_POP(CAST(
                                   p.received_at AT TIME ZONE :businessTimeZone
                               AS date) - p.purchase_date)
                               AS lead_time_variability_days
                    FROM purchases p
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(pp.amount), 0) AS payment_total
                        FROM purchase_payments pp
                        WHERE pp.purchase_id = p.id
                          AND pp.payment_date < :toTimestamp
                    ) payments ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(sca.amount), 0) AS credit_total
                        FROM supplier_credit_applications sca
                        WHERE sca.purchase_id = p.id
                          AND sca.applied_at < :toTimestamp
                    ) credits ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT COALESCE(SUM(pr.total_return_amount), 0)
                            AS return_total
                        FROM purchase_returns pr
                        WHERE pr.purchase_id = p.id
                          AND pr.return_date < :toTimestamp
                    ) returns ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT MIN(CAST(
                                   pp.payment_date AT TIME ZONE :businessTimeZone
                               AS date)) - p.purchase_date
                            AS days_to_first_payment
                        FROM purchase_payments pp
                        WHERE pp.purchase_id = p.id
                          AND pp.payment_date < :toTimestamp
                    ) first_payment ON TRUE
                    WHERE p.status = 'RECEIVED'
                      AND p.received_at < :toTimestamp
                      AND p.purchase_date BETWEEN :fromDate AND :toDate
                    GROUP BY p.supplier_id
                ),
                totals AS (
                    SELECT s.id AS supplier_id, s.supplier_name,
                           COALESCE(m.received_purchase_count, 0)
                               AS received_purchase_count,
                           COALESCE(m.purchase_total, 0) AS purchase_total,
                           COALESCE(m.payment_total, 0) AS payment_total,
                           COALESCE(m.credit_applied_total, 0)
                               AS credit_applied_total,
                           COALESCE(m.returned_amount, 0) AS returned_amount,
                           m.average_days_to_first_payment,
                           m.average_lead_time_days,
                           m.lead_time_variability_days
                    FROM suppliers s
                    LEFT JOIN purchase_metrics m ON m.supplier_id = s.id
                )
                SELECT supplier_id, supplier_name, received_purchase_count,
                       purchase_total, payment_total,
                       CASE WHEN purchase_total = 0 THEN NULL
                            ELSE (payment_total + credit_applied_total)
                                 * 100 / purchase_total
                       END AS payment_coverage_percentage,
                       average_days_to_first_payment,
                       average_lead_time_days,
                       lead_time_variability_days,
                       returned_amount,
                       CASE WHEN purchase_total = 0 THEN NULL
                            ELSE returned_amount * 100 / purchase_total
                       END AS return_rate_percentage,
                       CASE WHEN SUM(purchase_total) OVER () = 0 THEN 0
                            ELSE purchase_total * 100 / SUM(purchase_total) OVER ()
                       END AS purchase_concentration_percentage
                FROM totals
                WHERE received_purchase_count > 0
                ORDER BY purchase_total DESC, supplier_name
                """;
        return jdbc.query(sql, dateParams(fromDate, toDate)
                        .addValue("toTimestamp", toTimestamp)
                        .addValue("businessTimeZone", businessTimeZone),
                (rs, rowNum) -> new SupplierMetric(
                        rs.getLong("supplier_id"),
                        rs.getString("supplier_name"),
                        rs.getObject("received_purchase_count", Long.class),
                        rs.getBigDecimal("purchase_total"),
                        rs.getBigDecimal("payment_total"),
                        rs.getBigDecimal("credit_applied_total"),
                        rs.getBigDecimal("payment_coverage_percentage"),
                        rs.getBigDecimal("average_days_to_first_payment"),
                        rs.getBigDecimal("average_lead_time_days"),
                        rs.getBigDecimal("lead_time_variability_days"),
                        rs.getBigDecimal("returned_amount"),
                        rs.getBigDecimal("return_rate_percentage"),
                        rs.getBigDecimal("purchase_concentration_percentage")));
    }

    @Override
    public ScenarioBaselineData getScenarioBaseline(
            LocalDate fromDate,
            LocalDate toDate) {
        return jdbc.queryForObject("""
                SELECT
                    (SELECT COALESCE(SUM(s.taxable_amount), 0)
                     FROM sales s
                     WHERE s.status = 'COMPLETED'
                       AND s.sale_date BETWEEN :fromDate AND :toDate)
                        AS revenue,
                    (SELECT COALESCE(SUM(e.amount), 0)
                     FROM expenses e
                     WHERE e.status = 'ACTIVE'
                       AND e.expense_date BETWEEN :fromDate AND :toDate)
                        AS expenses
                """, dateParams(fromDate, toDate),
                (rs, rowNum) -> new ScenarioBaselineData(
                        rs.getBigDecimal("revenue"),
                        rs.getBigDecimal("expenses")));
    }

    private static MapSqlParameterSource dateParams(
            LocalDate fromDate,
            LocalDate toDate) {
        return new MapSqlParameterSource()
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate);
    }

    private static MapSqlParameterSource dateTimeParams(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromTimestamp,
            OffsetDateTime toTimestamp,
            String businessTimeZone) {
        return dateParams(fromDate, toDate)
                .addValue("fromTimestamp", fromTimestamp)
                .addValue("toTimestamp", toTimestamp)
                .addValue("businessTimeZone", businessTimeZone);
    }
}
