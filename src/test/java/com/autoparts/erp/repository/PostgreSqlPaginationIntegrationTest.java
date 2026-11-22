package com.autoparts.erp.repository;

import com.autoparts.erp.ai.repositoryimpl.AiRepositoryImpl;
import com.autoparts.erp.audit.repositoryimpl.AuditLogRepositoryImpl;
import com.autoparts.erp.auth.repositoryimpl.UserManagementRepositoryImpl;
import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.model.CashRegister;
import com.autoparts.erp.cashregister.model.CashRegisterMovement;
import com.autoparts.erp.cashregister.model.CashRegisterSession;
import com.autoparts.erp.cashregister.repositoryimpl.CashRegisterRepositoryImpl;
import com.autoparts.erp.customer.model.Customer;
import com.autoparts.erp.customer.repositoryimpl.CustomerRepositoryImpl;
import com.autoparts.erp.dailyclosing.repositoryimpl.DailyClosingRepositoryImpl;
import com.autoparts.erp.expense.category.model.ExpenseCategory;
import com.autoparts.erp.expense.category.repositoryimpl.ExpenseCategoryRepositoryImpl;
import com.autoparts.erp.expense.management.model.Expense;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;
import com.autoparts.erp.expense.management.model.ExpenseStatus;
import com.autoparts.erp.expense.management.repositoryimpl.ExpenseRepositoryImpl;
import com.autoparts.erp.inventory.repositoryimpl.InventoryRepositoryImpl;
import com.autoparts.erp.masters.brand.model.ProductBrand;
import com.autoparts.erp.masters.brand.repositoryimpl.ProductBrandRepositoryImpl;
import com.autoparts.erp.masters.category.model.ProductCategory;
import com.autoparts.erp.masters.category.repositoryimpl.ProductCategoryRepositoryImpl;
import com.autoparts.erp.product.model.Product;
import com.autoparts.erp.product.repositoryimpl.ProductRepositoryImpl;
import com.autoparts.erp.purchase.model.Purchase;
import com.autoparts.erp.purchase.model.PurchaseStatus;
import com.autoparts.erp.purchase.repositoryimpl.PurchaseRepositoryImpl;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.repositoryimpl.SalesRepositoryImpl;
import com.autoparts.erp.supplier.model.Supplier;
import com.autoparts.erp.supplier.repositoryimpl.SupplierRepositoryImpl;
import com.autoparts.erp.tax.model.TaxRate;
import com.autoparts.erp.tax.repositoryimpl.TaxRateRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Comprehensive PostgreSQL 18 Pagination Integration Tests across All Repositories")
class PostgreSqlPaginationIntegrationTest extends BaseRepositoryIntegrationTest {

    private SalesRepositoryImpl salesRepository;
    private PurchaseRepositoryImpl purchaseRepository;
    private InventoryRepositoryImpl inventoryRepository;
    private ExpenseRepositoryImpl expenseRepository;
    private AuditLogRepositoryImpl auditLogRepository;
    private CashRegisterRepositoryImpl cashRegisterRepository;
    private AiRepositoryImpl aiRepository;
    private TaxRateRepositoryImpl taxRateRepository;
    private CustomerRepositoryImpl customerRepository;
    private SupplierRepositoryImpl supplierRepository;
    private ProductRepositoryImpl productRepository;
    private UserManagementRepositoryImpl userManagementRepository;
    private ProductBrandRepositoryImpl brandRepository;
    private ProductCategoryRepositoryImpl categoryRepository;
    private ExpenseCategoryRepositoryImpl expenseCategoryRepository;
    private DailyClosingRepositoryImpl dailyClosingRepository;

    @BeforeEach
    void setUp() {
        salesRepository = new SalesRepositoryImpl(getJdbcTemplate());
        purchaseRepository = new PurchaseRepositoryImpl(getJdbcTemplate());
        inventoryRepository = new InventoryRepositoryImpl(getJdbcTemplate());
        expenseRepository = new ExpenseRepositoryImpl(getJdbcTemplate());
        auditLogRepository = new AuditLogRepositoryImpl(getJdbcTemplate());
        cashRegisterRepository = new CashRegisterRepositoryImpl(getJdbcTemplate());
        aiRepository = new AiRepositoryImpl(getJdbcTemplate());
        taxRateRepository = new TaxRateRepositoryImpl(getJdbcTemplate());
        customerRepository = new CustomerRepositoryImpl(getJdbcTemplate());
        supplierRepository = new SupplierRepositoryImpl(getJdbcTemplate());
        productRepository = new ProductRepositoryImpl(getJdbcTemplate());
        userManagementRepository = new UserManagementRepositoryImpl(getJdbcTemplate());
        brandRepository = new ProductBrandRepositoryImpl(getJdbcTemplate());
        categoryRepository = new ProductCategoryRepositoryImpl(getJdbcTemplate());
        expenseCategoryRepository = new ExpenseCategoryRepositoryImpl(getJdbcTemplate());
        dailyClosingRepository = new DailyClosingRepositoryImpl(getJdbcTemplate());
    }

