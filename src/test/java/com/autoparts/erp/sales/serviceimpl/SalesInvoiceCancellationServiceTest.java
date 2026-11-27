package com.autoparts.erp.sales.serviceimpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.auth.security.CurrentUserService;
import com.autoparts.erp.auth.service.PermissionService;
import com.autoparts.erp.cashregister.enums.CashMovementSourceType;
import com.autoparts.erp.cashregister.enums.CashMovementType;
import com.autoparts.erp.cashregister.service.CashRegisterService;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.customer.repository.CustomerRepository;
import com.autoparts.erp.dailyclosing.dto.DailyClosingResponse;
import com.autoparts.erp.dailyclosing.repository.DailyClosingRepository;
import com.autoparts.erp.inventory.service.InventoryService;
import com.autoparts.erp.sales.dto.SalesCancelRequest;
import com.autoparts.erp.sales.dto.SalesCancellationPreviewResponse;
import com.autoparts.erp.sales.dto.SalesCancellationRefundConfirmRequest;
import com.autoparts.erp.sales.dto.SalesCancellationResponse;
import com.autoparts.erp.sales.dto.SalesResponse;
import com.autoparts.erp.sales.enums.CancellationRefundStatus;
import com.autoparts.erp.sales.enums.GstAdjustmentType;
import com.autoparts.erp.sales.enums.PaymentMethod;
import com.autoparts.erp.sales.enums.SalesPaymentStatus;
import com.autoparts.erp.sales.enums.SalesStatus;
import com.autoparts.erp.sales.exception.SalesErrorCode;
import com.autoparts.erp.sales.model.Sales;
import com.autoparts.erp.sales.model.SalesCancellationRefund;
import com.autoparts.erp.sales.model.SalesInvoiceCancellation;
import com.autoparts.erp.sales.model.SalesInvoiceCancellationItem;
import com.autoparts.erp.sales.model.SalesPayment;
import com.autoparts.erp.sales.model.SalesProduct;
import com.autoparts.erp.sales.model.SalesReturn;
import com.autoparts.erp.sales.model.SalesReturnProduct;
import com.autoparts.erp.sales.enums.SalesReturnStatus;
import com.autoparts.erp.sales.repository.SalesCancellationRepository;
import com.autoparts.erp.sales.repository.SalesRepository;
import com.autoparts.erp.sales.repository.SalesReturnRepository;
import com.autoparts.erp.tax.repository.ProductTaxMappingRepository;
import com.autoparts.erp.tax.repository.TaxRateRepository;

@ExtendWith(MockitoExtension.class)
class SalesInvoiceCancellationServiceTest {

    @Mock private SalesRepository salesRepository;
    @Mock private InventoryService inventoryService;
    @Mock private ProductTaxMappingRepository productTaxMappingRepository;
    @Mock private TaxRateRepository taxRateRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CashRegisterService cashRegisterService;
    @Mock private AuditLogService auditLogService;
    @Mock private SalesReturnRepository salesReturnRepository;
    @Mock private SalesCancellationRepository salesCancellationRepository;
    @Mock private DailyClosingRepository dailyClosingRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private PermissionService permissionService;

