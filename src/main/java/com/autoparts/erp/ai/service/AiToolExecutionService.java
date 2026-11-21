package com.autoparts.erp.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.model.AiEntityReference;
import com.autoparts.erp.ai.model.AiIntentPlan;
import com.autoparts.erp.ai.model.AiOperation;
import com.autoparts.erp.ai.model.AiOperationResult;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;

/**
 * Executes ERP tool operations with comprehensive audit logging, metrics tracking,
 * and security authorization checking.
 */
@Service
public class AiToolExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AiToolExecutionService.class);

    private final AiToolRegistry tools;
    private final AiMetricsService metrics;
    private final AiToolAuditService auditService;

    @Autowired
    public AiToolExecutionService(
            AiToolRegistry tools,
            AiMetricsService metrics,
            AiToolAuditService auditService
    ) {
        this.tools = tools;
        this.metrics = metrics != null ? metrics : new AiMetricsService();
        this.auditService = auditService;
    }

    public AiOperationResult executeTool(AiIntentPlan plan, ErpAiExecutionContext context) {
        if (context == null) {
            log.warn("ERP tool execution rejected: No execution context provided. Operation: {}", plan.operation());
            return AiOperationResult.accessDenied(plan.operation(), "ACCESS_DENIED: No execution context provided. Access denied.");
        }

        String operationName = plan.operation().name();
        String searchText = plan.effectiveSearchText();
        int limit = plan.limit() != null ? plan.limit() : 10;
        long startNanos = System.nanoTime();
        String auditStatus = "SUCCESS";
        String safeError = null;
        Object resultForAudit = null;

        log.info("ERP tool execution started. Operation: {}, search: {}", operationName, searchText);

        try {
            Object rawResult = invokeTool(plan, context);
            resultForAudit = rawResult;
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            int rowCount = countRows(rawResult);

            metrics.recordTool(operationName, "SUCCESS", System.nanoTime() - startNanos, rowCount);

            List<AiEntityReference> extracted = extractEntitiesFromResult(rawResult);
            log.info("ERP tool execution completed. Operation: {}, rows: {}, duration: {}ms", operationName, rowCount, durationMs);
            return AiOperationResult.success(plan.operation(), rawResult, extracted);

        } catch (ToolAuthorizationException authEx) {
            auditStatus = "ACCESS_DENIED";
            safeError = authEx.getMessage();
            metrics.recordTool(operationName, "ACCESS_DENIED", System.nanoTime() - startNanos, 0);
            log.warn("ERP tool execution authorization denied: {}", authEx.getMessage());
            return AiOperationResult.accessDenied(plan.operation(), "ACCESS_DENIED: " + authEx.getMessage());

        } catch (CancellationException cancelEx) {
            auditStatus = "CANCELLED";
            safeError = "Cancelled";
            metrics.recordTool(operationName, "CANCELLED", System.nanoTime() - startNanos, 0);
            throw cancelEx;

        } catch (Exception ex) {
            auditStatus = "ERROR";
            safeError = ex.getMessage();
            metrics.recordTool(operationName, "ERROR", System.nanoTime() - startNanos, 0);
            log.error("ERP tool execution failed. Operation: {}, error: {}", operationName, ex.getMessage(), ex);
            return AiOperationResult.failure(plan.operation(), "Unable to retrieve the requested ERP information.");

        } finally {
            if (auditService != null) {
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
                auditService.recordExecution(
                        context,
                        operationName,
                        searchText,
                        plan.startDate(),
                        plan.endDate(),
                        limit,
                        resultForAudit,
                        auditStatus,
                        safeError,
                        durationMs
                );
            }
        }
    }

    private Object invokeTool(AiIntentPlan plan, ErpAiExecutionContext context) {
        String searchText = plan.effectiveSearchText();
        int limit = plan.limit() != null ? plan.limit() : 10;
        String start = plan.startDate();
        String end = plan.endDate();

        return switch (plan.operation()) {
            case PRODUCT_SEARCH ->
                    tools.productLookupTool().searchProducts(searchText, context);
            case PRODUCT_COMPARISON -> {
                List<String> idents = plan.entityReferences().stream()
                        .map(AiEntityReference::identifier)
                        .filter(StringUtils::hasText)
                        .toList();
                yield tools.productLookupTool().getProductsByIdentifiers(idents, context);
            }
            case PRODUCT_DETAILS ->
                    tools.productLookupTool().getProductDetails(searchText, context);
            case PRODUCT_STOCK, PRODUCT_MINIMUM_STOCK, PRODUCT_AVAILABLE_QUANTITY ->
                    tools.productLookupTool().getProductStock(searchText, context);
            case PRODUCT_COUNT ->
                    tools.productLookupTool().countProducts(context);
            case PRODUCT_LOW_STOCK ->
                    tools.productLookupTool().getLowStockProducts(context);

            case INVENTORY_LOOKUP, PRODUCT_MOVEMENT_HISTORY ->
                    tools.inventoryHistoryLookupTool().getProductMovementHistory(searchText, context);
            case PRODUCT_MOVEMENT_SUMMARY ->
                    tools.inventoryHistoryLookupTool().getProductMovementSummary(searchText, context);
            case PRODUCT_MOVEMENTS_BY_DATE_RANGE ->
                    tools.inventoryHistoryLookupTool().getProductMovementsByDateRange(searchText, start, end, context);
            case RECENT_INVENTORY_MOVEMENTS ->
                    tools.inventoryHistoryLookupTool().getRecentInventoryMovements(limit, context);
            case INVENTORY_ADJUSTMENTS ->
                    tools.inventoryHistoryLookupTool().getInventoryAdjustments(searchText, limit, context);

            case PURCHASE_SEARCH, PURCHASE_LOOKUP -> {
                if (tools.purchaseLookupTool() != null) {
                    if (StringUtils.hasText(start) && StringUtils.hasText(end)) {
                        yield tools.purchaseLookupTool().searchPurchases(searchText, start, end, context);
                    }
                    yield tools.purchaseLookupTool().searchPurchases(searchText, context);
                }
                yield List.of();
            }
            case PURCHASE_DETAILS ->
                    tools.purchaseLookupTool() != null ? tools.purchaseLookupTool().getPurchaseDetails(searchText, context) : Map.of();
            case PURCHASE_DATE_RANGE ->
                    tools.purchaseLookupTool() != null ? tools.purchaseLookupTool().getPurchasesByDateRange(start, end, context) : List.of();
            case PURCHASES_BY_SUPPLIER ->
                    tools.purchaseLookupTool() != null ? tools.purchaseLookupTool().getPurchasesBySupplier(searchText, context) : List.of();
            case PURCHASE_SUMMARY ->
                    tools.purchaseLookupTool() != null ? tools.purchaseLookupTool().getPurchaseSummary(start, end, context) : Map.of();
            case PURCHASE_STATUS ->
                    tools.purchaseLookupTool() != null ? tools.purchaseLookupTool().getPurchaseStatus(searchText, context) : Map.of();

            case SALES_SEARCH, SALES_LOOKUP ->
                    tools.salesLookupTool() != null ? tools.salesLookupTool().searchSales(searchText, context) : List.of();
            case SALE_DETAILS ->
                    tools.salesLookupTool() != null ? tools.salesLookupTool().getSaleDetails(searchText, context) : Map.of();
            case SALES_DATE_RANGE ->
                    tools.salesLookupTool() != null ? tools.salesLookupTool().getSalesByDateRange(start, end, context) : List.of();
            case SALES_BY_CUSTOMER ->
                    tools.salesLookupTool() != null ? tools.salesLookupTool().getSalesByCustomer(searchText, context) : List.of();
            case SALES_SUMMARY ->
                    tools.salesLookupTool() != null ? tools.salesLookupTool().getSalesSummary(start, end, context) : Map.of();
            case SALE_STATUS ->
                    tools.salesLookupTool() != null ? tools.salesLookupTool().getSaleStatus(searchText, context) : Map.of();

            case CUSTOMER_SEARCH, CUSTOMER_LOOKUP ->
                    tools.customerLookupTool() != null ? tools.customerLookupTool().searchCustomers(searchText, context) : List.of();
            case CUSTOMER_DETAILS ->
                    tools.customerLookupTool() != null ? tools.customerLookupTool().getCustomerDetails(searchText, context) : Map.of();
            case CUSTOMER_SALES_HISTORY ->
                    tools.customerLookupTool() != null ? tools.customerLookupTool().getCustomerSalesHistory(searchText, context) : List.of();
            case CUSTOMER_OUTSTANDING ->
                    tools.customerLookupTool() != null ? tools.customerLookupTool().getCustomerOutstandingBalance(searchText, context) : Map.of();

            case SUPPLIER_SEARCH, SUPPLIER_LOOKUP ->
                    tools.supplierLookupTool() != null ? tools.supplierLookupTool().searchSuppliers(searchText, context) : List.of();
            case SUPPLIER_DETAILS ->
                    tools.supplierLookupTool() != null ? tools.supplierLookupTool().getSupplierDetails(searchText, context) : Map.of();
            case SUPPLIER_PURCHASE_HISTORY ->
                    tools.supplierLookupTool() != null ? tools.supplierLookupTool().getSupplierPurchaseHistory(searchText, context) : List.of();
            case SUPPLIER_SUMMARY ->
                    tools.supplierLookupTool() != null ? tools.supplierLookupTool().getSupplierPurchaseSummary(searchText, context) : Map.of();

            case SALES_RETURN_SEARCH, RETURN_SUMMARY -> {
                if (plan.operation() == AiOperation.RETURN_SUMMARY) {
                    yield tools.returnLookupTool() != null ? tools.returnLookupTool().getReturnSummary(start, end, context) : Map.of();
                }
                yield tools.returnLookupTool() != null ? tools.returnLookupTool().searchSalesReturns(searchText, context) : List.of();
            }
            case SALES_RETURN_DETAILS ->
                    tools.returnLookupTool() != null ? tools.returnLookupTool().getSalesReturnDetails(searchText, context) : Map.of();
            case PURCHASE_RETURN_SEARCH ->
                    tools.returnLookupTool() != null ? tools.returnLookupTool().searchPurchaseReturns(searchText, context) : List.of();
            case PURCHASE_RETURN_DETAILS ->
                    tools.returnLookupTool() != null ? tools.returnLookupTool().getPurchaseReturnDetails(searchText, context) : Map.of();

            case OPEN_CASH_REGISTERS, CASH_REGISTER_STATUS, FINANCIAL_LOOKUP ->
                    tools.financialLookupTool() != null ? tools.financialLookupTool().getOpenCashRegisterStatus(context) : List.of();
            case EXPENSE_SUMMARY ->
                    tools.financialLookupTool() != null ? tools.financialLookupTool().getExpenseSummary(start, end, context) : Map.of();
            case EXPENSES_BY_CATEGORY, EXPENSE_BY_CATEGORY ->
                    tools.financialLookupTool() != null ? tools.financialLookupTool().getExpensesByCategory(searchText, start, end, context) : List.of();
            case DAILY_CLOSING ->
                    tools.financialLookupTool() != null ? tools.financialLookupTool().getDailyClosing(start, context) : Map.of();
            case CASH_DISCREPANCIES ->
                    tools.financialLookupTool() != null ? tools.financialLookupTool().getCashDiscrepancies(start, end, context) : List.of();

            case KPI_SUMMARY, REPORT_LOOKUP ->
                    tools.reportLookupTool() != null ? tools.reportLookupTool().getKpiSummary(start, end, context) : Map.of();
            case INVENTORY_ABC_ANALYSIS, INVENTORY_ABC_XYZ ->
                    tools.reportLookupTool() != null ? tools.reportLookupTool().getInventoryAbcXyzAnalysis(context) : List.of();
            case CUSTOMER_RFM_ANALYSIS, CUSTOMER_RFM ->
                    tools.reportLookupTool() != null ? tools.reportLookupTool().getCustomerRfmAnalysis(context) : List.of();
            case RECONCILIATION_DISCREPANCIES ->
                    tools.reportLookupTool() != null ? tools.reportLookupTool().getReconciliationDiscrepancies(start, end, context) : Map.of();
            case COGS_PROFIT_EXPLANATION ->
                    tools.reportLookupTool() != null ? tools.reportLookupTool().explainCogsLimitation(context) : Map.of();

            case AUDIT_LOG_SEARCH, AUDIT_LOOKUP ->
                    tools.auditLogLookupTool() != null ? tools.auditLogLookupTool().getRecentAuditLogs(searchText, limit, context) : List.of();
            case AI_TOOL_RUNS_LOG ->
                    tools.auditLogLookupTool() != null ? tools.auditLogLookupTool().getRecentAiToolRuns(searchText, limit, context) : List.of();

            default -> throw new IllegalArgumentException("Unsupported tool operation: " + plan.operation());
        };
    }

    private int countRows(Object result) {
        if (result instanceof List<?> l) return l.size();
        if (result instanceof Map<?, ?> m) return m.isEmpty() ? 0 : 1;
        if (result != null) return 1;
        return 0;
    }

    private List<AiEntityReference> extractEntitiesFromResult(Object result) {
        List<AiEntityReference> list = new ArrayList<>();
        if (result instanceof List<?> rows) {
            for (Object r : rows) {
                if (r instanceof Map<?, ?> m) {
                    addEntityIfPresent(m, list);
                }
            }
        } else if (result instanceof Map<?, ?> m) {
            addEntityIfPresent(m, list);
        }
        return list;
    }

    private void addEntityIfPresent(Map<?, ?> m, List<AiEntityReference> list) {
        Object code = m.get("product_code");
        if (code != null) {
            String name = m.get("name") != null ? String.valueOf(m.get("name")) : null;
            String part = m.get("part_no") != null ? String.valueOf(m.get("part_no")) : null;
            list.add(AiEntityReference.ofProduct(String.valueOf(code), name, part));
        }
    }
}