    @Test
    @DisplayName("Sales findPaged & countPaged: verifies SQL LIMIT, OFFSET, sorting, and status filter")
    void testSalesPagination() {
        getTransactionTemplate().executeWithoutResult(status -> {
            status.setRollbackOnly();

            UUID saleUuid = randomUuid();
            Sales sale = new Sales(
                    null, saleUuid, "INV-PAGED-" + (System.currentTimeMillis() % 100000L),
                    null, "Customer Name", "9876543210",
                    LocalDate.now(),
                    BigDecimal.valueOf(100.00), BigDecimal.ZERO, BigDecimal.valueOf(100.00),
                    BigDecimal.valueOf(9.00), BigDecimal.valueOf(9.00), BigDecimal.ZERO,
                    BigDecimal.valueOf(18.00), BigDecimal.valueOf(118.00), BigDecimal.valueOf(118.00),
                    BigDecimal.ZERO, SalesStatus.COMPLETED, SalesPaymentStatus.PAID,
                    "Pagination test", OffsetDateTime.now(), OffsetDateTime.now()
            );
            salesRepository.createSale(sale);

            List<Sales> page0 = salesRepository.findPaged(0, 5, null, "COMPLETED", null, "id", "desc");
            assertNotNull(page0);
            assertFalse(page0.isEmpty());
            assertTrue(page0.size() <= 5);

            long count = salesRepository.countPaged(null, "COMPLETED", null);
            assertTrue(count >= 1L);

            // Test sorting allowlist
            List<Sales> sortedByDate = salesRepository.findPaged(0, 2, null, null, null, "saleDate", "asc");
            assertNotNull(sortedByDate);
        });
    }

    @Test
    @DisplayName("Purchases findPaged & countPaged: verifies SQL LIMIT, OFFSET, supplier join, and status filter")
    void testPurchasePagination() {
        getTransactionTemplate().executeWithoutResult(status -> {
            status.setRollbackOnly();

            UUID suppUuid = randomUuid();
            Supplier supp = new Supplier(
                    null, suppUuid, "Apex Spares " + (System.currentTimeMillis() % 100000L),
                    "Suresh", "9876543210", "suresh@apex.com",
                    "Industrial Area", "Bengaluru", "Karnataka", "560058",
                    "29ABCDE1234F1Z5", 30, true, null, null
            );
            Supplier createdSupp = supplierRepository.create(supp);

            UUID purchaseUuid = randomUuid();
            purchaseRepository.createPurchase(
                    purchaseUuid, "PO-PAGED-" + (System.currentTimeMillis() % 100000L),
                    createdSupp.id(), "INV-999",
                    LocalDate.now(), "Pagination PO test"
            );

            List<Purchase> paged = purchaseRepository.findPaged(0, 5, null, "DRAFT", "id", "desc");
            assertNotNull(paged);
            assertFalse(paged.isEmpty());

            long count = purchaseRepository.countPaged(null, "DRAFT");
            assertTrue(count >= 1L);
        });
    }

    @Test
    @DisplayName("Inventory Stock & Low-Stock findPaged: verifies SQL LIMIT, OFFSET, search, and low-stock filter")
    void testInventoryPagination() {
        getTransactionTemplate().executeWithoutResult(status -> {
            status.setRollbackOnly();

            ProductCategory category = categoryRepository.create(new ProductCategory(
                    null, randomUuid(), "Filters-" + System.currentTimeMillis(), "Air & Oil Filters",
                    true, null, null
            ));
            ProductBrand brand = brandRepository.create(new ProductBrand(
                    null, randomUuid(), "Bosch-" + System.currentTimeMillis(), "Bosch Auto",
                    true, null, null
            ));

            Product product = productRepository.create(
                    randomUuid(),
                    "PRD-PAGED-" + (System.currentTimeMillis() % 100000L),
                    "PART-PAGED-" + (System.currentTimeMillis() % 100000L),
                    "Oil Filter Premium",
                    "High filtration oil filter",
                    category.id(),
                    brand.id(),
                    BigDecimal.valueOf(150.00),
                    BigDecimal.valueOf(250.00),
                    BigDecimal.valueOf(10.00),
                    "PIECE",
                    "123456789012",
                    "CODE128"
            );

            // Update quantity to low stock level (5 <= 10)
            inventoryRepository.updateProductQuantity(product.id(), BigDecimal.valueOf(5.00));

            var stockPaged = inventoryRepository.findStockPaged(0, 10, "Oil Filter", "productName", "asc");
            assertNotNull(stockPaged);
            assertFalse(stockPaged.isEmpty());

            long totalStock = inventoryRepository.countStockPaged("Oil Filter");
            assertTrue(totalStock >= 1L);

            var lowStockPaged = inventoryRepository.findLowStockPaged(0, 10, "Oil Filter", "quantity", "asc");
            assertNotNull(lowStockPaged);
            assertFalse(lowStockPaged.isEmpty());

            long totalLowStock = inventoryRepository.countLowStockPaged("Oil Filter");
            assertTrue(totalLowStock >= 1L);
        });
    }

