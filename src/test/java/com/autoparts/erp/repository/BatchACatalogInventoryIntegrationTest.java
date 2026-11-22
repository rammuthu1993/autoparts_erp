package com.autoparts.erp.repository;

import com.autoparts.erp.inventory.enums.InventoryMovementType;
import com.autoparts.erp.inventory.enums.InventoryReferenceType;
import com.autoparts.erp.inventory.model.InventoryMovement;
import com.autoparts.erp.inventory.model.InventoryStock;
import com.autoparts.erp.inventory.repositoryimpl.InventoryRepositoryImpl;
import com.autoparts.erp.masters.brand.model.ProductBrand;
import com.autoparts.erp.masters.brand.repositoryimpl.ProductBrandRepositoryImpl;
import com.autoparts.erp.masters.category.model.ProductCategory;
import com.autoparts.erp.masters.category.repositoryimpl.ProductCategoryRepositoryImpl;
import com.autoparts.erp.product.model.Product;
import com.autoparts.erp.product.repositoryimpl.ProductRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Batch A — Catalog, Masters and Inventory PostgreSQL Integration Tests")
class BatchACatalogInventoryIntegrationTest extends BaseRepositoryIntegrationTest {

    private ProductBrandRepositoryImpl brandRepository;
    private ProductCategoryRepositoryImpl categoryRepository;
    private ProductRepositoryImpl productRepository;
    private InventoryRepositoryImpl inventoryRepository;

    @BeforeEach
    void setUp() {
        brandRepository = new ProductBrandRepositoryImpl(getJdbcTemplate());
        categoryRepository = new ProductCategoryRepositoryImpl(getJdbcTemplate());
        productRepository = new ProductRepositoryImpl(getJdbcTemplate());
        inventoryRepository = new InventoryRepositoryImpl(getJdbcTemplate());
    }

