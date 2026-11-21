package com.autoparts.erp.repository;

import com.autoparts.erp.auth.repositoryimpl.UserManagementRepositoryImpl;
import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.model.CashRegister;
import com.autoparts.erp.cashregister.model.CashRegisterMovement;
import com.autoparts.erp.cashregister.model.CashRegisterSession;
import com.autoparts.erp.cashregister.repositoryimpl.CashRegisterRepositoryImpl;
import com.autoparts.erp.customer.ledger.dto.CustomerLedgerSummary;
import com.autoparts.erp.customer.ledger.repository.CustomerLedgerRepository;
import com.autoparts.erp.customer.ledger.repositoryimpl.CustomerLedgerRepositoryImpl;
import com.autoparts.erp.customer.model.Customer;
import com.autoparts.erp.customer.repositoryimpl.CustomerRepositoryImpl;
import com.autoparts.erp.masters.brand.model.ProductBrand;
import com.autoparts.erp.masters.brand.repositoryimpl.ProductBrandRepositoryImpl;
import com.autoparts.erp.masters.category.model.ProductCategory;
import com.autoparts.erp.masters.category.repositoryimpl.ProductCategoryRepositoryImpl;
import com.autoparts.erp.product.model.Product;
import com.autoparts.erp.product.repositoryimpl.ProductRepositoryImpl;
import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.ledger.repositoryimpl.SalesLedgerRepositoryImpl;
import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.model.SalesPayment;
import com.autoparts.erp.sales.model.SalesProduct;
import com.autoparts.erp.sales.model.SalesReturn;
import com.autoparts.erp.sales.model.SalesReturnProduct;
import com.autoparts.erp.sales.repositoryimpl.SalesRepositoryImpl;
import com.autoparts.erp.sales.repositoryimpl.SalesReturnRepositoryImpl;
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

@DisplayName("Batch C — Sales, Customer, and Cash Register PostgreSQL Integration Tests")
class BatchCSalesCustomerIntegrationTest extends BaseRepositoryIntegrationTest {

    private SalesRepositoryImpl salesRepository;
    private SalesReturnRepositoryImpl salesReturnRepository;
    private SalesLedgerRepositoryImpl salesLedgerRepository;
    private CustomerRepositoryImpl customerRepository;
    private CustomerLedgerRepositoryImpl customerLedgerRepository;
    private CashRegisterRepositoryImpl cashRegisterRepository;

    private ProductRepositoryImpl productRepository;
    private ProductBrandRepositoryImpl brandRepository;
    private ProductCategoryRepositoryImpl categoryRepository;
    private UserManagementRepositoryImpl userManagementRepository;

    @BeforeEach
    void setUp() {
        salesRepository = new SalesRepositoryImpl(getJdbcTemplate());
        salesReturnRepository = new SalesReturnRepositoryImpl(getJdbcTemplate());
        salesLedgerRepository = new SalesLedgerRepositoryImpl(getJdbcTemplate());
        customerRepository = new CustomerRepositoryImpl(getJdbcTemplate());
        customerLedgerRepository = new CustomerLedgerRepositoryImpl(getJdbcTemplate());
        cashRegisterRepository = new CashRegisterRepositoryImpl(getJdbcTemplate());

        productRepository = new ProductRepositoryImpl(getJdbcTemplate());
        brandRepository = new ProductBrandRepositoryImpl(getJdbcTemplate());
        categoryRepository = new ProductCategoryRepositoryImpl(getJdbcTemplate());
        userManagementRepository = new UserManagementRepositoryImpl(getJdbcTemplate());
    }

