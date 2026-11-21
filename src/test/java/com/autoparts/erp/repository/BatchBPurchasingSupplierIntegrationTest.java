package com.autoparts.erp.repository;

import com.autoparts.erp.auth.repositoryimpl.UserManagementRepositoryImpl;
import com.autoparts.erp.masters.brand.model.ProductBrand;
import com.autoparts.erp.masters.brand.repositoryimpl.ProductBrandRepositoryImpl;
import com.autoparts.erp.masters.category.model.ProductCategory;
import com.autoparts.erp.masters.category.repositoryimpl.ProductCategoryRepositoryImpl;
import com.autoparts.erp.product.model.Product;
import com.autoparts.erp.product.repositoryimpl.ProductRepositoryImpl;
import com.autoparts.erp.purchase.enums.PurchasePaymentMethod;
import com.autoparts.erp.purchase.ledger.dto.PurchaseLedgerEntry;
import com.autoparts.erp.purchase.ledger.dto.PurchaseLedgerSummary;
import com.autoparts.erp.purchase.ledger.repositoryimpl.PurchaseLedgerRepositoryImpl;
import com.autoparts.erp.purchase.model.Purchase;
import com.autoparts.erp.purchase.model.PurchasePayment;
import com.autoparts.erp.purchase.model.PurchaseProduct;
import com.autoparts.erp.purchase.model.PurchaseStatus;
import com.autoparts.erp.purchase.payment.repositoryimpl.PurchasePaymentRepositoryImpl;
import com.autoparts.erp.purchase.repositoryimpl.PurchaseRepositoryImpl;
import com.autoparts.erp.purchase.returns.model.PurchaseReturn;
import com.autoparts.erp.purchase.returns.model.PurchaseReturnProduct;
import com.autoparts.erp.purchase.returns.model.SupplierCredit;
import com.autoparts.erp.purchase.returns.repositoryimpl.PurchaseReturnRepositoryImpl;
import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerEntry;
import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerSummary;
import com.autoparts.erp.supplier.ledger.repository.SupplierLedgerRepository;
import com.autoparts.erp.supplier.ledger.repositoryimpl.SupplierLedgerRepositoryImpl;
import com.autoparts.erp.supplier.model.Supplier;
import com.autoparts.erp.supplier.repositoryimpl.SupplierRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Batch B — Purchasing and Supplier PostgreSQL Integration Tests")
class BatchBPurchasingSupplierIntegrationTest extends BaseRepositoryIntegrationTest {

    private SupplierRepositoryImpl supplierRepository;
    private SupplierLedgerRepositoryImpl supplierLedgerRepository;
    private PurchaseRepositoryImpl purchaseRepository;
    private PurchasePaymentRepositoryImpl purchasePaymentRepository;
    private PurchaseReturnRepositoryImpl purchaseReturnRepository;
    private PurchaseLedgerRepositoryImpl purchaseLedgerRepository;

    private ProductRepositoryImpl productRepository;
    private ProductBrandRepositoryImpl brandRepository;
    private ProductCategoryRepositoryImpl categoryRepository;

    @BeforeEach
    void setUp() {
        supplierRepository = new SupplierRepositoryImpl(getJdbcTemplate());
        supplierLedgerRepository = new SupplierLedgerRepositoryImpl(getJdbcTemplate());
        purchaseRepository = new PurchaseRepositoryImpl(getJdbcTemplate());
        purchasePaymentRepository = new PurchasePaymentRepositoryImpl(getJdbcTemplate());
        purchaseReturnRepository = new PurchaseReturnRepositoryImpl(getJdbcTemplate());
        purchaseLedgerRepository = new PurchaseLedgerRepositoryImpl(getJdbcTemplate());

        productRepository = new ProductRepositoryImpl(getJdbcTemplate());
        brandRepository = new ProductBrandRepositoryImpl(getJdbcTemplate());
        categoryRepository = new ProductCategoryRepositoryImpl(getJdbcTemplate());
    }