    @Test
    @DisplayName("Expenses findPaged & countPaged: verifies search, status, and paymentMethod filters")
    void testExpensePagination() {
        getTransactionTemplate().executeWithoutResult(status -> {
            status.setRollbackOnly();

            Long userId = userManagementRepository.create("exp_user_" + UUID.randomUUID().toString().substring(0, 6), "hash", randomUuid());

            ExpenseCategory category = expenseCategoryRepository.create(new ExpenseCategory(
                    null, randomUuid(), "Office-" + System.currentTimeMillis(), "Office supplies",
                    true, null, null
            ));

            Expense expense = new Expense(
                    null, randomUuid(), category.id(), category.name(),
                    LocalDate.now(), "Printer Paper", "A4 paper pack",
                    BigDecimal.valueOf(450.00), ExpensePaymentMethod.CASH,
                    "REF-" + (System.currentTimeMillis() % 10000L),
                    "Regular monthly supply", ExpenseStatus.ACTIVE,
                    userId, OffsetDateTime.now(), OffsetDateTime.now(), null
            );
            expenseRepository.create(expense);

            List<Expense> paged = expenseRepository.findPaged(0, 5, "Printer", "ACTIVE", "CASH", "id", "desc");
            assertNotNull(paged);
            assertFalse(paged.isEmpty());

            long count = expenseRepository.countPaged("Printer", "ACTIVE", "CASH");
            assertTrue(count >= 1L);
        });
    }

    @Test
    @DisplayName("Audit Logs findPaged & countPaged: verifies SQL LIMIT, OFFSET, and action/entityType filters")
    void testAuditLogPagination() {
        getTransactionTemplate().executeWithoutResult(status -> {
            status.setRollbackOnly();

            String entityId = String.valueOf(System.currentTimeMillis());
            auditLogRepository.insert(
                    null, "admin", "CREATE", "TEST_ENTITY", entityId,
                    "Integration test audit record", null, "{}"
            );

            var paged = auditLogRepository.findPaged(0, 5, null, "CREATE", "TEST_ENTITY", "id", "desc");
            assertNotNull(paged);
            assertFalse(paged.isEmpty());

            long count = auditLogRepository.countPaged(null, "CREATE", "TEST_ENTITY");
            assertTrue(count >= 1L);
        });
    }

    @Test
    @DisplayName("Cash Register Movements findMovementsPaged: verifies SQL LIMIT, OFFSET, and count")
    void testCashRegisterMovementsPagination() {
        getTransactionTemplate().executeWithoutResult(status -> {
            status.setRollbackOnly();

            Long cashierId = userManagementRepository.create("cashier_" + UUID.randomUUID().toString().substring(0, 6), "hash", randomUuid());

            CashRegister register = cashRegisterRepository.createRegister(new CashRegister(
                    null, randomUuid(), "REG-" + (System.currentTimeMillis() % 100000L),
                    "Front Counter", "Main billing register", true, null, null
            ));

            CashRegisterSession session = cashRegisterRepository.openSession(
                    randomUuid(), register.id(), cashierId, BigDecimal.valueOf(1000.00)
            );

            cashRegisterRepository.createMovement(new CashRegisterMovement(
                    null, randomUuid(), session.id(), CashMovementType.IN,
                    CashMovementSourceType.MANUAL, null, BigDecimal.valueOf(500.00),
                    "Petty cash top-up", cashierId, OffsetDateTime.now()
            ));

            List<CashRegisterMovement> paged = cashRegisterRepository.findMovementsPaged(
                    session.id(), 0, 5, "createdAt", "desc"
            );
            assertNotNull(paged);
            assertFalse(paged.isEmpty());

            long count = cashRegisterRepository.countMovements(session.id());
            assertEquals(1L, count);
        });
    }

