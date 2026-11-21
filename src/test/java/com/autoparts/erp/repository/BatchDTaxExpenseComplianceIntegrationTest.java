package com.autoparts.erp.repository;

import com.autoparts.erp.audit.model.AuditLog;
import com.autoparts.erp.audit.repositoryimpl.AuditLogRepositoryImpl;
import com.autoparts.erp.auth.repositoryimpl.UserManagementRepositoryImpl;
import com.autoparts.erp.dailyclosing.dto.DailyClosingResponse;
import com.autoparts.erp.dailyclosing.repositoryimpl.DailyClosingRepositoryImpl;
import com.autoparts.erp.expense.category.model.ExpenseCategory;
import com.autoparts.erp.expense.category.repositoryimpl.ExpenseCategoryRepositoryImpl;
import com.autoparts.erp.expense.ledger.dto.ExpenseLedgerSummary;
import com.autoparts.erp.expense.ledger.repositoryimpl.ExpenseLedgerRepositoryImpl;
import com.autoparts.erp.expense.management.model.Expense;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;
import com.autoparts.erp.expense.management.model.ExpenseStatus;
import com.autoparts.erp.expense.management.repositoryimpl.ExpenseRepositoryImpl;
import com.autoparts.erp.masters.brand.model.ProductBrand;
import com.autoparts.erp.masters.brand.repositoryimpl.ProductBrandRepositoryImpl;
import com.autoparts.erp.masters.category.model.ProductCategory;
import com.autoparts.erp.masters.category.repositoryimpl.ProductCategoryRepositoryImpl;
import com.autoparts.erp.product.model.Product;
import com.autoparts.erp.product.repositoryimpl.ProductRepositoryImpl;
import com.autoparts.erp.tax.model.ProductTaxMapping;
import com.autoparts.erp.tax.model.TaxRate;
import com.autoparts.erp.tax.repositoryimpl.ProductTaxMappingRepositoryImpl;
import com.autoparts.erp.tax.repositoryimpl.TaxRateRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Batch D — Tax, Expense and Compliance PostgreSQL Integration Tests")
class BatchDTaxExpenseComplianceIntegrationTest extends BaseRepositoryIntegrationTest {

    private TaxRateRepositoryImpl taxRateRepository;
    private ProductTaxMappingRepositoryImpl taxMappingRepository;
    private ExpenseCategoryRepositoryImpl expenseCategoryRepository;
    private ExpenseRepositoryImpl expenseRepository;
    private ExpenseLedgerRepositoryImpl expenseLedgerRepository;
    private DailyClosingRepositoryImpl dailyClosingRepository;
    private AuditLogRepositoryImpl auditLogRepository;

    private ProductRepositoryImpl productRepository;
    private ProductBrandRepositoryImpl brandRepository;
    private ProductCategoryRepositoryImpl categoryRepository;
    private UserManagementRepositoryImpl userManagementRepository;

    @BeforeEach
    void setUp() {
        taxRateRepository = new TaxRateRepositoryImpl(getJdbcTemplate());
        taxMappingRepository = new ProductTaxMappingRepositoryImpl(getJdbcTemplate());
        expenseCategoryRepository = new ExpenseCategoryRepositoryImpl(getJdbcTemplate());
        expenseRepository = new ExpenseRepositoryImpl(getJdbcTemplate());
        expenseLedgerRepository = new ExpenseLedgerRepositoryImpl(getJdbcTemplate());
        dailyClosingRepository = new DailyClosingRepositoryImpl(getJdbcTemplate());
        auditLogRepository = new AuditLogRepositoryImpl(getJdbcTemplate());

        productRepository = new ProductRepositoryImpl(getJdbcTemplate());
        brandRepository = new ProductBrandRepositoryImpl(getJdbcTemplate());
        categoryRepository = new ProductCategoryRepositoryImpl(getJdbcTemplate());
        userManagementRepository = new UserManagementRepositoryImpl(getJdbcTemplate());
    }

