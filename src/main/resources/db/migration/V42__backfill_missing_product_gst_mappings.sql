-- V42: ensure every existing active product has a tax mapping for the current date.
-- Preserve all historical mappings; only products without a current mapping receive GST 18%.
INSERT INTO product_tax_mapping (
    uuid, product_id, tax_rate_id, hsn_code, effective_from, effective_to
)
SELECT
    gen_random_uuid(),
    p.id,
    tr.id,
    NULL,
    CURRENT_DATE,
    NULL
FROM products p
CROSS JOIN LATERAL (
    SELECT id
    FROM tax_rates
    WHERE active = TRUE
      AND gst_rate = 18.00
      AND effective_from <= CURRENT_DATE
      AND (effective_to IS NULL OR effective_to > CURRENT_DATE)
    ORDER BY effective_from DESC, id DESC
    LIMIT 1
) tr
WHERE p.active = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM product_tax_mapping ptm
      JOIN tax_rates existing_rate ON existing_rate.id = ptm.tax_rate_id
      WHERE ptm.product_id = p.id
        AND ptm.effective_from <= CURRENT_DATE
        AND (ptm.effective_to IS NULL OR ptm.effective_to > CURRENT_DATE)
        AND existing_rate.active = TRUE
        AND existing_rate.effective_from <= CURRENT_DATE
        AND (existing_rate.effective_to IS NULL OR existing_rate.effective_to > CURRENT_DATE)
  );