    private SalesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SalesServiceImpl(
            salesRepository,
            inventoryService,
            productTaxMappingRepository,
            taxRateRepository,
            customerRepository,
            cashRegisterService,
            auditLogService,
            salesReturnRepository,
            salesCancellationRepository,
            dailyClosingRepository,
            currentUserService,
            permissionService
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("manager", "N/A", List.of())
        );
        lenient().when(salesRepository.isSalesStatusConfigured(anyString())).thenReturn(true);
        lenient().when(salesRepository.isSalesPaymentStatusConfigured(anyString())).thenReturn(true);
        lenient().when(permissionService.hasPermission(any(), eq("SALES.CANCEL_ISSUED"))).thenReturn(true);
        lenient().when(currentUserService.getCurrentUserId()).thenReturn(42L);
        lenient().when(salesCancellationRepository.createCancellation(any())).thenAnswer(inv -> {
            SalesInvoiceCancellation c = inv.getArgument(0);
            return new SalesInvoiceCancellation(
                900L, c.uuid(), c.saleId(), c.invoiceNumber(), c.idempotencyKey(),
                c.cancellationReason(), c.cancelledBy(), c.cancelledAt(),
                c.originalGrandTotal(), c.originalTaxableAmount(), c.originalCgstAmount(),
                c.originalSgstAmount(), c.originalIgstAmount(), c.originalTotalTaxAmount(),
                c.previouslyPaidAmount(), c.previouslyReturnedAmount(), c.previouslyRefundedAmount(),
                c.cancelledInvoiceAmount(), c.refundAmount(), c.refundMethod(), c.refundStatus(),
                c.refundReference(), c.refundConfirmedAt(), c.refundConfirmedBy(),
                c.receivableAdjustmentAmount(), c.gstAdjustmentType(), c.gstAdjustmentReference(),
                c.creditNoteNumber(), c.irn(), c.irnStatus(), c.ewayBillNumber(), c.ewayBillStatus(),
                c.notes(), OffsetDateTime.now(), OffsetDateTime.now()
            );
        });
        lenient().when(salesCancellationRepository.createCancellationItem(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(salesCancellationRepository.createCancellationRefund(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void draftCancellationRemainsFunctional() {
        UUID saleUuid = UUID.randomUUID();
        Sales draft = sampleSale(1L, saleUuid, "DRF-1234", SalesStatus.DRAFT, new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("1000.00"));
        Sales cancelled = sampleSale(1L, saleUuid, "DRF-1234", SalesStatus.CANCELLED, new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(draft));
        when(salesRepository.findByUuid(saleUuid)).thenReturn(Optional.of(cancelled));
        when(salesRepository.findProductsBySaleId(1L)).thenReturn(List.of());
        when(salesRepository.findPaymentsBySaleId(1L)).thenReturn(List.of());
        when(salesRepository.findCancellationReason(1L)).thenReturn(Optional.of("Created by mistake"));

        SalesResponse response = service.cancel(saleUuid, "  Created by mistake  ");

        assertEquals(SalesStatus.CANCELLED, response.status());
        assertNull(response.paymentStatus());
        assertEquals("Created by mistake", response.cancellationReason());
        verifyNoInteractions(inventoryService, cashRegisterService, salesCancellationRepository);
    }

    @Test
    void issuedUnpaidInvoiceCancellationReversesStockAndReceivableWithoutRefund() {
        UUID saleUuid = UUID.randomUUID();
        Sales issued = sampleSale(10L, saleUuid, "GAS/2026-27/00001", SalesStatus.COMPLETED, new BigDecimal("1180.00"), BigDecimal.ZERO, new BigDecimal("1180.00"));
        Sales cancelled = sampleSale(10L, saleUuid, "GAS/2026-27/00001", SalesStatus.CANCELLED, new BigDecimal("1180.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        SalesProduct item = sampleProduct(100L, 501L, "PART-01", new BigDecimal("2.000"), new BigDecimal("1000.00"), new BigDecimal("180.00"), new BigDecimal("1180.00"));

        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(issued));
        when(salesRepository.findByUuid(saleUuid)).thenReturn(Optional.of(cancelled));
        when(salesRepository.findProductsBySaleId(10L)).thenReturn(List.of(item));
        when(salesRepository.findPaymentsBySaleId(10L)).thenReturn(List.of());
        when(salesReturnRepository.findReturnsBySaleId(10L)).thenReturn(List.of());
        when(salesReturnRepository.getTotalRefundedAmountBySaleId(10L)).thenReturn(BigDecimal.ZERO);
        when(salesReturnRepository.findReturnedProductsBySaleProductId(100L)).thenReturn(List.of());

        SalesResponse response = service.cancel(saleUuid, new SalesCancelRequest("Customer cancelled order"));

        assertEquals(SalesStatus.CANCELLED, response.status());
        verify(inventoryService).restoreStockFromSalesCancellation(501L, new BigDecimal("2.000"), 900L, "GAS/2026-27/00001");
        verifyNoInteractions(cashRegisterService);

        ArgumentCaptor<SalesInvoiceCancellation> captor = ArgumentCaptor.forClass(SalesInvoiceCancellation.class);
        verify(salesCancellationRepository).createCancellation(captor.capture());
        assertEquals(new BigDecimal("0.00"), captor.getValue().refundAmount());
        assertEquals(new BigDecimal("1180.00"), captor.getValue().receivableAdjustmentAmount());
        assertEquals(CancellationRefundStatus.NOT_REQUIRED, captor.getValue().refundStatus());
        assertEquals(GstAdjustmentType.INVOICE_CANCELLATION, captor.getValue().gstAdjustmentType());
    }

    @Test
    void issuedPartiallyPaidCashInvoicePostsCashOutAndReversesRemainingReceivable() {
        UUID saleUuid = UUID.randomUUID();
        Sales issued = sampleSale(11L, saleUuid, "GAS/2026-27/00002", SalesStatus.COMPLETED, new BigDecimal("10000.00"), new BigDecimal("4000.00"), new BigDecimal("6000.00"));
        Sales cancelled = sampleSale(11L, saleUuid, "GAS/2026-27/00002", SalesStatus.CANCELLED, new BigDecimal("10000.00"), new BigDecimal("4000.00"), BigDecimal.ZERO);
        SalesProduct item = sampleProduct(101L, 502L, "PART-02", new BigDecimal("5.000"), new BigDecimal("8474.58"), new BigDecimal("1525.42"), new BigDecimal("10000.00"));
        SalesPayment cashPayment = new SalesPayment(1L, UUID.randomUUID(), 11L, PaymentMethod.CASH, new BigDecimal("4000.00"), null, OffsetDateTime.now(), null, null);

        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(issued));
        when(salesRepository.findByUuid(saleUuid)).thenReturn(Optional.of(cancelled));
        when(salesRepository.findProductsBySaleId(11L)).thenReturn(List.of(item));
        when(salesRepository.findPaymentsBySaleId(11L)).thenReturn(List.of(cashPayment));
        when(salesReturnRepository.findReturnsBySaleId(11L)).thenReturn(List.of());
        when(salesReturnRepository.getTotalRefundedAmountBySaleId(11L)).thenReturn(BigDecimal.ZERO);
        when(salesReturnRepository.findReturnedProductsBySaleProductId(101L)).thenReturn(List.of());

        service.cancel(saleUuid, new SalesCancelRequest("Customer ordered wrong part", PaymentMethod.CASH, null, null, null, null, null, null));

        verify(cashRegisterService).recordTransactionMovement(
            eq(CashMovementType.OUT),
            eq(CashMovementSourceType.SALES_CANCELLATION_REFUND),
            any(UUID.class),
            eq(new BigDecimal("4000.00")),
            contains("GAS/2026-27/00002")
        );

        ArgumentCaptor<SalesInvoiceCancellation> captor = ArgumentCaptor.forClass(SalesInvoiceCancellation.class);
        verify(salesCancellationRepository).createCancellation(captor.capture());
        assertEquals(new BigDecimal("4000.00"), captor.getValue().refundAmount());
        assertEquals(new BigDecimal("6000.00"), captor.getValue().receivableAdjustmentAmount());
        assertEquals(CancellationRefundStatus.SUCCESSFUL, captor.getValue().refundStatus());
    }

    @Test
    void issuedFullyPaidUpiInvoiceCreatesPendingExternalRefundWithoutCashRegisterMovement() {
        UUID saleUuid = UUID.randomUUID();
        Sales issued = sampleSale(12L, saleUuid, "GAS/2026-27/00003", SalesStatus.COMPLETED, new BigDecimal("5000.00"), new BigDecimal("5000.00"), BigDecimal.ZERO);
        Sales cancelled = sampleSale(12L, saleUuid, "GAS/2026-27/00003", SalesStatus.CANCELLED, new BigDecimal("5000.00"), new BigDecimal("5000.00"), BigDecimal.ZERO);
        SalesProduct item = sampleProduct(102L, 503L, "PART-03", new BigDecimal("1.000"), new BigDecimal("4237.29"), new BigDecimal("762.71"), new BigDecimal("5000.00"));
        SalesPayment upiPayment = new SalesPayment(2L, UUID.randomUUID(), 12L, PaymentMethod.UPI, new BigDecimal("5000.00"), "UPI-123", OffsetDateTime.now(), null, null);

        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(issued));
        when(salesRepository.findByUuid(saleUuid)).thenReturn(Optional.of(cancelled));
        when(salesRepository.findProductsBySaleId(12L)).thenReturn(List.of(item));
        when(salesRepository.findPaymentsBySaleId(12L)).thenReturn(List.of(upiPayment));
        when(salesReturnRepository.findReturnsBySaleId(12L)).thenReturn(List.of());
        when(salesReturnRepository.getTotalRefundedAmountBySaleId(12L)).thenReturn(BigDecimal.ZERO);
        when(salesReturnRepository.findReturnedProductsBySaleProductId(102L)).thenReturn(List.of());

        service.cancel(saleUuid, new SalesCancelRequest("Wrong spare part", PaymentMethod.UPI, "UPI-REF-999", null, false, null, null, null));

        verifyNoInteractions(cashRegisterService);
        ArgumentCaptor<SalesInvoiceCancellation> captor = ArgumentCaptor.forClass(SalesInvoiceCancellation.class);
        verify(salesCancellationRepository).createCancellation(captor.capture());
        assertEquals(CancellationRefundStatus.PENDING, captor.getValue().refundStatus());
        assertEquals(new BigDecimal("5000.00"), captor.getValue().refundAmount());
    }

    @Test
    void partialReturnsBeforeCancellationRestoreOnlyRemainingStockAndNetPaidAmount() {
        UUID saleUuid = UUID.randomUUID();
        Sales issued = sampleSale(13L, saleUuid, "GAS/2026-27/00004", SalesStatus.COMPLETED, new BigDecimal("10000.00"), new BigDecimal("4000.00"), new BigDecimal("6000.00"));
        Sales cancelled = sampleSale(13L, saleUuid, "GAS/2026-27/00004", SalesStatus.CANCELLED, new BigDecimal("10000.00"), new BigDecimal("4000.00"), BigDecimal.ZERO);
        SalesProduct item = sampleProduct(103L, 504L, "PART-04", new BigDecimal("10.000"), new BigDecimal("8474.58"), new BigDecimal("1525.42"), new BigDecimal("10000.00"));
        SalesPayment cashPayment = new SalesPayment(3L, UUID.randomUUID(), 13L, PaymentMethod.CASH, new BigDecimal("4000.00"), null, OffsetDateTime.now(), null, null);

        SalesReturn prevReturn = new SalesReturn(1L, UUID.randomUUID(), 13L, OffsetDateTime.now(), "Damaged", null,
            new BigDecimal("3000.00"), BigDecimal.ZERO, new BigDecimal("2542.37"), new BigDecimal("228.81"),
            new BigDecimal("228.82"), BigDecimal.ZERO, new BigDecimal("457.63"), new BigDecimal("3000.00"),
            SalesReturnStatus.PARTIALLY_REFUNDED, null, null);
        SalesReturnProduct prevReturnItem = new SalesReturnProduct(1L, UUID.randomUUID(), 1L, 103L, 504L, "PART-04", "Part 04",
            new BigDecimal("3.000"), "PCS", new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("2542.37"), "8708",
            new BigDecimal("18.00"), new BigDecimal("9.00"), new BigDecimal("9.00"), BigDecimal.ZERO,
            new BigDecimal("228.81"), new BigDecimal("228.82"), BigDecimal.ZERO, new BigDecimal("457.63"), new BigDecimal("3000.00"), null);

        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(issued));
        when(salesRepository.findByUuid(saleUuid)).thenReturn(Optional.of(cancelled));
        when(salesRepository.findProductsBySaleId(13L)).thenReturn(List.of(item));
        when(salesRepository.findPaymentsBySaleId(13L)).thenReturn(List.of(cashPayment));
        when(salesReturnRepository.findReturnsBySaleId(13L)).thenReturn(List.of(prevReturn));
        when(salesReturnRepository.getTotalRefundedAmountBySaleId(13L)).thenReturn(new BigDecimal("1000.00"));
        when(salesReturnRepository.findReturnedProductsBySaleProductId(103L)).thenReturn(List.of(prevReturnItem));

        service.cancel(saleUuid, new SalesCancelRequest("Remaining items cancelled", PaymentMethod.CASH, null, null, null, null, null, null));

        // 10 sold - 3 returned = 7 restored
        verify(inventoryService).restoreStockFromSalesCancellation(504L, new BigDecimal("7.000"), 900L, "GAS/2026-27/00004");
        // 4000 paid - 1000 previously refunded = 3000 net refund
        verify(cashRegisterService).recordTransactionMovement(
            eq(CashMovementType.OUT),
            eq(CashMovementSourceType.SALES_CANCELLATION_REFUND),
            any(UUID.class),
            eq(new BigDecimal("3000.00")),
            anyString()
        );

        ArgumentCaptor<SalesInvoiceCancellation> captor = ArgumentCaptor.forClass(SalesInvoiceCancellation.class);
        verify(salesCancellationRepository).createCancellation(captor.capture());
        assertEquals(new BigDecimal("7000.00"), captor.getValue().cancelledInvoiceAmount());
        assertEquals(new BigDecimal("3000.00"), captor.getValue().refundAmount());
        assertEquals(new BigDecimal("4000.00"), captor.getValue().receivableAdjustmentAmount());
        assertEquals(GstAdjustmentType.CREDIT_NOTE_REQUIRED, captor.getValue().gstAdjustmentType());
    }

    @Test
    void fullReturnsBeforeCancellationRestoresZeroStockAndZeroRefund() {
        UUID saleUuid = UUID.randomUUID();
        Sales issued = sampleSale(14L, saleUuid, "GAS/2026-27/00005", SalesStatus.COMPLETED, new BigDecimal("2000.00"), new BigDecimal("2000.00"), BigDecimal.ZERO);
        Sales cancelled = sampleSale(14L, saleUuid, "GAS/2026-27/00005", SalesStatus.CANCELLED, new BigDecimal("2000.00"), new BigDecimal("2000.00"), BigDecimal.ZERO);
        SalesProduct item = sampleProduct(104L, 505L, "PART-05", new BigDecimal("2.000"), new BigDecimal("1694.92"), new BigDecimal("305.08"), new BigDecimal("2000.00"));
        SalesPayment cashPayment = new SalesPayment(4L, UUID.randomUUID(), 14L, PaymentMethod.CASH, new BigDecimal("2000.00"), null, OffsetDateTime.now(), null, null);
        SalesReturn fullReturn = new SalesReturn(2L, UUID.randomUUID(), 14L, OffsetDateTime.now(), "Full return", null,
            new BigDecimal("2000.00"), BigDecimal.ZERO, new BigDecimal("1694.92"), new BigDecimal("152.54"),
            new BigDecimal("152.54"), BigDecimal.ZERO, new BigDecimal("305.08"), new BigDecimal("2000.00"),
            SalesReturnStatus.REFUNDED, null, null);
        SalesReturnProduct fullReturnItem = new SalesReturnProduct(2L, UUID.randomUUID(), 2L, 104L, 505L, "PART-05", "Part 05",
            new BigDecimal("2.000"), "PCS", new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("1694.92"), "8708",
            new BigDecimal("18.00"), new BigDecimal("9.00"), new BigDecimal("9.00"), BigDecimal.ZERO,
            new BigDecimal("152.54"), new BigDecimal("152.54"), BigDecimal.ZERO, new BigDecimal("305.08"), new BigDecimal("2000.00"), null);

        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(issued));
        when(salesRepository.findByUuid(saleUuid)).thenReturn(Optional.of(cancelled));
        when(salesRepository.findProductsBySaleId(14L)).thenReturn(List.of(item));
        when(salesRepository.findPaymentsBySaleId(14L)).thenReturn(List.of(cashPayment));
        when(salesReturnRepository.findReturnsBySaleId(14L)).thenReturn(List.of(fullReturn));
        when(salesReturnRepository.getTotalRefundedAmountBySaleId(14L)).thenReturn(new BigDecimal("2000.00"));
        when(salesReturnRepository.findReturnedProductsBySaleProductId(104L)).thenReturn(List.of(fullReturnItem));

        service.cancel(saleUuid, new SalesCancelRequest("Close cancelled invoice"));

        verifyNoInteractions(inventoryService, cashRegisterService);
        ArgumentCaptor<SalesInvoiceCancellation> captor = ArgumentCaptor.forClass(SalesInvoiceCancellation.class);
        verify(salesCancellationRepository).createCancellation(captor.capture());
        assertEquals(new BigDecimal("0.00"), captor.getValue().refundAmount());
        assertEquals(CancellationRefundStatus.NOT_REQUIRED, captor.getValue().refundStatus());
    }

    @Test
    void rejectsBlankReasonAlreadyCancelledAndClosedPeriod() {
        UUID saleUuid = UUID.randomUUID();
        ApiException blankEx = assertThrows(ApiException.class, () -> service.cancel(saleUuid, new SalesCancelRequest("   ")));
        assertEquals(SalesErrorCode.INVALID_CANCELLATION_REASON, blankEx.getError());

        Sales alreadyCancelled = sampleSale(15L, saleUuid, "GAS/2026-27/00006", SalesStatus.CANCELLED, new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(alreadyCancelled));
        ApiException dupEx = assertThrows(ApiException.class, () -> service.cancel(saleUuid, new SalesCancelRequest("Duplicate")));
        assertEquals(SalesErrorCode.SALES_ALREADY_CANCELLED, dupEx.getError());

        ReflectionTestUtils.setField(service, "blockClosedDailyPeriod", true);
        Sales issued = sampleSale(16L, saleUuid, "GAS/2026-27/00007", SalesStatus.COMPLETED, new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"));
        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(issued));
        when(dailyClosingRepository.findByBusinessDate(issued.saleDate())).thenReturn(Optional.of(mock(DailyClosingResponse.class)));
        ApiException periodEx = assertThrows(ApiException.class, () -> service.cancel(saleUuid, new SalesCancelRequest("Closed day")));
        assertEquals(SalesErrorCode.SALES_CANCELLATION_PERIOD_LOCKED, periodEx.getError());
    }

    @Test
    void idempotencyKeyReplayReturnsExistingCancelledSaleWithoutDoubleProcessing() {
        UUID saleUuid = UUID.randomUUID();
        Sales cancelled = sampleSale(17L, saleUuid, "GAS/2026-27/00008", SalesStatus.CANCELLED, new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        SalesInvoiceCancellation existing = new SalesInvoiceCancellation(
            1L, UUID.randomUUID(), 17L, "GAS/2026-27/00008", "idem-key-1", "Reason", 42L, OffsetDateTime.now(),
            new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500.00"), BigDecimal.ZERO, null,
            CancellationRefundStatus.NOT_REQUIRED, null, null, null, new BigDecimal("500.00"),
            GstAdjustmentType.INVOICE_CANCELLATION, "CANC-1", null, null, "NOT_APPLICABLE", null, "NOT_APPLICABLE",
            null, null, null
        );

        when(salesCancellationRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.of(existing));
        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(cancelled));
        when(salesRepository.findByUuid(saleUuid)).thenReturn(Optional.of(cancelled));
        when(salesRepository.findProductsBySaleId(17L)).thenReturn(List.of());
        when(salesRepository.findPaymentsBySaleId(17L)).thenReturn(List.of());

        SalesResponse resp = service.cancel(saleUuid, new SalesCancelRequest("Reason", null, null, null, null, null, "idem-key-1", null));
        assertEquals(SalesStatus.CANCELLED, resp.status());
        verifyNoInteractions(inventoryService, cashRegisterService);
    }

    @Test
    void missingCashSessionThrowsAndPreventsCommit() {
        UUID saleUuid = UUID.randomUUID();
        Sales issued = sampleSale(18L, saleUuid, "GAS/2026-27/00009", SalesStatus.COMPLETED, new BigDecimal("1000.00"), new BigDecimal("1000.00"), BigDecimal.ZERO);
        SalesProduct item = sampleProduct(108L, 508L, "PART-08", new BigDecimal("1.000"), new BigDecimal("847.46"), new BigDecimal("152.54"), new BigDecimal("1000.00"));
        SalesPayment cashPayment = new SalesPayment(8L, UUID.randomUUID(), 18L, PaymentMethod.CASH, new BigDecimal("1000.00"), null, OffsetDateTime.now(), null, null);

        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(issued));
        when(salesRepository.findProductsBySaleId(18L)).thenReturn(List.of(item));
        when(salesRepository.findPaymentsBySaleId(18L)).thenReturn(List.of(cashPayment));
        when(salesReturnRepository.findReturnsBySaleId(18L)).thenReturn(List.of());
        when(salesReturnRepository.getTotalRefundedAmountBySaleId(18L)).thenReturn(BigDecimal.ZERO);
        when(salesReturnRepository.findReturnedProductsBySaleProductId(108L)).thenReturn(List.of());
        doThrow(new IllegalStateException("No open cash register session found for the current user"))
            .when(cashRegisterService).recordTransactionMovement(any(), any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () ->
            service.cancel(saleUuid, new SalesCancelRequest("Cash refund test", PaymentMethod.CASH, null, null, null, null, null, null))
        );
    }

