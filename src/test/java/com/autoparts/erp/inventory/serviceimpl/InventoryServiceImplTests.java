package com.autoparts.erp.inventory.serviceimpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.inventory.enums.InventoryMovementType;
import com.autoparts.erp.inventory.enums.InventoryReferenceType;
import com.autoparts.erp.inventory.exception.InventoryErrorCode;
import com.autoparts.erp.inventory.model.InventoryStock;
import com.autoparts.erp.inventory.repository.InventoryRepository;

class InventoryServiceImplTests {

    private InventoryRepository repository;
    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(InventoryRepository.class);
        service = new InventoryServiceImpl(repository);
    }

    @Test
    void increaseStockUpdatesQuantityAndRecordsPurchaseMovement() {
        InventoryStock stock = stock("10.000");
        when(repository.findStockByIdForUpdate(1L))
                .thenReturn(Optional.of(stock));

        InventoryStock updated = service.increaseStock(
                1L,
                new BigDecimal("2.500"),
                9L
        );

        assertEquals(new BigDecimal("12.500"), updated.quantity());
        verify(repository).updateProductQuantity(
                1L,
                new BigDecimal("12.500")
        );
        verify(repository).createMovement(
                any(UUID.class),
                eq(1L),
                eq(InventoryMovementType.PURCHASE_RECEIPT),
                eq(InventoryReferenceType.PURCHASE),
                eq(9L),
                eq(new BigDecimal("2.500")),
                eq(new BigDecimal("10.000")),
                eq(new BigDecimal("12.500")),
                eq("Stock received from purchase")
        );
    }

    @Test
    void decreaseStockRecordsNegativeSaleMovement() {
        InventoryStock stock = stock("10.000");
        when(repository.findStockByIdForUpdate(1L))
                .thenReturn(Optional.of(stock));

        InventoryStock updated = service.decreaseStock(
                1L,
                new BigDecimal("3.000"),
                12L
        );

        assertEquals(new BigDecimal("7.000"), updated.quantity());
        verify(repository).updateProductQuantity(
                1L,
                new BigDecimal("7.000")
        );
        verify(repository).createMovement(
                any(UUID.class),
                eq(1L),
                eq(InventoryMovementType.SALE),
                eq(InventoryReferenceType.SALE),
                eq(12L),
                eq(new BigDecimal("-3.000")),
                eq(new BigDecimal("10.000")),
                eq(new BigDecimal("7.000")),
                eq("Stock reduced through sales invoice")
        );
    }

    @Test
    void decreaseStockRejectsInsufficientQuantityWithoutWriting() {
        when(repository.findStockByIdForUpdate(1L))
                .thenReturn(Optional.of(stock("2.000")));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.decreaseStock(
                        1L,
                        new BigDecimal("3.000"),
                        12L
                )
        );

        assertEquals(InventoryErrorCode.INSUFFICIENT_STOCK, exception.getError());
        verify(repository, never()).updateProductQuantity(
                Mockito.anyLong(),
                any(BigDecimal.class)
        );
        verify(repository, never()).createMovement(
                any(UUID.class),
                Mockito.anyLong(),
                Mockito.any(InventoryMovementType.class),
                Mockito.any(InventoryReferenceType.class),
                Mockito.anyLong(),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                Mockito.anyString()
        );
    }

    @Test
    void stockChangeRejectsZeroQuantityBeforeLocking() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.increaseStock(1L, BigDecimal.ZERO, 9L)
        );

        assertEquals(
                InventoryErrorCode.INVALID_STOCK_QUANTITY,
                exception.getError()
        );
        verify(repository, never()).findStockByIdForUpdate(Mockito.anyLong());
    }

    @Test
    void initializeOpeningStockRecordsOpeningMovement() {
        when(repository.findStockByIdForUpdate(1L))
                .thenReturn(Optional.of(stock("0.000")));

        InventoryStock updated = service.initializeOpeningStock(
                1L,
                new BigDecimal("5.000")
        );

        assertEquals(new BigDecimal("5.000"), updated.quantity());
        verify(repository).createMovement(
                any(UUID.class),
                eq(1L),
                eq(InventoryMovementType.OPENING_STOCK),
                eq(InventoryReferenceType.OPENING_STOCK),
                eq(1L),
                eq(new BigDecimal("5.000")),
                eq(new BigDecimal("0.000")),
                eq(new BigDecimal("5.000")),
                eq("Initial product stock")
        );
    }

    private InventoryStock stock(String quantity) {
        return new InventoryStock(
                1L,
                UUID.randomUUID(),
                "P-001",
                "PART-001",
                "Test product",
                new BigDecimal(quantity),
                BigDecimal.ZERO,
                "PCS"
        );
    }
}
