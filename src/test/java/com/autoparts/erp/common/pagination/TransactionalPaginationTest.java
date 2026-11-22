package com.autoparts.erp.common.pagination;

import com.autoparts.erp.ai.dto.AiConversationResponse;
import com.autoparts.erp.ai.repository.AiRepository;
import com.autoparts.erp.ai.service.AiAssistantService;
import com.autoparts.erp.ai.service.ErpAiAgentService;
import com.autoparts.erp.audit.model.AuditLog;
import com.autoparts.erp.audit.repository.AuditLogRepository;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.audit.serviceimpl.AuditLogServiceImpl;
import com.autoparts.erp.auth.model.ManagedUser;
import com.autoparts.erp.auth.repository.UserManagementRepository;
import com.autoparts.erp.auth.repository.UserRepository;
import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.auth.service.RefreshTokenService;
import com.autoparts.erp.auth.serviceimpl.UserServiceImpl;
import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.enums.CashSessionStatus;
import com.autoparts.erp.cashregister.model.CashRegisterMovement;
import com.autoparts.erp.cashregister.model.CashRegisterSession;
import com.autoparts.erp.cashregister.repository.CashRegisterRepository;
import com.autoparts.erp.cashregister.service.CashRegisterService;
import com.autoparts.erp.cashregister.serviceimpl.CashRegisterServiceImpl;
import com.autoparts.erp.common.exception.ClientValidationException;
import com.autoparts.erp.customer.repository.CustomerRepository;
import com.autoparts.erp.dailyclosing.dto.DailyClosingResponse;
import com.autoparts.erp.dailyclosing.repository.DailyClosingRepository;
import com.autoparts.erp.dailyclosing.serviceimpl.DailyClosingServiceImpl;
import com.autoparts.erp.expense.category.model.ExpenseCategory;
import com.autoparts.erp.expense.category.repository.ExpenseCategoryRepository;
import com.autoparts.erp.expense.category.serviceimpl.ExpenseCategoryServiceImpl;
import com.autoparts.erp.expense.management.model.Expense;
import com.autoparts.erp.expense.management.model.ExpensePaymentMethod;
import com.autoparts.erp.expense.management.model.ExpenseStatus;
import com.autoparts.erp.expense.management.repository.ExpenseRepository;
import com.autoparts.erp.expense.management.serviceimpl.ExpenseServiceImpl;
import com.autoparts.erp.inventory.model.InventoryStock;
import com.autoparts.erp.inventory.repository.InventoryRepository;
import com.autoparts.erp.inventory.service.InventoryService;
import com.autoparts.erp.inventory.serviceimpl.InventoryServiceImpl;
import com.autoparts.erp.masters.brand.model.ProductBrand;
import com.autoparts.erp.masters.brand.repository.ProductBrandRepository;
import com.autoparts.erp.masters.brand.serviceimpl.ProductBrandServiceImpl;
import com.autoparts.erp.masters.category.model.ProductCategory;
import com.autoparts.erp.masters.category.repository.ProductCategoryRepository;
import com.autoparts.erp.masters.category.serviceimpl.ProductCategoryServiceImpl;
import com.autoparts.erp.purchase.model.Purchase;
import com.autoparts.erp.purchase.model.PurchaseStatus;
import com.autoparts.erp.purchase.repository.PurchaseRepository;
import com.autoparts.erp.purchase.serviceimpl.PurchaseServiceImpl;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.repository.SalesRepository;
import com.autoparts.erp.sales.serviceimpl.SalesServiceImpl;
import com.autoparts.erp.tax.model.TaxRate;
import com.autoparts.erp.tax.repository.ProductTaxMappingRepository;
import com.autoparts.erp.tax.repository.TaxRateRepository;
import com.autoparts.erp.tax.serviceimpl.TaxRateServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionalPaginationTest {

    @Mock
    private SalesRepository salesRepository;

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserManagementRepository userManagementRepository;

    @Mock
    private ProductBrandRepository productBrandRepository;

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @Mock
    private ExpenseCategoryRepository expenseCategoryRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private CashRegisterRepository cashRegisterRepository;

    @Mock
    private AiRepository aiRepository;

    @Mock
    private TaxRateRepository taxRateRepository;

    @Mock
    private DailyClosingRepository dailyClosingRepository;

    @Test
    void salesFindPagedCapsMaxPageSizeAndCalculatesOffset() {
        var service = new SalesServiceImpl(
                salesRepository,
                mock(InventoryService.class),
                mock(ProductTaxMappingRepository.class),
                mock(TaxRateRepository.class),
                mock(CustomerRepository.class),
                mock(CashRegisterService.class),
                mock(AuditLogService.class)
        );

        Sales sale = new Sales(
                1L, UUID.randomUUID(), "INV-2026-0001", 10L, "Acme Auto", "9876543210",
                LocalDate.now(), BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(100),
                BigDecimal.valueOf(9), BigDecimal.valueOf(9), BigDecimal.ZERO, BigDecimal.valueOf(18),
                BigDecimal.valueOf(118), BigDecimal.valueOf(118), BigDecimal.ZERO,
                SalesStatus.COMPLETED, SalesPaymentStatus.PAID, "Test note",
                OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(salesRepository.findPaged(eq(40), eq(20), eq("INV"), eq("COMPLETED"), eq("PAID"), eq("id"), eq("desc")))
                .thenReturn(List.of(sale));
        when(salesRepository.countPaged(eq("INV"), eq("COMPLETED"), eq("PAID"))).thenReturn(55L);
        when(salesRepository.findProductsBySaleId(1L)).thenReturn(List.of());
        when(salesRepository.findPaymentsBySaleId(1L)).thenReturn(List.of());

        var result = service.findPaged(2, 20, "INV", "COMPLETED", "PAID", "id", "desc");

        assertEquals(2, result.page());
        assertEquals(20, result.size());
        assertEquals(55L, result.totalElements());
        assertEquals(3, result.totalPages());
        assertEquals(1, result.content().size());
        assertEquals("INV-2026-0001", result.content().get(0).invoiceNumber());
    }

    @Test
    void purchaseFindPagedCapsMaxPageSizeAndNormalizesInputs() {
        var service = new PurchaseServiceImpl(
                purchaseRepository,
                mock(InventoryService.class),
                mock(AuditLogService.class)
        );

        Purchase purchase = new Purchase(
                1L, UUID.randomUUID(), "PO-2026-0001", 5L, "AutoParts Corp",
                "INV-SUP-99", LocalDate.now(), PurchaseStatus.RECEIVED,
                BigDecimal.valueOf(500), BigDecimal.ZERO, BigDecimal.valueOf(90),
                BigDecimal.valueOf(590), "Notes", List.of(),
                OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(purchaseRepository.findPaged(eq(0), eq(100), any(), any(), any(), any()))
                .thenReturn(List.of(purchase));
        when(purchaseRepository.countPaged(any(), any())).thenReturn(1L);

        var result = service.findPaged(-1, 500, null, null, "id", "desc");

        verify(purchaseRepository).findPaged(0, 100, null, null, "id", "desc");
        assertEquals(0, result.page());
        assertEquals(100, result.size());
        assertEquals(1, result.content().size());
    }

    @Test
    void inventoryStockFindPagedComputesPaginationMetadata() {
        var service = new InventoryServiceImpl(
                inventoryRepository,
                mock(AuditLogService.class)
        );

        InventoryStock stock = new InventoryStock(
                1L, UUID.randomUUID(), "PRD001", "PART-1", "Brake Pad",
                BigDecimal.valueOf(50), BigDecimal.valueOf(10), "PCS"
        );

        when(inventoryRepository.findStockPaged(eq(0), eq(20), eq("Brake"), eq("productName"), eq("asc")))
                .thenReturn(List.of(stock));
        when(inventoryRepository.countStockPaged(eq("Brake"))).thenReturn(1L);

        var result = service.findStockPaged(0, 20, "Brake", "productName", "asc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals(1, result.content().size());
        assertEquals("Brake Pad", result.content().get(0).productName());
    }

    @Test
    void inventoryLowStockFindPagedComputesPaginationMetadata() {
        var service = new InventoryServiceImpl(
                inventoryRepository,
                mock(AuditLogService.class)
        );

        InventoryStock stock = new InventoryStock(
                2L, UUID.randomUUID(), "PRD002", "PART-2", "Oil Filter",
                BigDecimal.valueOf(5), BigDecimal.valueOf(10), "PCS"
        );

        when(inventoryRepository.findLowStockPaged(eq(0), eq(20), any(), any(), any()))
                .thenReturn(List.of(stock));
        when(inventoryRepository.countLowStockPaged(any())).thenReturn(1L);

        var result = service.findLowStockPaged(0, 20, null, "quantity", "asc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals("Oil Filter", result.content().get(0).productName());
    }

    @Test
    void expenseFindPagedMapsResponseAndValidatesTotals() {
        var service = new ExpenseServiceImpl(
                expenseRepository,
                mock(ExpenseCategoryRepository.class),
                mock(CurrentUserService.class),
                mock(CashRegisterService.class),
                mock(AuditLogService.class)
        );

        Expense expense = new Expense(
                1L, UUID.randomUUID(), 10L, "Rent", LocalDate.now(),
                "Store Rent", "Monthly store rent", BigDecimal.valueOf(15000),
                ExpensePaymentMethod.BANK, "TXN12345", "Paid on time",
                ExpenseStatus.ACTIVE, 1L, OffsetDateTime.now(), OffsetDateTime.now(), null
        );

        when(expenseRepository.findPaged(eq(0), eq(20), any(), any(), any(), any(), any()))
                .thenReturn(List.of(expense));
        when(expenseRepository.countPaged(any(), any(), any())).thenReturn(1L);

        var result = service.findPaged(0, 20, null, null, null, "id", "desc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals("Store Rent", result.content().get(0).title());
    }

    @Test
    void userFindPagedMapsResponseAndComputesOffset() {
        var service = new UserServiceImpl(
                userManagementRepository,
                mock(PasswordEncoder.class),
                mock(AuditLogService.class),
                mock(CurrentUserService.class),
                mock(RefreshTokenService.class),
                mock(UserRepository.class)
        );

        ManagedUser user = new ManagedUser(
                1L, UUID.randomUUID(), "admin", true,
                Set.of("ADMIN"), Instant.now(), Instant.now()
        );

        when(userManagementRepository.findPaged(eq(0), eq(20), eq("adm"), eq("id"), eq("asc")))
                .thenReturn(List.of(user));
        when(userManagementRepository.countPaged(eq("adm"))).thenReturn(1L);

        var result = service.findPaged(0, 20, "adm", "id", "asc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals("admin", result.content().get(0).username());
    }

    @Test
    void brandFindPagedComputesOffsetAndMapsResponse() {
        var service = new ProductBrandServiceImpl(
                productBrandRepository,
                mock(AuditLogService.class)
        );

        ProductBrand brand = new ProductBrand(
                1L, UUID.randomUUID(), "Bosch", "Automotive Parts",
                true, OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(productBrandRepository.findPaged(eq(0), eq(20), any(), any(), any()))
                .thenReturn(List.of(brand));
        when(productBrandRepository.countPaged(any())).thenReturn(1L);

        var result = service.findPaged(0, 20, null, "id", "desc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals("Bosch", result.content().get(0).name());
    }

    @Test
    void categoryFindPagedComputesOffsetAndMapsResponse() {
        var service = new ProductCategoryServiceImpl(
                productCategoryRepository,
                mock(AuditLogService.class)
        );

        ProductCategory category = new ProductCategory(
                1L, UUID.randomUUID(), "Braking System", "Disc brakes, pads and rotors",
                true, OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(productCategoryRepository.findPaged(eq(0), eq(20), any(), any(), any()))
                .thenReturn(List.of(category));
        when(productCategoryRepository.countPaged(any())).thenReturn(1L);

        var result = service.findPaged(0, 20, null, "id", "desc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals("Braking System", result.content().get(0).name());
    }

    @Test
    void expenseCategoryFindPagedComputesOffsetAndMapsResponse() {
        var service = new ExpenseCategoryServiceImpl(
                expenseCategoryRepository,
                mock(AuditLogService.class)
        );

        ExpenseCategory category = new ExpenseCategory(
                1L, UUID.randomUUID(), "Utilities", "Electricity and water",
                true, OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(expenseCategoryRepository.findPaged(eq(0), eq(20), any(), any(), any()))
                .thenReturn(List.of(category));
        when(expenseCategoryRepository.countPaged(any())).thenReturn(1L);

        var result = service.findPaged(0, 20, null, "name", "asc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals("Utilities", result.content().get(0).name());
    }

    @Test
    void auditLogFindPagedComputesOffsetAndMapsResponse() {
        var service = new AuditLogServiceImpl(
                auditLogRepository,
                mock(CurrentUserService.class),
                new tools.jackson.databind.ObjectMapper()
        );

        AuditLog log = new AuditLog(
                1L, UUID.randomUUID().toString(), 1L, "admin",
                "CREATE", "PRODUCT", "100", "Created product",
                null, "{}", Instant.now()
        );

        when(auditLogRepository.findPaged(eq(0), eq(20), any(), any(), any(), any(), any()))
                .thenReturn(List.of(log));
        when(auditLogRepository.countPaged(any(), any(), any())).thenReturn(1L);

        var result = service.findPaged(0, 20, null, null, null, "id", "desc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals("CREATE", result.content().get(0).action());
    }

    @Test
    void cashMovementFindPagedComputesOffsetAndMapsResponse() {
        var service = new CashRegisterServiceImpl(
                mock(CurrentUserService.class),
                cashRegisterRepository,
                mock(AuditLogService.class)
        );

        UUID sessionUuid = UUID.randomUUID();
        CashRegisterSession session = new CashRegisterSession(
                10L, sessionUuid, 1L, "Main Counter", 1L, OffsetDateTime.now(),
                BigDecimal.ZERO, null, null, null, null, null, CashSessionStatus.OPEN
        );

        CashRegisterMovement movement = new CashRegisterMovement(
                100L, UUID.randomUUID(), 10L, CashMovementType.IN, CashMovementSourceType.MANUAL,
                null, BigDecimal.valueOf(150.00), "Float addition", 1L, OffsetDateTime.now()
        );

        when(cashRegisterRepository.findSessionByUuid(sessionUuid)).thenReturn(java.util.Optional.of(session));
        when(cashRegisterRepository.findMovementsPaged(eq(10L), eq(0), eq(20), any(), any()))
                .thenReturn(List.of(movement));
        when(cashRegisterRepository.countMovements(10L)).thenReturn(1L);

        var result = service.findMovementsPaged(sessionUuid, 0, 20, "createdAt", "desc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals(BigDecimal.valueOf(150.00), result.content().get(0).amount());
    }

    @Test
    void aiConversationsFindPagedComputesOffsetAndMapsResponse() {
        var service = new AiAssistantService(
                mock(ErpAiAgentService.class),
                aiRepository,
                mock(PlatformTransactionManager.class)
        );

        AiConversationResponse convo = new AiConversationResponse(
                UUID.randomUUID(), "Order Inquiry", OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(aiRepository.getConversationsPaged(eq("user-sub-1"), eq(0), eq(20), any(), any()))
                .thenReturn(List.of(convo));
        when(aiRepository.countConversations("user-sub-1")).thenReturn(1L);

        var result = service.getConversationsPaged("user-sub-1", 0, 20, "updatedAt", "desc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals("Order Inquiry", result.content().get(0).title());
    }

    @Test
    void taxRateFindPagedComputesOffsetAndMapsResponse() {
        var service = new TaxRateServiceImpl(
                taxRateRepository,
                mock(AuditLogService.class)
        );

        TaxRate rate = new TaxRate(
                1L, UUID.randomUUID(), "GST 18%",
                BigDecimal.valueOf(18.00), BigDecimal.valueOf(9.00), BigDecimal.valueOf(9.00), BigDecimal.valueOf(18.00),
                LocalDate.now(), null, true, OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(taxRateRepository.findPaged(eq(0), eq(20), any(), any(), any()))
                .thenReturn(List.of(rate));
        when(taxRateRepository.countPaged(any())).thenReturn(1L);

        var result = service.findPaged(0, 20, null, "effectiveFrom", "desc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals("GST 18%", result.content().get(0).taxName());
    }

    @Test
    void dailyClosingFindPagedComputesOffsetAndMapsResponse() {
        var service = new DailyClosingServiceImpl(
                dailyClosingRepository,
                mock(CurrentUserService.class),
                mock(AuditLogService.class),
                "Asia/Kolkata"
        );

        DailyClosingResponse closing = new DailyClosingResponse(
                UUID.randomUUID(), LocalDate.now(), 1L, OffsetDateTime.now(),
                "End of day closing", null, List.of()
        );

        when(dailyClosingRepository.findPaged(eq(0), eq(20), any(), any(), any(), any()))
                .thenReturn(List.of(closing));
        when(dailyClosingRepository.countPaged(any(), any())).thenReturn(1L);

        var result = service.findPaged(0, 20, null, null, "businessDate", "desc");

        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals("End of day closing", result.content().get(0).notes());
    }

    @Test
    void salesFindPagedIntegerOverflowThrowsClientValidationException() {
        var service = new SalesServiceImpl(
                salesRepository,
                mock(InventoryService.class),
                mock(ProductTaxMappingRepository.class),
                mock(TaxRateRepository.class),
                mock(CustomerRepository.class),
                mock(CashRegisterService.class),
                mock(AuditLogService.class)
        );

        assertThrows(ClientValidationException.class, () ->
                service.findPaged(Integer.MAX_VALUE, 20, null, null, null, "id", "desc")
        );
        verifyNoInteractions(salesRepository);
    }

    @Test
    void purchaseFindPagedIntegerOverflowThrowsClientValidationException() {
        var service = new PurchaseServiceImpl(
                purchaseRepository,
                mock(InventoryService.class),
                mock(AuditLogService.class)
        );

        assertThrows(ClientValidationException.class, () ->
                service.findPaged(Integer.MAX_VALUE, 20, null, null, "id", "desc")
        );
        verifyNoInteractions(purchaseRepository);
    }

    @Test
    void inventoryStockFindPagedIntegerOverflowThrowsClientValidationException() {
        var service = new InventoryServiceImpl(
                inventoryRepository,
                mock(AuditLogService.class)
        );

        assertThrows(ClientValidationException.class, () ->
                service.findStockPaged(Integer.MAX_VALUE, 20, null, "productName", "asc")
        );
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void inventoryLowStockFindPagedIntegerOverflowThrowsClientValidationException() {
        var service = new InventoryServiceImpl(
                inventoryRepository,
                mock(AuditLogService.class)
        );

        assertThrows(ClientValidationException.class, () ->
                service.findLowStockPaged(Integer.MAX_VALUE, 20, null, "quantity", "asc")
        );
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void dailyClosingFindPagedIntegerOverflowThrowsClientValidationException() {
        var service = new DailyClosingServiceImpl(
                dailyClosingRepository,
                mock(CurrentUserService.class),
                mock(AuditLogService.class),
                "Asia/Kolkata"
        );

        assertThrows(ClientValidationException.class, () ->
                service.findPaged(Integer.MAX_VALUE, 20, null, null, "businessDate", "desc")
        );
        verifyNoInteractions(dailyClosingRepository);
    }

    @Test
    void expenseFindPagedIntegerOverflowThrowsClientValidationException() {
        var service = new ExpenseServiceImpl(
                expenseRepository,
                mock(ExpenseCategoryRepository.class),
                mock(CurrentUserService.class),
                mock(CashRegisterService.class),
                mock(AuditLogService.class)
        );

        assertThrows(ClientValidationException.class, () ->
                service.findPaged(Integer.MAX_VALUE, 20, null, null, null, "id", "desc")
        );
        verifyNoInteractions(expenseRepository);
    }
}
