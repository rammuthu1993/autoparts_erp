package com.autoparts.erp.sales.repositoryimpl;

import com.autoparts.erp.sales.dto.SalesStatusOption;
import com.autoparts.erp.sales.dto.SalesSummaryResponse;
import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.enums.SupplyType;
import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.model.SalesPayment;
import com.autoparts.erp.sales.model.SalesProduct;
import com.autoparts.erp.sales.repository.SalesRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SalesRepositoryImpl implements SalesRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SalesRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SalesStatusOption> findSalesStatuses() {
        return jdbcTemplate.query("""
            SELECT code, label, sort_order
            FROM sales_statuses
            WHERE active = TRUE
            ORDER BY sort_order, code
            """, new MapSqlParameterSource(),
            (rs, rowNum) -> new SalesStatusOption(
                rs.getString("code"), rs.getString("label"), rs.getInt("sort_order")
            ));
    }

    @Override
    public List<SalesStatusOption> findSalesPaymentStatuses() {
        return jdbcTemplate.query("""
            SELECT code, label, sort_order
            FROM sales_payment_statuses
            WHERE active = TRUE
            ORDER BY sort_order, code
            """, new MapSqlParameterSource(),
            (rs, rowNum) -> new SalesStatusOption(
                rs.getString("code"), rs.getString("label"), rs.getInt("sort_order")
            ));
    }

    @Override
    public boolean isSalesStatusConfigured(String code) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales_statuses WHERE code = :code AND active = TRUE",
            new MapSqlParameterSource("code", code), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean isSalesPaymentStatusConfigured(String code) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales_payment_statuses WHERE code = :code AND active = TRUE",
            new MapSqlParameterSource("code", code), Integer.class);
        return count != null && count > 0;
    }

    private final RowMapper<Sales> saleRowMapper = (rs, rowNum) -> {
        String supplyRaw = rs.getString("supply_type");
        SupplyType supplyType = (supplyRaw != null && !supplyRaw.isBlank())
            ? SupplyType.valueOf(supplyRaw)
            : SupplyType.INTRA_STATE;
        return new Sales(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getString("invoice_number"),
            rs.getObject("customer_id", Long.class),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("customer_gstin"),
            supplyType,
            rs.getString("place_of_supply"),
            rs.getString("billing_address"),
            rs.getString("shipping_address"),
            rs.getString("customer_reference"),
            rs.getString("payment_terms"),
            rs.getObject("due_date", LocalDate.class),
            rs.getObject("sale_date", LocalDate.class),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("discount_amount"),
            rs.getBigDecimal("taxable_amount"),
            rs.getBigDecimal("cgst_amount"),
            rs.getBigDecimal("sgst_amount"),
            rs.getBigDecimal("igst_amount"),
            rs.getBigDecimal("total_tax_amount"),
            rs.getBigDecimal("grand_total"),
            rs.getBigDecimal("paid_amount"),
            rs.getBigDecimal("balance_amount"),
            SalesStatus.valueOf(rs.getString("status")),
            SalesPaymentStatus.valueOf(rs.getString("payment_status")),
            rs.getString("notes"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    };

    private final RowMapper<SalesPayment> paymentRowMapper =
        (rs, rowNum) -> new SalesPayment(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("sale_id"),
            PaymentMethod.valueOf(rs.getString("payment_method")),
            rs.getBigDecimal("amount"),
            rs.getString("reference_number"),
            rs.getObject("payment_date", OffsetDateTime.class),
            rs.getString("notes"),
            rs.getObject("created_at", OffsetDateTime.class)
        );

    @Override
    public Sales createSale(Sales sale) {
        String sql = """
            INSERT INTO sales (
                uuid,
                invoice_number,
                customer_id,
                customer_name,
                customer_phone,
                customer_gstin,
                supply_type,
                place_of_supply,
                billing_address,
                shipping_address,
                customer_reference,
                payment_terms,
                due_date,
                sale_date,
                subtotal,
                discount_amount,
                taxable_amount,
                cgst_amount,
                sgst_amount,
                igst_amount,
                total_tax_amount,
                round_off_amount,
                grand_total,
                paid_amount,
                balance_amount,
                status,
                payment_status,
                notes,
                created_at,
                updated_at
            )
            VALUES (
                :uuid, :invoiceNumber, :customerId, :customerName, :customerPhone, :customerGstin,
                :supplyType, :placeOfSupply, :billingAddress, :shippingAddress, :customerReference,
                :paymentTerms, :dueDate, :saleDate,
                :subtotal, :discountAmount, :taxableAmount, :cgstAmount, :sgstAmount, :igstAmount,
                :totalTaxAmount, :roundOffAmount, :grandTotal, :paidAmount, :balanceAmount,
                :status, :paymentStatus, :notes,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("uuid", sale.uuid())
            .addValue("invoiceNumber", sale.invoiceNumber())
            .addValue("customerId", sale.customerId())
            .addValue("customerName", sale.customerName())
            .addValue("customerPhone", sale.customerPhone())
            .addValue("customerGstin", sale.customerGstin())
            .addValue("supplyType", (sale.supplyType() != null ? sale.supplyType() : SupplyType.INTRA_STATE).name())
            .addValue("placeOfSupply", sale.placeOfSupply())
            .addValue("billingAddress", sale.billingAddress())
            .addValue("shippingAddress", sale.shippingAddress())
            .addValue("customerReference", sale.customerReference())
            .addValue("paymentTerms", sale.paymentTerms())
            .addValue("dueDate", sale.dueDate())
            .addValue("saleDate", sale.saleDate())
            .addValue("subtotal", sale.subtotal())
            .addValue("discountAmount", sale.discountAmount())
            .addValue("taxableAmount", sale.taxableAmount())
            .addValue("cgstAmount", sale.cgstAmount())
            .addValue("sgstAmount", sale.sgstAmount())
            .addValue("igstAmount", sale.igstAmount())
            .addValue("totalTaxAmount", sale.totalTaxAmount())
            .addValue("roundOffAmount", sale.grandTotal().subtract(sale.taxableAmount()).subtract(sale.totalTaxAmount()))
            .addValue("grandTotal", sale.grandTotal())
            .addValue("paidAmount", sale.paidAmount())
            .addValue("balanceAmount", sale.balanceAmount())
            .addValue("status", sale.status().name())
            .addValue("paymentStatus", sale.paymentStatus().name())
            .addValue("notes", sale.notes());

        return jdbcTemplate.queryForObject(sql, params, saleRowMapper);
    }

    @Override
    public Sales updateSale(Sales sale) {
        String sql = """
            UPDATE sales
            SET invoice_number = :invoiceNumber,
                customer_id = :customerId,
                customer_name = :customerName,
                customer_phone = :customerPhone,
                customer_gstin = :customerGstin,
                supply_type = :supplyType,
                place_of_supply = :placeOfSupply,
                billing_address = :billingAddress,
                shipping_address = :shippingAddress,
                customer_reference = :customerReference,
                payment_terms = :paymentTerms,
                due_date = :dueDate,
                sale_date = :saleDate,
                subtotal = :subtotal,
                discount_amount = :discountAmount,
                taxable_amount = :taxableAmount,
                cgst_amount = :cgstAmount,
                sgst_amount = :sgstAmount,
                igst_amount = :igstAmount,
                total_tax_amount = :totalTaxAmount,
                round_off_amount = :roundOffAmount,
                grand_total = :grandTotal,
                paid_amount = :paidAmount,
                balance_amount = :balanceAmount,
                status = :status,
                payment_status = :paymentStatus,
                notes = :notes,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            RETURNING *
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("invoiceNumber", sale.invoiceNumber())
            .addValue("customerId", sale.customerId())
            .addValue("customerName", sale.customerName())
            .addValue("customerPhone", sale.customerPhone())
            .addValue("customerGstin", sale.customerGstin())
            .addValue("supplyType", (sale.supplyType() != null ? sale.supplyType() : SupplyType.INTRA_STATE).name())
            .addValue("placeOfSupply", sale.placeOfSupply())
            .addValue("billingAddress", sale.billingAddress())
            .addValue("shippingAddress", sale.shippingAddress())
            .addValue("customerReference", sale.customerReference())
            .addValue("paymentTerms", sale.paymentTerms())
            .addValue("dueDate", sale.dueDate())
            .addValue("saleDate", sale.saleDate())
            .addValue("subtotal", sale.subtotal())
            .addValue("discountAmount", sale.discountAmount())
            .addValue("taxableAmount", sale.taxableAmount())
            .addValue("cgstAmount", sale.cgstAmount())
            .addValue("sgstAmount", sale.sgstAmount())
            .addValue("igstAmount", sale.igstAmount())
            .addValue("totalTaxAmount", sale.totalTaxAmount())
            .addValue("roundOffAmount", sale.grandTotal().subtract(sale.taxableAmount()).subtract(sale.totalTaxAmount()))
            .addValue("grandTotal", sale.grandTotal())
            .addValue("paidAmount", sale.paidAmount())
            .addValue("balanceAmount", sale.balanceAmount())
            .addValue("status", sale.status().name())
            .addValue("paymentStatus", sale.paymentStatus().name())
            .addValue("notes", sale.notes())
            .addValue("id", sale.id());

        return jdbcTemplate.queryForObject(sql, params, saleRowMapper);
    }

    @Override
    public Optional<Sales> findByUuid(UUID uuid) {
        return jdbcTemplate.query(
            "SELECT * FROM sales WHERE uuid = :uuid",
            new MapSqlParameterSource("uuid", uuid),
            saleRowMapper
        ).stream().findFirst();
    }

    @Override
    public Optional<Sales> findByUuidForUpdate(UUID uuid) {
        return jdbcTemplate.query(
            "SELECT * FROM sales WHERE uuid = :uuid FOR UPDATE",
            new MapSqlParameterSource("uuid", uuid),
            saleRowMapper
        ).stream().findFirst();
    }

    private static final Map<String, String> SALES_SORT_ALLOWLIST = Map.of(
        "id", "s.id",
        "invoiceNumber", "s.invoice_number",
        "customerName", "s.customer_name",
        "saleDate", "s.sale_date",
        "grandTotal", "s.grand_total",
        "balanceAmount", "s.balance_amount",
        "status", "s.status",
        "paymentStatus", "s.payment_status",
        "createdAt", "s.created_at"
    );

    @Override
    public List<Sales> findAll() {
        return jdbcTemplate.query(
            "SELECT * FROM sales ORDER BY created_at DESC, id DESC LIMIT 501",
            new MapSqlParameterSource(),
            saleRowMapper
        );
    }

    @Override
    public List<Sales> findPaged(
        int offset,
        int limit,
        String search,
        String status,
        String paymentStatus,
        LocalDate fromDate,
        LocalDate toDate,
        UUID customerUuid,
        String paymentMethod,
        String returnStatus,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String sortBy,
        String sortDir
    ) {
        StringBuilder sql = new StringBuilder("SELECT s.* FROM sales s WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applySalesFilters(sql, params, search, status, paymentStatus, fromDate, toDate, customerUuid, paymentMethod, returnStatus, minAmount, maxAmount);

        String sortColumn = SALES_SORT_ALLOWLIST.getOrDefault(sortBy, "s.id");
        String direction = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(sortColumn).append(" ").append(direction);
        if (!"s.id".equals(sortColumn)) {
            sql.append(", s.id DESC");
        }
        sql.append(" LIMIT :limit OFFSET :offset");

        params.addValue("limit", Math.max(1, Math.min(limit, 100)));
        params.addValue("offset", Math.max(0, offset));

        return jdbcTemplate.query(sql.toString(), params, saleRowMapper);
    }

    @Override
    public long countPaged(
        String search,
        String status,
        String paymentStatus,
        LocalDate fromDate,
        LocalDate toDate,
        UUID customerUuid,
        String paymentMethod,
        String returnStatus,
        BigDecimal minAmount,
        BigDecimal maxAmount
    ) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sales s WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        applySalesFilters(sql, params, search, status, paymentStatus, fromDate, toDate, customerUuid, paymentMethod, returnStatus, minAmount, maxAmount);

        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    private void applySalesFilters(
        StringBuilder sql,
        MapSqlParameterSource params,
        String search,
        String status,
        String paymentStatus,
        LocalDate fromDate,
        LocalDate toDate,
        UUID customerUuid,
        String paymentMethod,
        String returnStatus,
        BigDecimal minAmount,
        BigDecimal maxAmount
    ) {
        if (search != null && !search.isBlank()) {
            sql.append("""
                 AND (
                    s.invoice_number ILIKE :search
                    OR s.customer_name ILIKE :search
                    OR s.customer_phone ILIKE :search
                    OR EXISTS (
                        SELECT 1 FROM sales_products sp
                        WHERE sp.sale_id = s.id
                          AND (sp.product_name ILIKE :search OR sp.product_code ILIKE :search)
                    )
                 )
                """);
            params.addValue("search", "%" + search.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }
        if (paymentStatus != null && !paymentStatus.isBlank()) {
            sql.append(" AND s.payment_status = :paymentStatus");
            params.addValue("paymentStatus", paymentStatus.trim().toUpperCase());
        }
        if (fromDate != null) {
            sql.append(" AND s.sale_date >= :fromDate");
            params.addValue("fromDate", fromDate);
        }
        if (toDate != null) {
            sql.append(" AND s.sale_date <= :toDate");
            params.addValue("toDate", toDate);
        }
        if (customerUuid != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM customers c WHERE c.id = s.customer_id AND c.uuid = :customerUuid)");
            params.addValue("customerUuid", customerUuid);
        }
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            sql.append(" AND EXISTS (SELECT 1 FROM sales_payments p WHERE p.sale_id = s.id AND p.payment_method = :paymentMethod)");
            params.addValue("paymentMethod", paymentMethod.trim().toUpperCase());
        }
        if (minAmount != null) {
            sql.append(" AND s.grand_total >= :minAmount");
            params.addValue("minAmount", minAmount);
        }
        if (maxAmount != null) {
            sql.append(" AND s.grand_total <= :maxAmount");
            params.addValue("maxAmount", maxAmount);
        }
        if (returnStatus != null && !returnStatus.isBlank()) {
            switch (returnStatus.trim().toUpperCase()) {
                case "NO_RETURNS" -> sql.append(" AND NOT EXISTS (SELECT 1 FROM sales_returns sr WHERE sr.sale_id = s.id)");
                case "PARTIALLY_RETURNED" -> sql.append("""
                     AND EXISTS (SELECT 1 FROM sales_returns sr WHERE sr.sale_id = s.id)
                     AND EXISTS (
                        SELECT 1 FROM sales_products sp
                        LEFT JOIN (
                            SELECT srp.sales_product_id, SUM(srp.quantity) AS ret_qty
                            FROM sales_return_products srp
                            GROUP BY srp.sales_product_id
                        ) rq ON rq.sales_product_id = sp.id
                        WHERE sp.sale_id = s.id AND COALESCE(rq.ret_qty, 0) < sp.quantity
                     )
                    """);
                case "FULLY_RETURNED" -> sql.append("""
                     AND EXISTS (SELECT 1 FROM sales_returns sr WHERE sr.sale_id = s.id)
                     AND NOT EXISTS (
                        SELECT 1 FROM sales_products sp
                        LEFT JOIN (
                            SELECT srp.sales_product_id, SUM(srp.quantity) AS ret_qty
                            FROM sales_return_products srp
                            GROUP BY srp.sales_product_id
                        ) rq ON rq.sales_product_id = sp.id
                        WHERE sp.sale_id = s.id AND COALESCE(rq.ret_qty, 0) < sp.quantity
                     )
                    """);
                default -> { }
            }
        }
    }

    @Override
    public void createSaleProduct(SalesProduct product) {
        String sql = """
            INSERT INTO sales_products (
                uuid, sale_id, product_id, product_code, product_name,
                quantity, unit, unit_price, discount_amount, taxable_amount,
                hsn_code, gst_rate, cgst_rate, sgst_rate, igst_rate,
                cgst_amount, sgst_amount, igst_amount, total_tax_amount, line_total
            )
            VALUES (
                :uuid, :saleId, :productId, :productCode, :productName,
                :quantity, :unit, :unitPrice, :discountAmount, :taxableAmount,
                :hsnCode, :gstRate, :cgstRate, :sgstRate, :igstRate,
                :cgstAmount, :sgstAmount, :igstAmount, :totalTaxAmount, :lineTotal
            )
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("uuid", product.uuid())
            .addValue("saleId", product.saleId())
            .addValue("productId", product.productId())
            .addValue("productCode", product.productCode())
            .addValue("productName", product.productName())
            .addValue("quantity", product.quantity())
            .addValue("unit", product.unit())
            .addValue("unitPrice", product.unitPrice())
            .addValue("discountAmount", product.discountAmount())
            .addValue("taxableAmount", product.taxableAmount())
            .addValue("hsnCode", product.hsnCode())
            .addValue("gstRate", product.gstRate())
            .addValue("cgstRate", product.cgstRate())
            .addValue("sgstRate", product.sgstRate())
            .addValue("igstRate", product.igstRate())
            .addValue("cgstAmount", product.cgstAmount())
            .addValue("sgstAmount", product.sgstAmount())
            .addValue("igstAmount", product.igstAmount())
            .addValue("totalTaxAmount", product.totalTaxAmount())
            .addValue("lineTotal", product.lineTotal());

        jdbcTemplate.update(sql, params);
    }

    @Override
    public List<SalesProduct> findProductsBySaleId(Long saleId) {
        String sql = """
            SELECT
                sp.id, sp.uuid, sp.sale_id, sp.product_id, p.uuid AS product_uuid,
                sp.product_code, sp.product_name, sp.quantity, sp.unit, sp.unit_price,
                sp.discount_amount, sp.taxable_amount, sp.hsn_code,
                sp.gst_rate, sp.cgst_rate, sp.sgst_rate, sp.igst_rate,
                sp.cgst_amount, sp.sgst_amount, sp.igst_amount, sp.total_tax_amount,
                sp.line_total, sp.created_at
            FROM sales_products sp
            JOIN products p ON p.id = sp.product_id
            WHERE sp.sale_id = :saleId
            ORDER BY sp.id
            """;

        return jdbcTemplate.query(sql, new MapSqlParameterSource("saleId", saleId), (rs, rowNum) -> mapSaleProduct(rs));
    }

    @Override
    public Map<Long, List<SalesProduct>> findProductsBySaleIds(List<Long> saleIds) {
        if (saleIds == null || saleIds.isEmpty()) return Collections.emptyMap();
        String sql = """
            SELECT
                sp.id, sp.uuid, sp.sale_id, sp.product_id, p.uuid AS product_uuid,
                sp.product_code, sp.product_name, sp.quantity, sp.unit, sp.unit_price,
                sp.discount_amount, sp.taxable_amount, sp.hsn_code,
                sp.gst_rate, sp.cgst_rate, sp.sgst_rate, sp.igst_rate,
                sp.cgst_amount, sp.sgst_amount, sp.igst_amount, sp.total_tax_amount,
                sp.line_total, sp.created_at
            FROM sales_products sp
            JOIN products p ON p.id = sp.product_id
            WHERE sp.sale_id IN (:saleIds)
            ORDER BY sp.sale_id, sp.id
            """;
        Map<Long, List<SalesProduct>> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource("saleIds", saleIds), rs -> {
            SalesProduct item = mapSaleProduct(rs);
            result.computeIfAbsent(item.saleId(), k -> new ArrayList<>()).add(item);
        });
        return result;
    }

    private SalesProduct mapSaleProduct(ResultSet rs) throws SQLException {
        return new SalesProduct(
            rs.getLong("id"),
            rs.getObject("uuid", UUID.class),
            rs.getLong("sale_id"),
            rs.getLong("product_id"),
            rs.getObject("product_uuid", UUID.class),
            rs.getString("product_code"),
            rs.getString("product_name"),
            rs.getBigDecimal("quantity"),
            rs.getString("unit"),
            rs.getBigDecimal("unit_price"),
            rs.getBigDecimal("discount_amount"),
            rs.getBigDecimal("taxable_amount"),
            rs.getString("hsn_code"),
            rs.getBigDecimal("gst_rate"),
            rs.getBigDecimal("cgst_rate"),
            rs.getBigDecimal("sgst_rate"),
            rs.getBigDecimal("igst_rate"),
            rs.getBigDecimal("cgst_amount"),
            rs.getBigDecimal("sgst_amount"),
            rs.getBigDecimal("igst_amount"),
            rs.getBigDecimal("total_tax_amount"),
            rs.getBigDecimal("line_total"),
            rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    @Override
    public void createSalePayment(SalesPayment payment) {
        String sql = """
            INSERT INTO sales_payments (
                uuid, sale_id, payment_method, amount, reference_number, payment_date, notes
            )
            VALUES (:uuid, :saleId, :paymentMethod, :amount, :referenceNumber, :paymentDate, :notes)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("uuid", payment.uuid())
            .addValue("saleId", payment.saleId())
            .addValue("paymentMethod", payment.paymentMethod().name())
            .addValue("amount", payment.amount())
            .addValue("referenceNumber", payment.referenceNumber())
            .addValue("paymentDate", payment.paymentDate())
            .addValue("notes", payment.notes());

        jdbcTemplate.update(sql, params);
    }

    @Override
    public List<SalesPayment> findPaymentsBySaleId(Long saleId) {
        return jdbcTemplate.query(
            "SELECT * FROM sales_payments WHERE sale_id = :saleId ORDER BY payment_date, id",
            new MapSqlParameterSource("saleId", saleId),
            paymentRowMapper
        );
    }

    @Override
    public Map<Long, List<SalesPayment>> findPaymentsBySaleIds(List<Long> saleIds) {
        if (saleIds == null || saleIds.isEmpty()) return Collections.emptyMap();
        Map<Long, List<SalesPayment>> result = new LinkedHashMap<>();
        jdbcTemplate.query(
            "SELECT * FROM sales_payments WHERE sale_id IN (:saleIds) ORDER BY sale_id, payment_date, id",
            new MapSqlParameterSource("saleIds", saleIds),
            rs -> {
                SalesPayment payment = paymentRowMapper.mapRow(rs, 0);
                if (payment != null) {
                    result.computeIfAbsent(payment.saleId(), k -> new ArrayList<>()).add(payment);
                }
            }
        );
        return result;
    }

    @Override
    public Map<Long, String> findCancellationReasonsBySaleIds(List<Long> saleIds) {
        if (saleIds == null || saleIds.isEmpty()) return Collections.emptyMap();
        Map<Long, String> result = new LinkedHashMap<>();
        jdbcTemplate.query(
            "SELECT id, cancellation_reason FROM sales WHERE id IN (:saleIds) AND cancellation_reason IS NOT NULL",
            new MapSqlParameterSource("saleIds", saleIds),
            rs -> {
                result.put(rs.getLong("id"), rs.getString("cancellation_reason"));
            }
        );
        return result;
    }

    @Override
    public Map<Long, UUID> findCustomerUuidsByIds(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) return Collections.emptyMap();
        Map<Long, UUID> result = new LinkedHashMap<>();
        jdbcTemplate.query(
            "SELECT id, uuid FROM customers WHERE id IN (:customerIds)",
            new MapSqlParameterSource("customerIds", customerIds),
            rs -> {
                result.put(rs.getLong("id"), rs.getObject("uuid", UUID.class));
            }
        );
        return result;
    }

    @Override
    public Map<Long, SaleReturnAggregate> findReturnAggregatesBySaleIds(List<Long> saleIds) {
        if (saleIds == null || saleIds.isEmpty()) return Collections.emptyMap();
        String sql = """
            SELECT
                s.id AS sale_id,
                COALESCE(ret_tot.returned_amount, 0) AS returned_amount,
                COALESCE(ref_tot.refunded_amount, 0) AS refunded_amount,
                COALESCE(line_stats.returned_lines, 0) AS returned_lines,
                COALESCE(line_stats.fully_returned_lines, 0) AS fully_returned_lines,
                COALESCE(line_stats.total_lines, 0) AS total_lines
            FROM sales s
            LEFT JOIN (
                SELECT sale_id, SUM(total_return_amount) AS returned_amount
                FROM sales_returns
                WHERE sale_id IN (:saleIds)
                GROUP BY sale_id
            ) ret_tot ON ret_tot.sale_id = s.id
            LEFT JOIN (
                SELECT sr.sale_id, SUM(sref.amount) AS refunded_amount
                FROM sales_refunds sref
                JOIN sales_returns sr ON sr.id = sref.sales_return_id
                WHERE sr.sale_id IN (:saleIds)
                GROUP BY sr.sale_id
            ) ref_tot ON ref_tot.sale_id = s.id
            LEFT JOIN (
                SELECT
                    sp.sale_id,
                    COUNT(*) AS total_lines,
                    COUNT(*) FILTER (WHERE COALESCE(rq.ret_qty, 0) > 0) AS returned_lines,
                    COUNT(*) FILTER (WHERE COALESCE(rq.ret_qty, 0) >= sp.quantity) AS fully_returned_lines
                FROM sales_products sp
                LEFT JOIN (
                    SELECT srp.sales_product_id, SUM(srp.quantity) AS ret_qty
                    FROM sales_return_products srp
                    GROUP BY srp.sales_product_id
                ) rq ON rq.sales_product_id = sp.id
                WHERE sp.sale_id IN (:saleIds)
                GROUP BY sp.sale_id
            ) line_stats ON line_stats.sale_id = s.id
            WHERE s.id IN (:saleIds)
            """;

        Map<Long, SaleReturnAggregate> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, new MapSqlParameterSource("saleIds", saleIds), rs -> {
            result.put(
                rs.getLong("sale_id"),
                new SaleReturnAggregate(
                    rs.getBigDecimal("returned_amount"),
                    rs.getBigDecimal("refunded_amount"),
                    rs.getLong("returned_lines"),
                    rs.getLong("fully_returned_lines"),
                    rs.getLong("total_lines")
                )
            );
        });
        return result;
    }

    @Override
    public void deleteDraftProducts(Long saleId) {
        jdbcTemplate.update(
            "DELETE FROM sales_products WHERE sale_id = :saleId",
            new MapSqlParameterSource("saleId", saleId)
        );
    }

    @Override
    public void deleteDraftPayments(Long saleId) {
        jdbcTemplate.update(
            "DELETE FROM sales_payments WHERE sale_id = :saleId",
            new MapSqlParameterSource("saleId", saleId)
        );
    }

    @Override
    public Optional<Long> findActiveProductId(UUID productUuid) {
        return jdbcTemplate.query(
            "SELECT id FROM products WHERE uuid = :productUuid AND active = TRUE",
            new MapSqlParameterSource("productUuid", productUuid),
            (rs, rowNum) -> rs.getLong("id")
        ).stream().findFirst();
    }

    @Override
    public boolean existsInvoiceNumber(String invoiceNumber) {
        Boolean exists = jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM sales WHERE invoice_number = :invoiceNumber)",
            new MapSqlParameterSource("invoiceNumber", invoiceNumber),
            Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public String generateInvoiceNumber(LocalDate saleDate) {
        int financialYearStart = saleDate.getMonthValue() >= 4
            ? saleDate.getYear() : saleDate.getYear() - 1;
        int financialYearEnd = financialYearStart + 1;
        String prefix = String.format("INV/%02d-%02d",
            financialYearStart % 100, financialYearEnd % 100);

        String lockSql = """
            SELECT last_number
            FROM sales_invoice_counters
            WHERE invoice_prefix = :prefix
            FOR UPDATE
            """;

        Long currentNumber = jdbcTemplate.query(lockSql,
            new MapSqlParameterSource("prefix", prefix),
            (rs, rowNum) -> rs.getLong("last_number"))
            .stream().findFirst().orElse(null);

        if (currentNumber == null) {
            try {
                jdbcTemplate.update("""
                    INSERT INTO sales_invoice_counters (
                        invoice_prefix, last_number, updated_at
                    ) VALUES (:prefix, 0, CURRENT_TIMESTAMP)
                    ON CONFLICT (invoice_prefix) DO NOTHING
                    """, new MapSqlParameterSource("prefix", prefix));
            } catch (DuplicateKeyException ignored) { }
            currentNumber = jdbcTemplate.query(lockSql,
                new MapSqlParameterSource("prefix", prefix),
                (rs, rowNum) -> rs.getLong("last_number"))
                .stream().findFirst().orElse(0L);
        }

        long nextNumber = currentNumber + 1;
        if (nextNumber > 99999) {
            throw new IllegalStateException("Financial-year invoice number limit reached");
        }

        jdbcTemplate.update("""
            UPDATE sales_invoice_counters
            SET last_number = :nextNumber, updated_at = CURRENT_TIMESTAMP
            WHERE invoice_prefix = :prefix
            """, new MapSqlParameterSource()
                .addValue("nextNumber", nextNumber)
                .addValue("prefix", prefix));

        return prefix + "/" + String.format("%05d", nextNumber);
    }

    @Override
    public void saveCancellationReason(Long saleId, String reason) {
        jdbcTemplate.update(
            "UPDATE sales SET cancellation_reason = :reason WHERE id = :saleId",
            new MapSqlParameterSource().addValue("reason", reason).addValue("saleId", saleId));
    }

    @Override
    public Optional<String> findCancellationReason(Long saleId) {
        List<String> reasons = jdbcTemplate.query(
            "SELECT cancellation_reason FROM sales WHERE id = :saleId",
            new MapSqlParameterSource("saleId", saleId),
            (rs, rowNum) -> rs.getString("cancellation_reason")
        );
        return reasons.isEmpty() ? Optional.empty() : Optional.ofNullable(reasons.get(0));
    }

    @Override
    public Optional<UUID> findUuidById(Long saleId) {
        return jdbcTemplate.query(
            "SELECT uuid FROM sales WHERE id = :saleId",
            new MapSqlParameterSource("saleId", saleId),
            (rs, rowNum) -> rs.getObject("uuid", UUID.class)
        ).stream().findFirst();
    }

    @Override
    public SalesSummaryResponse getSummary(LocalDate fromDate, LocalDate toDate) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder dateFilter = new StringBuilder(" WHERE 1=1");
        if (fromDate != null) {
            dateFilter.append(" AND sale_date >= :fromDate");
            params.addValue("fromDate", fromDate);
        }
        if (toDate != null) {
            dateFilter.append(" AND sale_date <= :toDate");
            params.addValue("toDate", toDate);
        }

        String sql = """
            SELECT
                COUNT(*) AS total_invoices,
                COUNT(*) FILTER (WHERE status = 'DRAFT') AS draft_count,
                COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed_count,
                COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_count,
                COUNT(*) FILTER (WHERE status = 'COMPLETED' AND payment_status = 'UNPAID') AS unpaid_count,
                COUNT(*) FILTER (WHERE status = 'COMPLETED' AND payment_status = 'PARTIALLY_PAID') AS partially_paid_count,
                COUNT(*) FILTER (WHERE status = 'COMPLETED' AND payment_status = 'PAID') AS paid_count,
                COALESCE(SUM(grand_total) FILTER (WHERE status = 'COMPLETED'), 0) AS total_sales_amount,
                COALESCE(SUM(paid_amount) FILTER (WHERE status = 'COMPLETED'), 0) AS total_collected_amount,
                COALESCE(SUM(balance_amount) FILTER (WHERE status = 'COMPLETED'), 0) AS outstanding_receivables,
                COALESCE((
                    SELECT SUM(sr.total_return_amount)
                    FROM sales_returns sr
                    JOIN sales s2 ON s2.id = sr.sale_id
                    WHERE (CAST(:fromDate AS DATE) IS NULL OR s2.sale_date >= CAST(:fromDate AS DATE))
                      AND (CAST(:toDate AS DATE) IS NULL OR s2.sale_date <= CAST(:toDate AS DATE))
                ), 0) AS total_returned_amount,
                COALESCE((
                    SELECT SUM(sref.amount)
                    FROM sales_refunds sref
                    JOIN sales_returns sr2 ON sr2.id = sref.sales_return_id
                    JOIN sales s3 ON s3.id = sr2.sale_id
                    WHERE (CAST(:fromDate AS DATE) IS NULL OR s3.sale_date >= CAST(:fromDate AS DATE))
                      AND (CAST(:toDate AS DATE) IS NULL OR s3.sale_date <= CAST(:toDate AS DATE))
                ), 0) AS total_refunded_amount
            FROM sales
            """ + dateFilter;

        if (!params.hasValue("fromDate")) params.addValue("fromDate", null);
        if (!params.hasValue("toDate")) params.addValue("toDate", null);

        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> new SalesSummaryResponse(
            rs.getLong("total_invoices"),
            rs.getLong("draft_count"),
            rs.getLong("completed_count"),
            rs.getLong("cancelled_count"),
            rs.getLong("unpaid_count"),
            rs.getLong("partially_paid_count"),
            rs.getLong("paid_count"),
            rs.getBigDecimal("total_sales_amount"),
            rs.getBigDecimal("total_collected_amount"),
            rs.getBigDecimal("outstanding_receivables"),
            rs.getBigDecimal("total_returned_amount"),
            rs.getBigDecimal("total_refunded_amount")
        ));
    }
}