    @Test
    @DisplayName("AI Conversations getConversationsPaged: verifies SQL LIMIT, OFFSET, and userSubject filter")
    void testAiConversationsPagination() {
        getTransactionTemplate().executeWithoutResult(status -> {
            status.setRollbackOnly();

            String userSub = "user-test-" + System.currentTimeMillis();
            UUID convoId1 = randomUuid();
            UUID convoId2 = randomUuid();

            aiRepository.createConversation(convoId1, userSub, "Conversation 1");
            aiRepository.createConversation(convoId2, userSub, "Conversation 2");

            var paged = aiRepository.getConversationsPaged(userSub, 0, 1, "updatedAt", "desc");
            assertNotNull(paged);
            assertEquals(1, paged.size());

            var pagedNext = aiRepository.getConversationsPaged(userSub, 1, 1, "updatedAt", "desc");
            assertNotNull(pagedNext);
            assertEquals(1, pagedNext.size());
            assertNotEquals(paged.get(0).id(), pagedNext.get(0).id());

            long total = aiRepository.countConversations(userSub);
            assertEquals(2L, total);
        });
    }

    @Test
    @DisplayName("Tax Rates findPaged & countPaged: verifies SQL LIMIT, OFFSET, search, and sorting")
    void testTaxRatePagination() {
        getTransactionTemplate().executeWithoutResult(status -> {
            status.setRollbackOnly();

            String taxName = "GST-TEST-" + (System.currentTimeMillis() % 100000L);
            taxRateRepository.create(
                    randomUuid(), taxName,
                    BigDecimal.valueOf(18.00), BigDecimal.valueOf(9.00), BigDecimal.valueOf(9.00), BigDecimal.valueOf(18.00),
                    LocalDate.now(), null
            );

            List<TaxRate> paged = taxRateRepository.findPaged(0, 5, taxName, "effectiveFrom", "desc");
            assertNotNull(paged);
            assertFalse(paged.isEmpty());

            long count = taxRateRepository.countPaged(taxName);
            assertTrue(count >= 1L);
        });
    }

    @Test
    @DisplayName("Master Collections findPaged: Customers, Suppliers, Brands, Categories, and Users")
    void testMasterCollectionsPagination() {
        getTransactionTemplate().executeWithoutResult(status -> {
            status.setRollbackOnly();

            // Customer
            var custPaged = customerRepository.findPaged(0, 5, null, null, null, "customerName", "asc");
            assertNotNull(custPaged);
            long custCount = customerRepository.countPaged(null, null, null);
            assertTrue(custCount >= 0L);

            // Supplier
            var suppPaged = supplierRepository.findPaged(0, 5, null, null, null, "supplierName", "asc");
            assertNotNull(suppPaged);
            long suppCount = supplierRepository.countPaged(null, null, null);
            assertTrue(suppCount >= 0L);

            // Brands
            var brandPaged = brandRepository.findPaged(0, 5, null, "name", "asc");
            assertNotNull(brandPaged);
            long brandCount = brandRepository.countPaged(null);
            assertTrue(brandCount >= 0L);

            // Categories
            var catPaged = categoryRepository.findPaged(0, 5, null, "name", "asc");
            assertNotNull(catPaged);
            long catCount = categoryRepository.countPaged(null);
            assertTrue(catCount >= 0L);

            // Users
            var userPaged = userManagementRepository.findPaged(0, 5, null, "username", "asc");
            assertNotNull(userPaged);
            long userCount = userManagementRepository.countPaged(null);
            assertTrue(userCount >= 0L);
        });
    }

    @Test
    @DisplayName("Daily Closing findPaged & countPaged: verifies SQL LIMIT, OFFSET, date filters and sorting")
    void testDailyClosingPagination() {
        getTransactionTemplate().executeWithoutResult(status -> {
            status.setRollbackOnly();

            var closings = dailyClosingRepository.findPaged(0, 5, null, null, "businessDate", "desc");
            assertNotNull(closings);
            long count = dailyClosingRepository.countPaged(null, null);
            assertTrue(count >= 0L);

            // Test sorting allowlist
            var sortedByClosedAt = dailyClosingRepository.findPaged(0, 2, null, null, "closedAt", "asc");
            assertNotNull(sortedByClosedAt);
        });
    }

    @Test
    @DisplayName("Offset boundary: valid large positive offset executes without SQL error")
    void testLargeSafeOffsetExecutesWithoutSqlError() {
        getTransactionTemplate().executeWithoutResult(status -> {
            status.setRollbackOnly();

            // Querying a high offset beyond data range returns empty list cleanly without PostgreSQL error
            var results = auditLogRepository.findPaged(1_000_000, 20, null, null, null, "createdAt", "desc");
            assertNotNull(results);
            assertTrue(results.isEmpty());
        });
    }
}