    @Test
    void requiresCancelIssuedPermissionAndSupportsExternalRefundConfirmation() {
        UUID saleUuid = UUID.randomUUID();
        Sales issued = sampleSale(19L, saleUuid, "GAS/2026-27/00010", SalesStatus.COMPLETED, new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00"));
        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(issued));
        when(permissionService.hasPermission(any(), eq("SALES.CANCEL_ISSUED"))).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
            service.cancel(saleUuid, new SalesCancelRequest("Unauthorized attempt"))
        );

        // Now test confirming a pending UPI refund
        when(permissionService.hasPermission(any(), eq("SALES.CANCEL_ISSUED"))).thenReturn(true);
        UUID refundUuid = UUID.randomUUID();
        Sales cancelled = sampleSale(19L, saleUuid, "GAS/2026-27/00010", SalesStatus.CANCELLED, new BigDecimal("500.00"), new BigDecimal("500.00"), BigDecimal.ZERO);
        SalesInvoiceCancellation cancellation = new SalesInvoiceCancellation(
            55L, UUID.randomUUID(), 19L, "GAS/2026-27/00010", null, "Reason", 42L, OffsetDateTime.now(),
            new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500.00"), new BigDecimal("500.00"),
            PaymentMethod.UPI, CancellationRefundStatus.PENDING, null, null, null, BigDecimal.ZERO,
            GstAdjustmentType.INVOICE_CANCELLATION, "CANC-10", null, null, "NOT_APPLICABLE", null, "NOT_APPLICABLE",
            null, null, null
        );
        SalesCancellationRefund pendingRefund = new SalesCancellationRefund(
            77L, refundUuid, 55L, 19L, null, PaymentMethod.UPI, new BigDecimal("500.00"),
            CancellationRefundStatus.PENDING, null, OffsetDateTime.now(), null, null, null, null, null
        );
        SalesCancellationRefund confirmedRefund = new SalesCancellationRefund(
            77L, refundUuid, 55L, 19L, null, PaymentMethod.UPI, new BigDecimal("500.00"),
            CancellationRefundStatus.SUCCESSFUL, "UPI-CONF-777", OffsetDateTime.now(), OffsetDateTime.now(), 42L, null, null, null
        );

