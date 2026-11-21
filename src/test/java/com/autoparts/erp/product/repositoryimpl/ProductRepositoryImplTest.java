package com.autoparts.erp.product.repositoryimpl;

import com.autoparts.erp.product.model.Product;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private ProductRepositoryImpl repository;

    @Test
    @DisplayName("findByUuid queries with named parameter :uuid")
    void testFindByUuid() {
        UUID uuid = UUID.randomUUID();
        Product product = createDummyProduct(uuid);

        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(product));

        Optional<Product> result = repository.findByUuid(uuid);

        assertTrue(result.isPresent());
        assertEquals(uuid, result.get().uuid());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramCaptor.capture(), any(RowMapper.class));

        assertTrue(sqlCaptor.getValue().contains(":uuid"));
        assertEquals(uuid, paramCaptor.getValue().getValue("uuid"));
    }

    @Test
    @DisplayName("create inserts product with named parameters using RETURNING id")
    void testCreate() {
        UUID uuid = UUID.randomUUID();
        Product product = createDummyProduct(uuid);

        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(100L);
        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(product));

        Product created = repository.create(
                uuid,
                "PRD-001",
                "PART-99",
                "Brake Pad",
                "Front brake pad",
                1L,
                2L,
                BigDecimal.valueOf(100.00),
                BigDecimal.valueOf(150.00),
                BigDecimal.valueOf(10.0),
                "PCS",
                "BAR-123",
                "CODE128"
        );

        assertNotNull(created);
        assertEquals(uuid, created.uuid());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);

        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), paramCaptor.capture(), eq(Long.class));

        SqlParameterSource params = paramCaptor.getValue();
        assertEquals(uuid, params.getValue("uuid"));
        assertEquals("PRD-001", params.getValue("productCode"));
        assertEquals("PART-99", params.getValue("partNo"));
        assertEquals("Brake Pad", params.getValue("name"));
        assertEquals(1L, params.getValue("categoryId"));
        assertEquals(2L, params.getValue("brandId"));
        assertTrue(sqlCaptor.getValue().contains(":productCode"));
        assertTrue(sqlCaptor.getValue().contains(":categoryId"));
    }

    @Test
    @DisplayName("existsByProductCode binds :productCode parameter")
    void testExistsByProductCode() {
        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        boolean exists = repository.existsByProductCode("PRD-001");

        assertTrue(exists);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), paramCaptor.capture(), eq(Boolean.class));

        assertTrue(sqlCaptor.getValue().contains(":productCode"));
        assertEquals("PRD-001", paramCaptor.getValue().getValue("productCode"));
    }

    private Product createDummyProduct(UUID uuid) {
        return new Product(
                100L,
                uuid,
                "PRD-001",
                "PART-99",
                "Brake Pad",
                "Front brake pad",
                1L,
                "Brakes",
                2L,
                "Bosch",
                BigDecimal.valueOf(100.00),
                BigDecimal.valueOf(150.00),
                25,
                BigDecimal.valueOf(10.0),
                "PCS",
                "BAR-123",
                "CODE128",
                true,
                null,
                null
        );
    }
}
