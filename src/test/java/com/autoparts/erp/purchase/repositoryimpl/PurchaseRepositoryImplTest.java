package com.autoparts.erp.purchase.repositoryimpl;

import com.autoparts.erp.purchase.model.Purchase;
import com.autoparts.erp.purchase.model.PurchaseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private PurchaseRepositoryImpl repository;

    @Test
    @DisplayName("findByUuid queries with named parameter :uuid and joins items")
    void testFindByUuid() {
        UUID uuid = UUID.randomUUID();
        Purchase purchase = createDummyPurchase(uuid);

        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(purchase)) // for findByUuid
                .thenReturn(List.of());        // for findProducts

        Optional<Purchase> result = repository.findByUuid(uuid);

        assertTrue(result.isPresent());
        assertEquals(uuid, result.get().uuid());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), paramCaptor.capture(), any(RowMapper.class));

        assertTrue(sqlCaptor.getAllValues().get(0).contains(":uuid"));
        assertEquals(uuid, paramCaptor.getAllValues().get(0).getValue("uuid"));
    }

    @Test
    @DisplayName("createPurchase binds named parameters and returns Purchase")
    void testCreatePurchase() {
        UUID uuid = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        Purchase purchase = createDummyPurchase(uuid);

        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(purchase);

        Purchase created = repository.createPurchase(
                uuid,
                "PUR-2026-001",
                50L,
                "INV-999",
                today,
                "Test notes"
        );

        assertNotNull(created);
        assertEquals(uuid, created.uuid());
        assertEquals("PUR-2026-001", created.purchaseNumber());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);

        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), paramCaptor.capture(), any(RowMapper.class));

        SqlParameterSource params = paramCaptor.getValue();
        assertEquals(uuid, params.getValue("uuid"));
        assertEquals("PUR-2026-001", params.getValue("purchaseNumber"));
        assertEquals(50L, params.getValue("supplierId"));
        assertEquals("INV-999", params.getValue("supplierInvoiceNo"));
        assertEquals(today, params.getValue("purchaseDate"));
        assertEquals("Test notes", params.getValue("notes"));
        assertTrue(sqlCaptor.getValue().contains(":purchaseNumber"));
        assertTrue(sqlCaptor.getValue().contains(":supplierId"));
    }

    private Purchase createDummyPurchase(UUID uuid) {
        return new Purchase(
                1001L,
                uuid,
                "PUR-2026-001",
                50L,
                "Acme Supplies",
                "INV-999",
                LocalDate.now(),
                PurchaseStatus.DRAFT,
                BigDecimal.valueOf(1000.00),
                BigDecimal.valueOf(180.00),
                BigDecimal.valueOf(1180.00),
                BigDecimal.valueOf(1180.00),
                "Test notes",
                List.of(),
                null,
                null,
                null
        );
    }
}
