package com.autoparts.erp.ai.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AiToolRegistry {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AiToolRegistry(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> definitions() {

        return List.of(

                Map.of(
                        "type", "function",
                        "name", "search_products",
                        "description",
                        "Search active ERP products by product name, product code or part number. Use this when the user asks to find or look up products.",
                        "strict", true,
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of(
                                                "type", "string",
                                                "description",
                                                "Product name, product code or part number to search"
                                        )
                                ),
                                "required", List.of("query"),
                                "additionalProperties", false
                        )
                ),

                Map.of(
                        "type", "function",
                        "name", "get_low_stock_products",
                        "description",
                        "Return active products whose current quantity is below their configured minimum stock. Use this for low-stock and replenishment questions.",
                        "strict", true,
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of(),
                                "additionalProperties", false
                        )
                )
        );
    }

    public JsonNode execute(String toolName, String arguments) throws Exception {

        JsonNode args = objectMapper.readTree(arguments);

        return switch (toolName) {
            case "search_products" -> searchProducts(args);
            case "get_low_stock_products" -> getLowStockProducts();
            default -> throw new IllegalArgumentException(
                    "Unsupported AI tool: " + toolName
            );
        };
    }

    private JsonNode searchProducts(JsonNode args) throws Exception {

        String query = args.path("query").asText("").trim();

        if (query.isBlank() || query.length() > 100) {
            throw new IllegalArgumentException("Invalid product search query");
        }

        String sql = """
                SELECT
                    p.uuid,
                    p.product_code,
                    p.part_no,
                    p.name,
                    p.qty,
                    p.minimum_stock,
                    p.unit,
                    p.purchase_price,
                    p.selling_price
                FROM products p
                WHERE p.active = TRUE
                  AND (
                    LOWER(p.name) LIKE LOWER(:query)
                    OR LOWER(p.product_code) LIKE LOWER(:query)
                    OR LOWER(p.part_no) LIKE LOWER(:query)
                  )
                ORDER BY p.name
                LIMIT 20
                """;

        List<Map<String, Object>> results = jdbc.queryForList(
                sql,
                Map.of("query", "%" + query + "%")
        );

        return objectMapper.valueToTree(results);
    }

    private JsonNode getLowStockProducts() throws Exception {

        String sql = """
                SELECT
                    p.uuid,
                    p.product_code,
                    p.part_no,
                    p.name,
                    p.qty,
                    p.minimum_stock,
                    p.unit
                FROM products p
                WHERE p.active = TRUE
                  AND p.qty < p.minimum_stock
                ORDER BY (p.minimum_stock - p.qty) DESC
                LIMIT 100
                """;

        List<Map<String, Object>> results = jdbc.queryForList(sql, Map.of());

        return objectMapper.valueToTree(results);
    }
}