        when(salesRepository.findByUuidForUpdate(saleUuid)).thenReturn(Optional.of(cancelled));
        when(salesRepository.findByUuid(saleUuid)).thenReturn(Optional.of(cancelled));
        when(salesCancellationRepository.findBySaleId(19L)).thenReturn(Optional.of(cancellation));
        when(salesCancellationRepository.findRefundByUuidForUpdate(refundUuid)).thenReturn(Optional.of(pendingRefund));
        when(salesCancellationRepository.updateCancellationRefund(any())).thenReturn(confirmedRefund);
        when(salesCancellationRepository.findRefundsByCancellationId(55L)).thenReturn(List.of(confirmedRefund));
        when(salesCancellationRepository.findDetailedBySaleId(19L)).thenReturn(Optional.of(
            new SalesCancellationResponse(
                cancellation.uuid(), saleUuid, "GAS/2026-27/00010", "Reason", 42L, "manager", OffsetDateTime.now(),
                new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500.00"), new BigDecimal("500.00"),
                PaymentMethod.UPI, CancellationRefundStatus.SUCCESSFUL, "UPI-CONF-777", OffsetDateTime.now(), BigDecimal.ZERO,
                GstAdjustmentType.INVOICE_CANCELLATION, "CANC-10", null, null, "NOT_APPLICABLE", null, "NOT_APPLICABLE",
                null, List.of(), List.of()
            )
        ));

