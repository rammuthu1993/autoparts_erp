package com.autoparts.erp.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.cashregister.dto.CloseCashSessionRequest;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.enums.CashSessionStatus;
import com.autoparts.erp.cashregister.model.CashRegisterSession;
import com.autoparts.erp.cashregister.repository.CashRegisterRepository;
import com.autoparts.erp.cashregister.serviceimpl.CashRegisterServiceImpl;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.dailyclosing.dto.DailyClosingPreviewResponse;
import com.autoparts.erp.dailyclosing.dto.DailyClosingSummary;
import com.autoparts.erp.dailyclosing.repository.DailyClosingRepository;
import com.autoparts.erp.dailyclosing.serviceimpl.DailyClosingServiceImpl;
import com.autoparts.erp.inventory.dto.StockReconciliationRequest;
import com.autoparts.erp.inventory.enums.InventoryMovementType;
import com.autoparts.erp.inventory.enums.InventoryReferenceType;
import com.autoparts.erp.inventory.model.InventoryStock;
import com.autoparts.erp.inventory.repository.InventoryRepository;
import com.autoparts.erp.inventory.serviceimpl.InventoryServiceImpl;
import com.autoparts.erp.purchase.exception.PurchaseErrorCode;
import com.autoparts.erp.purchase.model.Purchase;
import com.autoparts.erp.purchase.model.PurchaseStatus;
import com.autoparts.erp.purchase.repository.PurchaseRepository;
import com.autoparts.erp.purchase.serviceimpl.PurchaseServiceImpl;

@ExtendWith(MockitoExtension.class)
class SixModuleWorkflowsTest {

    @Mock private PurchaseRepository purchaseRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private CashRegisterRepository cashRegisterRepository;
    @Mock private DailyClosingRepository dailyClosingRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private CurrentUserService currentUserService;

    @Test
    void purchaseDraftCancelRejectsAlreadyReceivedPurchase() {
        PurchaseServiceImpl service = new PurchaseServiceImpl(
            purchaseRepository, mock(com.autoparts.erp.inventory.service.InventoryService.class), auditLogService
        );
        UUID uuid = UUID.randomUUID();
        Purchase received = new Purchase(
            1L, uuid, "PUR-0001", 1L, "Bosch", "INV-1", LocalDate.now(),
            PurchaseStatus.RECEIVED, new BigDecimal("1000.00"), BigDecimal.ZERO,
            new BigDecimal("180.00"), new BigDecimal("1180.00"), null, List.of(),
            OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now()
        );
        when(purchaseRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(received));

        ApiException ex = assertThrows(ApiException.class, () -> service.cancelDraft(uuid, "No longer needed"));
        assertEquals(PurchaseErrorCode.PURCHASE_ALREADY_RECEIVED, ex.getError());
    }

    @Test
    void inventoryReconcileRecordsMovementAndUpdatesQuantity() {
        InventoryServiceImpl service = new InventoryServiceImpl(inventoryRepository, auditLogService);
        UUID productUuid = UUID.randomUUID();
        InventoryStock stock = new InventoryStock(
            10L, productUuid, "P-10", "PART-10", "Oil Filter",
            new BigDecimal("15.000"), new BigDecimal("5.000"), "PCS"
        );
        when(inventoryRepository.findStockByUuidForUpdate(productUuid)).thenReturn(Optional.of(stock));
        when(inventoryRepository.createAdjustment(any(), anyString(), any())).thenReturn(50L);

        var resp = service.reconcileStock(productUuid, new StockReconciliationRequest(
            new BigDecimal("12.000"), "Cycle count", "3 units missing"
        ));

        assertEquals(new BigDecimal("12.000"), resp.quantity());
        verify(inventoryRepository).updateProductQuantity(10L, new BigDecimal("12.000"));
        verify(inventoryRepository).createMovement(
            any(), eq(10L), eq(InventoryMovementType.RECONCILIATION), eq(InventoryReferenceType.RECONCILIATION),
            eq(50L), eq(new BigDecimal("-3.000")), eq(new BigDecimal("15.000")), eq(new BigDecimal("12.000")), anyString()
        );
    }

    @Test
    void cashRegisterCloseWithDenominationsRequiresDifferenceReasonOnVariance() {
        CashRegisterServiceImpl service = new CashRegisterServiceImpl(
            currentUserService, cashRegisterRepository, auditLogService
        );
        UUID sessionUuid = UUID.randomUUID();
        CashRegisterSession openSession = new CashRegisterSession(
            1L, sessionUuid, 1L, "Main Counter", 1L,
            OffsetDateTime.now(), new BigDecimal("1000.00"), null, null,
            null, null, null, CashSessionStatus.OPEN
        );
        when(cashRegisterRepository.findSessionByUuidForUpdate(sessionUuid)).thenReturn(Optional.of(openSession));
        when(cashRegisterRepository.calculateTotalByType(1L, CashMovementType.IN.name())).thenReturn(new BigDecimal("500.00"));
        when(cashRegisterRepository.calculateTotalByType(1L, CashMovementType.OUT.name())).thenReturn(BigDecimal.ZERO);

        // Expected = 1500.00; Denomination count = 2*500 + 2*200 = 1400.00 -> diff = -100.00 without reason => rejected!
        CloseCashSessionRequest missingReasonReq = new CloseCashSessionRequest(
            null, true, 2, 2, 0, 0, 0, 0, BigDecimal.ZERO, "   "
        );
        assertThrows(ResponseStatusException.class, () -> service.closeSession(sessionUuid, missingReasonReq));

        // With reason => succeeds!
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(cashRegisterRepository.closeSession(eq(1L), eq(1L), eq(new BigDecimal("1500.00")), eq(new BigDecimal("1400.00")), eq(new BigDecimal("-100.00"))))
            .thenReturn(true);
        when(cashRegisterRepository.findSessionByUuid(sessionUuid)).thenReturn(Optional.of(
            new CashRegisterSession(
                1L, sessionUuid, 1L, "Main Counter", 1L,
                OffsetDateTime.now(), new BigDecimal("1000.00"), 1L, OffsetDateTime.now(),
                new BigDecimal("1500.00"), new BigDecimal("1400.00"), new BigDecimal("-100.00"), CashSessionStatus.CLOSED
            )
        ));

        var closed = service.closeSession(sessionUuid, new CloseCashSessionRequest(
            null, true, 2, 2, 0, 0, 0, 0, BigDecimal.ZERO, "Shortage of 100"
        ));
        assertEquals(CashSessionStatus.CLOSED, closed.status());
        assertEquals(new BigDecimal("1400.00"), closed.actualClosingBalance());
    }

    @Test
    void dailyClosingPreviewDetectsOpenSessionsAndPreventsClosing() {
        DailyClosingServiceImpl service = new DailyClosingServiceImpl(
            dailyClosingRepository, currentUserService, auditLogService, "Asia/Kolkata"
        );
        LocalDate date = LocalDate.now();
        when(dailyClosingRepository.existsByBusinessDate(date)).thenReturn(false);
        when(dailyClosingRepository.countOpenSessions(any())).thenReturn(1L);
        when(dailyClosingRepository.previewSummary(eq(date), any(), any())).thenReturn(null);
        when(dailyClosingRepository.previewRegisterClosings(any(), any())).thenReturn(List.of());

        DailyClosingPreviewResponse preview = service.previewBusinessDate(date);
        assertFalse(preview.canClose());
        assertTrue(preview.hasOpenSessions());
        assertEquals(1L, preview.openSessionCount());
        assertFalse(preview.validationErrors().isEmpty());
    }
}
