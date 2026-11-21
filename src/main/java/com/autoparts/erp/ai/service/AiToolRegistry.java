package com.autoparts.erp.ai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.autoparts.erp.ai.tools.AuditLogLookupTool;
import com.autoparts.erp.ai.tools.CustomerLookupTool;
import com.autoparts.erp.ai.tools.FinancialLookupTool;
import com.autoparts.erp.ai.tools.InventoryHistoryLookupTool;
import com.autoparts.erp.ai.tools.ProductLookupTool;
import com.autoparts.erp.ai.tools.PurchaseLookupTool;
import com.autoparts.erp.ai.tools.ReportLookupTool;
import com.autoparts.erp.ai.tools.ReturnLookupTool;
import com.autoparts.erp.ai.tools.SalesLookupTool;
import com.autoparts.erp.ai.tools.SupplierLookupTool;

/**
 * Registry holding all available Spring JDBC read-only ERP lookup tools.
 */
@Component
public class AiToolRegistry {

    private final ProductLookupTool productLookupTool;
    private final InventoryHistoryLookupTool inventoryHistoryLookupTool;
    private final PurchaseLookupTool purchaseLookupTool;
    private final SalesLookupTool salesLookupTool;
    private final CustomerLookupTool customerLookupTool;
    private final SupplierLookupTool supplierLookupTool;
    private final ReturnLookupTool returnLookupTool;
    private final FinancialLookupTool financialLookupTool;
    private final ReportLookupTool reportLookupTool;
    private final AuditLogLookupTool auditLogLookupTool;

    @Autowired
    public AiToolRegistry(
            ProductLookupTool productLookupTool,
            InventoryHistoryLookupTool inventoryHistoryLookupTool,
            @Autowired(required = false) PurchaseLookupTool purchaseLookupTool,
            @Autowired(required = false) SalesLookupTool salesLookupTool,
            @Autowired(required = false) CustomerLookupTool customerLookupTool,
            @Autowired(required = false) SupplierLookupTool supplierLookupTool,
            @Autowired(required = false) ReturnLookupTool returnLookupTool,
            @Autowired(required = false) FinancialLookupTool financialLookupTool,
            @Autowired(required = false) ReportLookupTool reportLookupTool,
            @Autowired(required = false) AuditLogLookupTool auditLogLookupTool
    ) {
        this.productLookupTool = productLookupTool;
        this.inventoryHistoryLookupTool = inventoryHistoryLookupTool;
        this.purchaseLookupTool = purchaseLookupTool;
        this.salesLookupTool = salesLookupTool;
        this.customerLookupTool = customerLookupTool;
        this.supplierLookupTool = supplierLookupTool;
        this.returnLookupTool = returnLookupTool;
        this.financialLookupTool = financialLookupTool;
        this.reportLookupTool = reportLookupTool;
        this.auditLogLookupTool = auditLogLookupTool;
    }

    public ProductLookupTool productLookupTool() { return productLookupTool; }
    public InventoryHistoryLookupTool inventoryHistoryLookupTool() { return inventoryHistoryLookupTool; }
    public PurchaseLookupTool purchaseLookupTool() { return purchaseLookupTool; }
    public SalesLookupTool salesLookupTool() { return salesLookupTool; }
    public CustomerLookupTool customerLookupTool() { return customerLookupTool; }
    public SupplierLookupTool supplierLookupTool() { return supplierLookupTool; }
    public ReturnLookupTool returnLookupTool() { return returnLookupTool; }
    public FinancialLookupTool financialLookupTool() { return financialLookupTool; }
    public ReportLookupTool reportLookupTool() { return reportLookupTool; }
    public AuditLogLookupTool auditLogLookupTool() { return auditLogLookupTool; }
}