    @Test
    @DisplayName("TaxRate and ProductTaxMapping: effective dates, overlapping protection, and advisory lock")
    void testTaxConfigurationAndMapping() throws Exception {
        getTransactionTemplate().executeWithoutResult(status -> {
            // Advisory lock test
            taxRateRepository.lockTaxRateConfiguration();

            UUID taxUuid = randomUuid();
            String taxName = "GST 18% " + UUID.randomUUID().toString().substring(0, 6);
            LocalDate effFrom = LocalDate.of(2026, 1, 1);
            LocalDate effTo = LocalDate.of(2026, 12, 31);

            TaxRate rate = taxRateRepository.create(
                    taxUuid, taxName,
                    new BigDecimal("18.00"), new BigDecimal("9.00"),
                    new BigDecimal("9.00"), new BigDecimal("18.00"),
                    effFrom, effTo
            );
            assertNotNull(rate.id());
            assertEquals(taxUuid, rate.uuid());
            assertEquals(taxName, rate.taxName());

            // Overlapping detection: exact dates or overlapping interval must be detected
            assertTrue(taxRateRepository.existsOverlappingTaxRate(taxName, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), null));
            assertFalse(taxRateRepository.existsOverlappingTaxRate(taxName, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31), taxUuid),
                    "Excluding own UUID should not detect overlap with itself");

            // Product Tax Mapping
            ProductBrand brand = brandRepository.create(new ProductBrand(null, randomUuid(), "TaxBrand", "Desc", true, null, null));
            ProductCategory cat = categoryRepository.create(new ProductCategory(null, randomUuid(), "TaxCat", "Desc", true, null, null));
            Product product = productRepository.create(
                    randomUuid(), "PRD-TAX-1", "PART-TAX-1", "Taxed Battery", "Desc",
                    cat.id(), brand.id(),
                    new BigDecimal("3000.00"), new BigDecimal("4500.00"),
                    new BigDecimal("2.0"), "PCS", "BAR-TAX-1", "CODE128"
            );

            UUID mapUuid = randomUuid();
            ProductTaxMapping mapping = taxMappingRepository.create(
                    mapUuid, product.id(), rate.id(), "8507", effFrom, effTo
            );
            assertNotNull(mapping.id());
            assertEquals(mapUuid, mapping.uuid());

            Optional<ProductTaxMapping> found = taxMappingRepository.findMappingByProductUuidAndDate(
                    product.uuid(), LocalDate.of(2026, 6, 15)
            );
            assertTrue(found.isPresent());
            assertEquals("8507", found.get().hsnCode());

            status.setRollbackOnly();
        });

        // Concurrent advisory lock test across two independent connections
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLockLatch = new CountDownLatch(1);
        AtomicBoolean secondConnectionHeldOff = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> task1 = executor.submit(() -> {
            try (Connection conn1 = openIndependentConnection()) {
                conn1.setAutoCommit(false);
                try (PreparedStatement ps = conn1.prepareStatement("SELECT pg_advisory_xact_lock(824611)")) {
                    ps.execute();
                }
                lockAcquiredLatch.countDown();
                releaseLockLatch.await(5, TimeUnit.SECONDS);
                conn1.commit();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> task2 = executor.submit(() -> {
            try {
                lockAcquiredLatch.await(3, TimeUnit.SECONDS);
                long start = System.currentTimeMillis();
                try (Connection conn2 = openIndependentConnection()) {
                    conn2.setAutoCommit(false);
                    try (PreparedStatement ps = conn2.prepareStatement("SELECT pg_advisory_xact_lock(824611)")) {
                        ps.execute();
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed >= 350) {
                        secondConnectionHeldOff.set(true);
                    }
                    conn2.commit();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread.sleep(400);
        releaseLockLatch.countDown();

        task1.get(5, TimeUnit.SECONDS);
        task2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(secondConnectionHeldOff.get(), "Second connection must block until first releases advisory lock");
    }

    @Test
    @DisplayName("Expense and ExpenseLedger: category, creation, ledger summary and cancellation")
    void testExpenseLifecycleAndLedger() {
        getTransactionTemplate().executeWithoutResult(status -> {
            Long userId = userManagementRepository.create("exp_user_" + UUID.randomUUID().toString().substring(0, 6), "hash", randomUuid());

            UUID catUuid = randomUuid();
            String catName = "Utilities " + UUID.randomUUID().toString().substring(0, 6);
            ExpenseCategory cat = expenseCategoryRepository.create(new ExpenseCategory(null, catUuid, catName, "Electricity and water", true, null, null));
            assertNotNull(cat.id());
            assertTrue(expenseCategoryRepository.existsByName(catName));

            UUID expUuid = randomUuid();
            Expense expense = new Expense(
                    null, expUuid, cat.id(), catName, LocalDate.now(), "Electric Bill Nov",
                    "Monthly bill", new BigDecimal("4500.00"), ExpensePaymentMethod.BANK,
                    "TXN-ELEC-01", "Paid online", ExpenseStatus.ACTIVE, userId, null, null, null
            );

            Expense created = expenseRepository.create(expense);
            assertNotNull(created.id());
            assertEquals(expUuid, created.uuid());
            assertEquals(ExpenseStatus.ACTIVE, created.status());

            // Check Expense Ledger
            ExpenseLedgerSummary summary = expenseLedgerRepository.getSummary(
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1),
                    catUuid, null, null
            );
            assertNotNull(summary);

            // Cancel expense
            boolean cancelled = expenseRepository.cancel(expUuid);
            assertTrue(cancelled);

            Expense cancelledExp = expenseRepository.findByUuid(expUuid).orElseThrow();
            assertEquals(ExpenseStatus.CANCELLED, cancelledExp.status());

            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("DailyClosing and AuditLog: closing creation, session checks, and audit log persistence")
    void testDailyClosingAndAuditLog() {
        getTransactionTemplate().executeWithoutResult(status -> {
            Long userId = userManagementRepository.create("audit_user_" + UUID.randomUUID().toString().substring(0, 6), "hash", randomUuid());

            // Audit log insert & query
            auditLogRepository.insert(
                    userId, "audit_user", "UPDATE", "Product", "101",
                    "Price increased from 100 to 120", "{\"price\": 100}", "{\"price\": 120}"
            );

            List<AuditLog> recentLogs = auditLogRepository.findRecent(10, 0);
            assertTrue(recentLogs.stream().anyMatch(l -> "audit_user".equals(l.username())),
                    "Inserted audit log must be retrievable");

            // Daily closing
            LocalDate closingDate = LocalDate.now().minusDays(365); // Safe past date avoiding clash with live closing
            OffsetDateTime start = closingDate.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime end = closingDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

            assertFalse(dailyClosingRepository.hasOpenSessions(start, end));

            DailyClosingResponse closing = dailyClosingRepository.createClosing(
                    closingDate, start, end, userId, "End of day successful"
            );
            assertNotNull(closing);
            assertEquals(closingDate, closing.businessDate());

            assertTrue(dailyClosingRepository.existsByBusinessDate(closingDate));
            assertTrue(dailyClosingRepository.findByBusinessDate(closingDate).isPresent());

            status.setRollbackOnly();
        });
    }
}