    @Test
    @DisplayName("Customer and Ledger: customer creation, opening balance and summary calculation")
    void testCustomerAndCustomerLedger() {
        getTransactionTemplate().executeWithoutResult(status -> {
            UUID custUuid = randomUuid();
            String code = "CUST-" + (System.currentTimeMillis() % 100000L);
            String phone = "97" + (System.currentTimeMillis() % 100000000L);
            Customer cust = new Customer(
                    null, custUuid, code, "Apex Garage", phone, "suresh@apex.com",
                    "45 Garage St", "Bengaluru", "Karnataka", "560002",
                    "29FFFFF0000F1Z5", true, null, null
            );
            Customer created = customerRepository.create(cust);
            assertNotNull(created.id());
            assertEquals(custUuid, created.uuid());

            CustomerLedgerRepository.CustomerInfo info = customerLedgerRepository.findCustomerByUuid(custUuid).orElseThrow();
            assertEquals("Apex Garage", info.customerName());

            BigDecimal opening = customerLedgerRepository.getOpeningBalance(created.id(), LocalDate.now());
            assertNotNull(opening);

            CustomerLedgerSummary summary = customerLedgerRepository.getSummary(
                    info, LocalDate.now().minusDays(30), LocalDate.now(), opening
            );
            assertNotNull(summary);

            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("Sales flow: invoice generation, item insertion, payments, and rollback on failure")
    void testSalesLifecycleAndRollback() {
        // Rollback test: Failed sale transaction midway
        UUID failUuid = randomUuid();
        try {
            getTransactionTemplate().executeWithoutResult(status -> {
                String invoiceNo = salesRepository.generateInvoiceNumber(LocalDate.now());
                Sales failedSale = new Sales(
                        null, failUuid, invoiceNo, null, "Walk-in Guest", "9000000000",
                        LocalDate.now(), new BigDecimal("1000.00"), BigDecimal.ZERO,
                        new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, new BigDecimal("1000.00"), BigDecimal.ZERO,
                        new BigDecimal("1000.00"), SalesStatus.DRAFT, SalesPaymentStatus.UNPAID,
                        "Rollback test", null, null
                );
                salesRepository.createSale(failedSale);
                throw new IllegalStateException("Simulated sale mid-flight failure");
            });
        } catch (IllegalStateException expected) {
            // expected
        }

        assertTrue(salesRepository.findByUuid(failUuid).isEmpty(), "Rolled back sale must not exist in DB");

        // Successful sale flow
        getTransactionTemplate().executeWithoutResult(status -> {
            ProductBrand brand = brandRepository.create(new ProductBrand(null, randomUuid(), "SalesBrand", "Desc", true, null, null));
            ProductCategory cat = categoryRepository.create(new ProductCategory(null, randomUuid(), "SalesCat", "Desc", true, null, null));
            Product product = productRepository.create(
                    randomUuid(), "PRD-SALE-1", "PART-SALE-1", "Wiper Blade", "Desc",
                    cat.id(), brand.id(),
                    new BigDecimal("200.00"), new BigDecimal("350.00"),
                    new BigDecimal("5.0"), "PAIR", "BAR-SALE-1", "CODE128"
            );

            UUID saleUuid = randomUuid();
            String invoiceNo = salesRepository.generateInvoiceNumber(LocalDate.now());
            assertNotNull(invoiceNo);

            Sales sale = new Sales(
                    null, saleUuid, invoiceNo, null, "Priya Sharma", "9800000001",
                    LocalDate.now(), new BigDecimal("700.00"), BigDecimal.ZERO,
                    new BigDecimal("700.00"), new BigDecimal("63.00"), new BigDecimal("63.00"),
                    BigDecimal.ZERO, new BigDecimal("126.00"), new BigDecimal("826.00"),
                    new BigDecimal("826.00"), BigDecimal.ZERO, SalesStatus.COMPLETED,
                    SalesPaymentStatus.PAID, "Counter sale", null, null
            );

            Sales createdSale = salesRepository.createSale(sale);
            assertNotNull(createdSale.id());
            assertEquals(saleUuid, createdSale.uuid());

            // Add line product
            SalesProduct line = new SalesProduct(
                    null, randomUuid(), createdSale.id(), product.id(), product.uuid(),
                    product.productCode(), product.name(), new BigDecimal("2.000"), "PAIR",
                    new BigDecimal("350.00"), BigDecimal.ZERO, new BigDecimal("700.00"),
                    "HSN-SALE", new BigDecimal("18.00"), new BigDecimal("9.00"),
                    new BigDecimal("9.00"), BigDecimal.ZERO, new BigDecimal("63.00"),
                    new BigDecimal("63.00"), BigDecimal.ZERO, new BigDecimal("126.00"),
                    new BigDecimal("826.00"), null
            );
            salesRepository.createSaleProduct(line);

            // Add payment
            SalesPayment payment = new SalesPayment(
                    null, randomUuid(), createdSale.id(), PaymentMethod.CASH,
                    new BigDecimal("826.00"), "CASH-PAID", OffsetDateTime.now(), "Full payment", null
            );
            salesRepository.createSalePayment(payment);

            Sales retrieved = salesRepository.findByUuid(saleUuid).orElseThrow();
            assertEquals(new BigDecimal("826.00"), retrieved.grandTotal());
            assertEquals(SalesPaymentStatus.PAID, retrieved.paymentStatus());

            List<SalesProduct> products = salesRepository.findProductsBySaleId(retrieved.id());
            assertEquals(1, products.size());

            List<SalesPayment> payments = salesRepository.findPaymentsBySaleId(retrieved.id());
            assertEquals(1, payments.size());

            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("CashRegisterRepository: register lifecycle, opening session, movements and closing balance")
    void testCashRegisterSessionAndMovements() {
        getTransactionTemplate().executeWithoutResult(status -> {
            Long userId = userManagementRepository.create("cashier_" + UUID.randomUUID().toString().substring(0, 6), "hash", randomUuid());

            UUID regUuid = randomUuid();
            CashRegister register = new CashRegister(null, regUuid, "REG-01", "Counter 1", "Main counter", true, null, null);
            CashRegister createdReg = cashRegisterRepository.createRegister(register);
            assertNotNull(createdReg.id());

            // Open session with 1000.00 opening balance
            UUID sessionUuid = randomUuid();
            CashRegisterSession session = cashRegisterRepository.openSession(
                    sessionUuid, createdReg.id(), userId, new BigDecimal("1000.00")
            );
            assertNotNull(session.id());
            assertEquals(new BigDecimal("1000.00"), session.openingBalance());

            // Record an IN movement of 500.00
            UUID movUuid = randomUuid();
            CashRegisterMovement movement = new CashRegisterMovement(
                    null, movUuid, session.id(), CashMovementType.IN,
                    CashMovementSourceType.MANUAL, randomUuid(), new BigDecimal("500.00"),
                    "Petty cash deposit", userId, OffsetDateTime.now()
            );
            cashRegisterRepository.createMovement(movement);

            List<CashRegisterMovement> movements = cashRegisterRepository.findMovements(session.id());
            assertEquals(1, movements.size());

            // Close session with expected 1500.00, actual 1500.00, difference 0.00
            boolean closed = cashRegisterRepository.closeSession(
                    session.id(), userId, new BigDecimal("1500.00"),
                    new BigDecimal("1500.00"), BigDecimal.ZERO
            );
            assertTrue(closed);

            CashRegisterSession closedSession = cashRegisterRepository.findSessionByUuid(sessionUuid).orElseThrow();
            assertNotNull(closedSession.closedAt());
            assertEquals(new BigDecimal("1500.00"), closedSession.actualClosingBalance());

            status.setRollbackOnly();
        });
    }
}
