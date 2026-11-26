-- V41: allow optional HSN and ensure common GST rates are available for product creation.
ALTER TABLE product_tax_mapping
    ALTER COLUMN hsn_code DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM tax_rates
        WHERE active = TRUE
          AND gst_rate = 0
          AND effective_from <= CURRENT_DATE
          AND (effective_to IS NULL OR effective_to > CURRENT_DATE)
    ) THEN
        INSERT INTO tax_rates (
            uuid, tax_name, gst_rate, cgst_rate, sgst_rate, igst_rate, effective_from
        ) VALUES (
            gen_random_uuid(), 'GST 0%', 0, 0, 0, 0, CURRENT_DATE
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM tax_rates
        WHERE active = TRUE
          AND gst_rate = 18
          AND effective_from <= CURRENT_DATE
          AND (effective_to IS NULL OR effective_to > CURRENT_DATE)
    ) THEN
        INSERT INTO tax_rates (
            uuid, tax_name, gst_rate, cgst_rate, sgst_rate, igst_rate, effective_from
        ) VALUES (
            gen_random_uuid(), 'GST 18%', 18, 9, 9, 18, CURRENT_DATE
        );
    END IF;
END $$;
