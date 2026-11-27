package com.autoparts.erp.masters.unit.controller;

import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/units")
public class UnitController {
    private final JdbcTemplate jdbcTemplate;

    public UnitController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @RequiresPermission("PRODUCTS.VIEW")
    @GetMapping("/active")
    public List<UnitResponse> findActive() {
        return jdbcTemplate.query("""
            SELECT uuid, unit_code, unit_name, symbol, decimal_allowed
            FROM units
            WHERE active = TRUE
            ORDER BY unit_name
            """, (rs, rowNum) -> new UnitResponse(
                rs.getObject("uuid", UUID.class),
                rs.getString("unit_code"),
                rs.getString("unit_name"),
                rs.getString("symbol"),
                rs.getBoolean("decimal_allowed")
            ));
    }

    public record UnitResponse(
        UUID uuid,
        String code,
        String name,
        String symbol,
        boolean decimalAllowed
    ) {}
}