        SalesCancellationResponse confirmed = service.confirmCancellationRefund(
            saleUuid, refundUuid, new SalesCancellationRefundConfirmRequest(CancellationRefundStatus.SUCCESSFUL, "UPI-CONF-777", null)
        );
        assertEquals(CancellationRefundStatus.SUCCESSFUL, confirmed.refundStatus());
        assertEquals("UPI-CONF-777", confirmed.refundReference());
    }

    private Sales sampleSale(Long id, UUID uuid, String invoiceNumber, SalesStatus status, BigDecimal grandTotal, BigDecimal paid, BigDecimal balance) {
        return new Sales(
            id, uuid, invoiceNumber, 1L, "Test Customer", "9999999999", LocalDate.now(),
            grandTotal, BigDecimal.ZERO, grandTotal, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, grandTotal, paid, balance, status,
            paid.compareTo(BigDecimal.ZERO) == 0 ? SalesPaymentStatus.UNPAID :
                (paid.compareTo(grandTotal) >= 0 ? SalesPaymentStatus.PAID : SalesPaymentStatus.PARTIALLY_PAID),
            null, OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    private SalesProduct sampleProduct(Long id, Long productId, String code, BigDecimal qty, BigDecimal taxable, BigDecimal tax, BigDecimal total) {
        return new SalesProduct(
            id, UUID.randomUUID(), 1L, productId, UUID.randomUUID(), code, "Test Part",
            qty, "PCS", new BigDecimal("500.00"), BigDecimal.ZERO, taxable, "8708",
            new BigDecimal("18.00"), new BigDecimal("9.00"), new BigDecimal("9.00"), BigDecimal.ZERO,
            tax.divide(new BigDecimal("2")), tax.divide(new BigDecimal("2")), BigDecimal.ZERO, tax, total, null
        );
    }
}