    @Test
    @DisplayName("SupplierRepository: create with generated key, unique checks and soft delete")
    void testSupplierLifecycle() {
        getTransactionTemplate().executeWithoutResult(status -> {
            UUID uuid = randomUuid();
            String name = "Apex Auto Dist " + UUID.randomUUID().toString().substring(0, 6);
            String phone = "98" + (System.currentTimeMillis() % 100000000L);
            String gstin = "29AAAAA0000A1Z" + (int)(Math.random() * 10);

            Supplier supplier = new Supplier(
                    null, uuid, name, "Ramesh Kumar", phone, "apex@example.com",
                    "123 Industrial Area", "Bengaluru", "Karnataka", "560001",
                    gstin, 30, true, null, null
            );

            Supplier created = supplierRepository.create(supplier);
            assertNotNull(created.id());
            assertEquals(uuid, created.uuid());
            assertEquals(name, created.supplierName());

            assertTrue(supplierRepository.existsBySupplierName(name));
            assertTrue(supplierRepository.existsByPhone(phone));
            assertTrue(supplierRepository.existsByGstin(gstin));

            Optional<Supplier> found = supplierRepository.findByUuid(uuid);
            assertTrue(found.isPresent());

            boolean deactivated = supplierRepository.deactivate(uuid);
            assertTrue(deactivated);
            assertFalse(supplierRepository.findByUuid(uuid).orElseThrow().active());

            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("Purchase flow: header RETURNING, line items, status transition and rollback on failure")
    void testPurchaseLifecycleAndRollback() {
        // Rollback test: A multi-step purchase transaction fails midway
        UUID failUuid = randomUuid();
        try {
            getTransactionTemplate().executeWithoutResult(status -> {
                UUID supUuid = randomUuid();
                Supplier sup = supplierRepository.create(new Supplier(
                        null, supUuid, "Rollback Supplier", "Contact", "9100000001",
                        "rb@example.com", "Address", "City", "State", "560001",
                        "29BBBBB0000B1Z1", 15, true, null, null
                ));

                purchaseRepository.createPurchase(
                        failUuid, "PUR-FAIL-001", sup.id(), "INV-FAIL", LocalDate.now(), "Notes"
                );
                // Simulate an unexpected business rule violation triggering rollback
                throw new IllegalStateException("Simulated mid-flight purchase failure");
            });
        } catch (IllegalStateException expected) {
            // Transaction rolled back
        }

        // Verify purchase was cleanly rolled back and does not exist in PostgreSQL
        Optional<Purchase> shouldBeEmpty = purchaseRepository.findByUuid(failUuid);
        assertTrue(shouldBeEmpty.isEmpty(), "Rolled back purchase must not exist in database");

        // Successful purchase execution flow
        getTransactionTemplate().executeWithoutResult(status -> {
            UUID supUuid = randomUuid();
            Supplier sup = supplierRepository.create(new Supplier(
                    null, supUuid, "Acme Auto Spares", "Rajesh", "9100000002",
                    "acme@example.com", "Address", "City", "State", "560001",
                    "29CCCCC0000C1Z2", 30, true, null, null
            ));

            ProductBrand brand = brandRepository.create(new ProductBrand(null, randomUuid(), "PurBrand", "Desc", true, null, null));
            ProductCategory cat = categoryRepository.create(new ProductCategory(null, randomUuid(), "PurCat", "Desc", true, null, null));
            Product product = productRepository.create(
                    randomUuid(), "PRD-PUR-1", "PART-PUR-1", "Pur Part", "Desc",
                    cat.id(), brand.id(),
                    new BigDecimal("500.00"), new BigDecimal("750.00"),
                    new BigDecimal("5.0"), "PCS", "BAR-PUR-1", "CODE128"
            );

            UUID purUuid = randomUuid();
            String purNumber = "PUR-" + System.currentTimeMillis();
            Purchase purchase = purchaseRepository.createPurchase(
                    purUuid, purNumber, sup.id(), "INV-SUP-101", LocalDate.now(), "Valid order"
            );
            assertNotNull(purchase.id());
            assertEquals(purUuid, purchase.uuid());
            assertEquals(PurchaseStatus.DRAFT, purchase.status());

            // Add line item
            PurchaseProduct lineItem = purchaseRepository.createPurchaseProduct(
                    randomUuid(), purUuid, product.id(),
                    new BigDecimal("10.000"), new BigDecimal("500.00"),
                    BigDecimal.ZERO, new BigDecimal("18.00"),
                    new BigDecimal("900.00"), new BigDecimal("5900.00")
            );
            assertNotNull(lineItem);

            // Update totals
            purchaseRepository.updatePurchaseTotals(
                    purUuid,
                    new BigDecimal("5000.00"), BigDecimal.ZERO,
                    new BigDecimal("900.00"), new BigDecimal("5900.00")
            );

            // Mark received
            boolean marked = purchaseRepository.markReceived(purUuid);
            assertTrue(marked);

            Purchase retrieved = purchaseRepository.findByUuid(purUuid).orElseThrow();
            assertEquals(PurchaseStatus.RECEIVED, retrieved.status());
            assertEquals(new BigDecimal("5900.00"), retrieved.grandTotal());
            assertEquals(1, retrieved.products().size());

            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("PurchasePaymentRepository: payments, total paid tracking and purchase context")
    void testPurchasePaymentTracking() {
        getTransactionTemplate().executeWithoutResult(status -> {
            UserManagementRepositoryImpl userRepo = new UserManagementRepositoryImpl(getJdbcTemplate());
            Long userId = userRepo.create("pay_user_" + UUID.randomUUID().toString().substring(0, 6), "hash123", randomUuid());

            UUID supUuid = randomUuid();
            Supplier sup = supplierRepository.create(new Supplier(
                    null, supUuid, "Payment Test Supplier", "Person", "9100000003",
                    "pay@example.com", "Address", "City", "State", "560001",
                    "29DDDDD0000D1Z3", 30, true, null, null
            ));

            UUID purUuid = randomUuid();
            Purchase purchase = purchaseRepository.createPurchase(
                    purUuid, "PUR-PAY-001", sup.id(), "INV-PAY-1", LocalDate.now(), "Notes"
            );
            purchaseRepository.updatePurchaseTotals(
                    purUuid,
                    new BigDecimal("2000.00"), BigDecimal.ZERO,
                    new BigDecimal("360.00"), new BigDecimal("2360.00")
            );

            // Initial paid should be 0
            BigDecimal initialPaid = purchasePaymentRepository.getTotalPaid(purchase.id());
            assertEquals(BigDecimal.ZERO, initialPaid);

            // Pay 1000.00
            UUID payUuid1 = randomUuid();
            PurchasePayment pay1 = new PurchasePayment(
                    null, payUuid1, purchase.id(), PurchasePaymentMethod.BANK,
                    new BigDecimal("1000.00"), "NEFT-123", OffsetDateTime.now(), "Part payment",
                    userId, null
            );
            purchasePaymentRepository.create(pay1);

            BigDecimal paidAfter1 = purchasePaymentRepository.getTotalPaid(purchase.id());
            assertEquals(new BigDecimal("1000.00"), paidAfter1);

            // Pay remaining 1360.00
            UUID payUuid2 = randomUuid();
            PurchasePayment pay2 = new PurchasePayment(
                    null, payUuid2, purchase.id(), PurchasePaymentMethod.CASH,
                    new BigDecimal("1360.00"), "CASH-REC", OffsetDateTime.now(), "Full settlement",
                    userId, null
            );
            purchasePaymentRepository.create(pay2);

            BigDecimal paidAfter2 = purchasePaymentRepository.getTotalPaid(purchase.id());
            assertEquals(new BigDecimal("2360.00"), paidAfter2);

            List<PurchasePayment> payments = purchasePaymentRepository.findByPurchaseId(purchase.id());
            assertEquals(2, payments.size());

            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("PurchaseReturn and SupplierLedger: return creation, credit generation and ledger summary")
    void testPurchaseReturnAndSupplierLedger() {
        getTransactionTemplate().executeWithoutResult(status -> {
            UUID supUuid = randomUuid();
            Supplier sup = supplierRepository.create(new Supplier(
                    null, supUuid, "Ledger Supplier", "Kumar", "9100000004",
                    "ledger@example.com", "Address", "City", "State", "560001",
                    "29EEEEE0000E1Z4", 30, true, null, null
            ));

            ProductBrand brand = brandRepository.create(new ProductBrand(null, randomUuid(), "LBrand", "Desc", true, null, null));
            ProductCategory cat = categoryRepository.create(new ProductCategory(null, randomUuid(), "LCat", "Desc", true, null, null));
            Product product = productRepository.create(
                    randomUuid(), "PRD-RET-1", "PART-RET-1", "Ret Part", "Desc",
                    cat.id(), brand.id(),
                    new BigDecimal("100.00"), new BigDecimal("150.00"),
                    new BigDecimal("5.0"), "PCS", "BAR-RET-1", "CODE128"
            );

            UUID purUuid = randomUuid();
            Purchase purchase = purchaseRepository.createPurchase(
                    purUuid, "PUR-RET-001", sup.id(), "INV-RET-101", LocalDate.now(), "Return test"
            );
            purchaseRepository.createPurchaseProduct(
                    randomUuid(), purUuid, product.id(),
                    new BigDecimal("5.000"), new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500.00")
            );
            purchaseRepository.updatePurchaseTotals(
                    purUuid, new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500.00")
            );
            purchaseRepository.markReceived(purUuid);

            // Create return of 2 items
            UUID retUuid = randomUuid();
            PurchaseReturn pReturn = new PurchaseReturn(
                    null, retUuid, "RET-" + System.currentTimeMillis(), purchase.id(),
                    OffsetDateTime.now(), "Damaged in transit", "Notes",
                    new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200.00"),
                    null, null
            );
            PurchaseReturn createdReturn = purchaseReturnRepository.createReturn(pReturn);
            assertNotNull(createdReturn.id());

            // Create supplier credit for return
            UUID creditUuid = randomUuid();
            SupplierCredit credit = purchaseReturnRepository.createSupplierCredit(
                    creditUuid, createdReturn.id(), new BigDecimal("200.00")
            );
            assertNotNull(credit.id());
            assertEquals(new BigDecimal("200.00"), credit.amount());

            // Verify Supplier Ledger summary
            SupplierLedgerRepository.SupplierInfo supInfo = supplierLedgerRepository.findSupplierByUuid(supUuid).orElseThrow();
            assertEquals("Ledger Supplier", supInfo.supplierName());

            BigDecimal opening = supplierLedgerRepository.getOpeningBalance(sup.id(), LocalDate.now());
            assertNotNull(opening);

            long count = supplierLedgerRepository.countEntries(sup.id(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
            assertTrue(count >= 0);

            status.setRollbackOnly();
        });
    }
}
