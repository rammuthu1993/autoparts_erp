package com.autoparts.erp.product.controller;

import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductPriceHistoryController {
    private final JdbcTemplate jdbcTemplate;

    public ProductPriceHistoryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @RequiresPermission("PRODUCTS.VIEW")
    @GetMapping("/{productUuid}/price-history")
    public List<PriceHistoryResponse> findPriceHistory(@PathVariable UUID productUuid) {
        Long productId = jdbcTemplate.query(
            "SELECT id FROM products WHERE uuid = ?",
            rs -> rs.next() ? rs.getLong("id") : null,
            productUuid
        );
        if (productId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        return jdbcTemplate.query("""
            SELECT purchase_price, selling_price, effective_at, change_source
            FROM product_price_history
            WHERE product_id = ?
            ORDER BY effective_at DESC, id DESC
            """, (rs, rowNum) -> new PriceHistoryResponse(
                rs.getBigDecimal("purchase_price"),
                rs.getBigDecimal("selling_price"),
                rs.getObject("effective_at", OffsetDateTime.class),
                rs.getString("change_source")
            ), productId);
    }

    public record PriceHistoryResponse(
        BigDecimal purchasePrice,
        BigDecimal sellingPrice,
        OffsetDateTime effectiveAt,
        String changeSource
    ) {}
}
