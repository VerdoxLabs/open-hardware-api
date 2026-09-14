-- Canonical identifier values. The legacy identifier column remains the raw/source value.
ALTER TABLE product_identifier
    ADD COLUMN IF NOT EXISTS normalized_value VARCHAR(512);

UPDATE product_identifier
SET normalized_value = identifier
WHERE normalized_value IS NULL;

ALTER TABLE product_identifier
    ALTER COLUMN normalized_value SET NOT NULL;

ALTER TABLE product_identifier
    DROP CONSTRAINT IF EXISTS uc_dc04a03d0202df86713febb3b;

CREATE UNIQUE INDEX IF NOT EXISTS ux_product_identifier_type_normalized
    ON product_identifier (type, normalized_value);

CREATE INDEX IF NOT EXISTS idx_product_identifier_normalized_value
    ON product_identifier (normalized_value);