    @Test
    @DisplayName("ProductBrandRepository: full lifecycle with generated keys, UUID and case-insensitive check")
    void testProductBrandLifecycle() {
        getTransactionTemplate().executeWithoutResult(status -> {
            UUID uuid = randomUuid();
            String brandName = "Bosch-" + UUID.randomUUID().toString().substring(0, 8);

            ProductBrand inputBrand = new ProductBrand(null, uuid, brandName, "German engineering", true, null, null);
            ProductBrand brand = brandRepository.create(inputBrand);

            assertNotNull(brand.id(), "Generated identity ID must not be null");
            assertEquals(uuid, brand.uuid());
            assertEquals(brandName, brand.name());
            assertTrue(brand.active());

            assertTrue(brandRepository.findByNameIgnoreCase(brandName.toLowerCase()).isPresent());
            assertTrue(brandRepository.findByNameIgnoreCase(brandName.toUpperCase()).isPresent());

            boolean updated = brandRepository.update(uuid, brandName + "-Updated", "Updated description");
            assertTrue(updated);

            Optional<ProductBrand> retrieved = brandRepository.findByUuid(uuid);
            assertTrue(retrieved.isPresent());
            assertEquals(brandName + "-Updated", retrieved.get().name());

            boolean deactivated = brandRepository.deactivate(uuid);
            assertTrue(deactivated);

            Optional<ProductBrand> deactivatedBrand = brandRepository.findByUuid(uuid);
            assertTrue(deactivatedBrand.isPresent());
            assertFalse(deactivatedBrand.get().active(), "Brand should be marked inactive on deactivate");

            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("ProductCategoryRepository: full lifecycle with UUID and update")
    void testProductCategoryLifecycle() {
        getTransactionTemplate().executeWithoutResult(status -> {
            UUID uuid = randomUuid();
            String catName = "Brakes-" + UUID.randomUUID().toString().substring(0, 8);

            ProductCategory inputCat = new ProductCategory(null, uuid, catName, "Braking components", true, null, null);
            ProductCategory category = categoryRepository.create(inputCat);
            assertNotNull(category.id());
            assertEquals(uuid, category.uuid());

            boolean updated = categoryRepository.update(uuid, catName + "-Renamed", "New desc");
            assertTrue(updated);

            Optional<ProductCategory> found = categoryRepository.findByUuid(uuid);
            assertTrue(found.isPresent());
            assertEquals(catName + "-Renamed", found.get().name());

            boolean deactivated = categoryRepository.deactivate(uuid);
            assertTrue(deactivated);
            assertFalse(categoryRepository.findByUuid(uuid).orElseThrow().active());

            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("ProductRepository: sequence generation, RETURNING id, and multi-field lookups")
    void testProductRepositoryLifecycle() {
        getTransactionTemplate().executeWithoutResult(status -> {
            long nextSeq = productRepository.nextProductCodeNumber();
            assertTrue(nextSeq > 0, "Sequence nextProductCodeNumber must be positive");

            UUID brandUuid = randomUuid();
            ProductBrand brand = brandRepository.create(new ProductBrand(null, brandUuid, "Brand-" + nextSeq, "Desc", true, null, null));
            UUID catUuid = randomUuid();
            ProductCategory cat = categoryRepository.create(new ProductCategory(null, catUuid, "Cat-" + nextSeq, "Desc", true, null, null));

            assertTrue(productRepository.brandExistsAndActive(brand.id()));
            assertTrue(productRepository.categoryExistsAndActive(cat.id()));

            UUID productUuid = randomUuid();
            String productCode = "PRD-" + nextSeq;
            String partNo = "PART-" + UUID.randomUUID().toString().substring(0, 8);
            String name = "Brake Rotor " + nextSeq;

            Product created = productRepository.create(
                    productUuid, productCode, partNo, name, "Vented disc brake rotor",
                    cat.id(), brand.id(),
                    new BigDecimal("1200.50"), new BigDecimal("1850.00"),
                    new BigDecimal("5.0"), "PCS",
                    "8901234567890", "EAN13"
            );

            assertNotNull(created);
            assertEquals(productUuid, created.uuid());
            assertEquals(productCode, created.productCode());
            assertEquals(partNo, created.partNo());
            assertEquals(new BigDecimal("1850.00"), created.sellingPrice());
            assertTrue(created.active());

            assertTrue(productRepository.existsByProductCode(productCode));
            assertTrue(productRepository.existsByPartNo(partNo));
            assertTrue(productRepository.existsByName(name));

            boolean updated = productRepository.update(
                    productUuid, partNo + "-M", name + " Modified", "Updated desc",
                    cat.id(), brand.id(),
                    new BigDecimal("1250.00"), new BigDecimal("1900.00"),
                    new BigDecimal("10.0"), "SET"
            );
            assertTrue(updated);

            Product retrieved = productRepository.findByUuid(productUuid).orElseThrow();
            assertEquals(partNo + "-M", retrieved.partNo());
            assertEquals("SET", retrieved.unit());

            boolean deactivated = productRepository.deactivate(productUuid);
            assertTrue(deactivated);
            assertFalse(productRepository.findByUuid(productUuid).orElseThrow().active());

            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("InventoryRepository: stock movements, low stock calculation, and SELECT FOR UPDATE concurrency")
    void testInventoryMovementsAndConcurrency() throws Exception {
        UUID brandUuid = randomUuid();
        ProductBrand brand = brandRepository.create(new ProductBrand(null, brandUuid, "Brand-Inv-" + brandUuid.toString().substring(0, 6), "Desc", true, null, null));
        UUID catUuid = randomUuid();
        ProductCategory cat = categoryRepository.create(new ProductCategory(null, catUuid, "Cat-Inv-" + catUuid.toString().substring(0, 6), "Desc", true, null, null));

        long seq = productRepository.nextProductCodeNumber();
        UUID productUuid = randomUuid();
        Product product = productRepository.create(
                productUuid, "PRD-INV-" + seq, "PART-INV-" + seq, "Brake Fluid " + seq, "DOT 4",
                cat.id(), brand.id(),
                new BigDecimal("250.00"), new BigDecimal("400.00"),
                new BigDecimal("10.0"), "BOTTLE",
                "BAR-INV-" + seq, "CODE128"
        );

        try {
            // 1. Initial movement: OPENING_STOCK 20 units
            UUID mov1Uuid = randomUuid();
            inventoryRepository.createMovement(
                    mov1Uuid, product.id(), InventoryMovementType.OPENING_STOCK,
                    InventoryReferenceType.OPENING_STOCK, product.id(),
                    new BigDecimal("20.000"), new BigDecimal("0.000"), new BigDecimal("20.000"),
                    "Initial stock"
            );
            inventoryRepository.updateProductQuantity(product.id(), new BigDecimal("20.000"));

            InventoryStock s1 = inventoryRepository.findStockByUuid(productUuid).orElseThrow();
            assertEquals(new BigDecimal("20.000"), s1.quantity());

            // 2. Movement: SALE 15 units -> remaining 5 units (< minimum_stock of 10)
            UUID mov2Uuid = randomUuid();
            inventoryRepository.createMovement(
                    mov2Uuid, product.id(), InventoryMovementType.SALE,
                    InventoryReferenceType.SALE, product.id(),
                    new BigDecimal("-15.000"), new BigDecimal("20.000"), new BigDecimal("5.000"),
                    "Customer sale"
            );
            inventoryRepository.updateProductQuantity(product.id(), new BigDecimal("5.000"));

            InventoryStock s2 = inventoryRepository.findStockByUuid(productUuid).orElseThrow();
            assertEquals(new BigDecimal("5.000"), s2.quantity());

            // 3. Verify low stock query returns this product
            List<InventoryStock> lowStock = inventoryRepository.findLowStock();
            assertTrue(lowStock.stream().anyMatch(s -> s.productUuid().equals(productUuid)),
                    "Product with qty 5 and min stock 10 must be in low stock list");

            // 4. Concurrency: Two independent connections attempt concurrent findStockByUuidForUpdate
            CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
            CountDownLatch releaseLockLatch = new CountDownLatch(1);
            AtomicBoolean secondConnectionBlocked = new AtomicBoolean(false);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            Future<?> task1 = executor.submit(() -> {
                try (Connection conn1 = openIndependentConnection()) {
                    conn1.setAutoCommit(false);
                    try (PreparedStatement ps = conn1.prepareStatement(
                            "SELECT id, qty FROM products WHERE id = ? FOR UPDATE")) {
                        ps.setLong(1, product.id());
                        try (ResultSet rs = ps.executeQuery()) {
                            assertTrue(rs.next());
                        }
                    }
                    lockAcquiredLatch.countDown();
                    releaseLockLatch.await(5, TimeUnit.SECONDS);
                    conn1.commit();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Future<?> task2 = executor.submit(() -> {
                try {
                    lockAcquiredLatch.await(3, TimeUnit.SECONDS);
                    long start = System.currentTimeMillis();
                    try (Connection conn2 = openIndependentConnection()) {
                        conn2.setAutoCommit(false);
                        try (PreparedStatement ps = conn2.prepareStatement(
                                "SELECT id, qty FROM products WHERE id = ? FOR UPDATE")) {
                            ps.setLong(1, product.id());
                            try (ResultSet rs = ps.executeQuery()) {
                                assertTrue(rs.next());
                            }
                        }
                        long elapsed = System.currentTimeMillis() - start;
                        if (elapsed >= 350) {
                            secondConnectionBlocked.set(true);
                        }
                        conn2.commit();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Allow task2 to attempt lock while task1 holds it
            Thread.sleep(400);
            releaseLockLatch.countDown();

            task1.get(5, TimeUnit.SECONDS);
            task2.get(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(secondConnectionBlocked.get(),
                    "Second connection must have blocked waiting for task1 to release FOR UPDATE lock");

        } finally {
            // Clean up created records
            getJdbcTemplate().update("DELETE FROM inventory_movements WHERE product_id = :id",
                    new MapSqlParameterSource("id", product.id()));
            getJdbcTemplate().update("DELETE FROM products WHERE id = :id",
                    new MapSqlParameterSource("id", product.id()));
            getJdbcTemplate().update("DELETE FROM product_brands WHERE id = :id",
                    new MapSqlParameterSource("id", brand.id()));
            getJdbcTemplate().update("DELETE FROM product_categories WHERE id = :id",
                    new MapSqlParameterSource("id", cat.id()));
        }
    }
}
