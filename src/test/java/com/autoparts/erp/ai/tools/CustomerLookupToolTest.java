package com.autoparts.erp.ai.tools;

import com.autoparts.erp.ai.security.ErpAiContextHolder;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerLookupTool Unit and Security Guard Tests")
class CustomerLookupToolTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CustomerLookupTool customerLookupTool;

    private final UUID convId = UUID.randomUUID();
    private ErpAiExecutionContext adminContext;
    private ErpAiExecutionContext managerContext;
    private ErpAiExecutionContext cashierContext;
    private ErpAiExecutionContext unauthorizedContext;

    @BeforeEach
    void setUp() {
        customerLookupTool = new CustomerLookupTool(jdbcTemplate);

        adminContext = ErpAiExecutionContext.create(convId, "admin", List.of("ROLE_ADMIN"));
        managerContext = ErpAiExecutionContext.create(convId, "manager", List.of("ROLE_MANAGER"));
        cashierContext = ErpAiExecutionContext.create(convId, "cashier", List.of("ROLE_CASHIER"));
        unauthorizedContext = ErpAiExecutionContext.create(convId, "guest", List.of("ROLE_USER"));
    }

    @AfterEach
    void tearDown() {
        ErpAiContextHolder.clear();
    }

    // =========================================================================
    // Security Guard & Role Enforcement Tests (@SalesAccess)
    // =========================================================================

    @Test
    @DisplayName("Cashier is permitted for all customer tools (@SalesAccess)")
    void shouldPermitCashierOnAllCustomerTools() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("customer_code", "CUS000001", "customer_name", "Acme Auto")));

        var searchResult = customerLookupTool.searchCustomers("Acme", cashierContext);
        assertFalse(searchResult.isEmpty());

        var details = customerLookupTool.getCustomerDetails("CUS000001", cashierContext);
        assertFalse(details.isEmpty());

        var history = customerLookupTool.getCustomerSalesHistory("CUS000001", cashierContext);
        assertFalse(history.isEmpty());

        var balance = customerLookupTool.getCustomerOutstandingBalance("CUS000001", cashierContext);
        assertFalse(balance.isEmpty());

        verify(jdbcTemplate, atLeast(4)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Unauthorized role is blocked with zero JDBC calls")
    void shouldBlockUnauthorizedRoleWithZeroJdbcCalls() {
        assertThrows(ToolAuthorizationException.class,
                () -> customerLookupTool.searchCustomers("Acme", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> customerLookupTool.getCustomerDetails("CUS000001", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> customerLookupTool.getCustomerSalesHistory("CUS000001", unauthorizedContext));

        assertThrows(ToolAuthorizationException.class,
                () -> customerLookupTool.getCustomerOutstandingBalance("CUS000001", unauthorizedContext));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Missing execution context fails closed with zero JDBC calls")
    void shouldFailClosedWhenContextIsMissing() {
        ErpAiContextHolder.clear();

        assertThrows(ToolAuthorizationException.class,
                () -> customerLookupTool.searchCustomers("Acme"));

        assertThrows(ToolAuthorizationException.class,
                () -> customerLookupTool.getCustomerDetails("CUS000001"));

        assertThrows(ToolAuthorizationException.class,
                () -> customerLookupTool.getCustomerSalesHistory("CUS000001"));

        assertThrows(ToolAuthorizationException.class,
                () -> customerLookupTool.getCustomerOutstandingBalance("CUS000001"));

        verifyNoInteractions(jdbcTemplate);
    }

    // =========================================================================
    // Parameter Validation & Query Execution Tests
    // =========================================================================

    @Test
    @DisplayName("Empty or whitespace queries return empty results without querying database")
    void shouldHandleEmptyQueriesGracefully() {
        assertTrue(customerLookupTool.searchCustomers("", cashierContext).isEmpty());
        assertTrue(customerLookupTool.searchCustomers("   ", cashierContext).isEmpty());
        assertTrue(customerLookupTool.getCustomerDetails("", cashierContext).isEmpty());
        assertTrue(customerLookupTool.getCustomerSalesHistory("", cashierContext).isEmpty());
        assertTrue(customerLookupTool.getCustomerOutstandingBalance("", cashierContext).isEmpty());

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("getCustomerDetails searches by customer_code, phone, or customer_name")
    void shouldRetrieveCustomerDetails() {
        Map<String, Object> customer = Map.of(
                "customer_code", "CUS000001",
                "customer_name", "Speedy Garage",
                "phone", "9876543210",
                "city", "Chennai",
                "state", "Tamil Nadu",
                "outstanding_balance", 4500.00
        );

        when(jdbcTemplate.queryForList(contains("FROM customers c"), any(Object[].class)))
                .thenReturn(List.of(customer));

        Map<String, Object> result = customerLookupTool.getCustomerDetails("Speedy Garage", managerContext);
        assertNotNull(result);
        assertEquals("CUS000001", result.get("customer_code"));
        assertEquals("Speedy Garage", result.get("customer_name"));
    }

    @Test
    @DisplayName("getCustomerOutstandingBalance retrieves credit and balance information")
    void shouldRetrieveCustomerOutstandingBalance() {
        Map<String, Object> balance = Map.of(
                "customer_code", "CUS000001",
                "customer_name", "Speedy Garage",
                "phone", "9876543210",
                "total_invoices", 3L,
                "total_invoiced", 15000.00,
                "total_paid", 2500.00,
                "outstanding_balance", 12500.00
        );

        when(jdbcTemplate.queryForList(contains("outstanding_balance"), any(Object[].class)))
                .thenReturn(List.of(balance));

        Map<String, Object> result = customerLookupTool.getCustomerOutstandingBalance("CUS000001", adminContext);
        assertNotNull(result);
        assertEquals(12500.00, result.get("outstanding_balance"));
        assertEquals(3L, result.get("total_invoices"));
    }

    @Test
    @DisplayName("getCustomerOutstandingBalance enforces deterministic ordering on ambiguous partial-name match")
    void shouldOrderDeterministicallyOnPartialNameMatch() {
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql.contains("ORDER BY c.customer_name ASC, c.id ASC")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "customer_code", "CUS000001",
                "customer_name", "Acme Auto Care",
                "phone", "9876543210",
                "total_invoices", 2L,
                "total_invoiced", 10000.00,
                "total_paid", 5000.00,
                "outstanding_balance", 5000.00
        )));

        Map<String, Object> result = customerLookupTool.getCustomerOutstandingBalance("Auto", cashierContext);
        assertNotNull(result);
        assertEquals("Acme Auto Care", result.get("customer_name"));
        verify(jdbcTemplate).queryForList(
                argThat(sql -> sql.contains("ORDER BY c.customer_name ASC, c.id ASC")),
                any(Object[].class)
        );
    }
}
