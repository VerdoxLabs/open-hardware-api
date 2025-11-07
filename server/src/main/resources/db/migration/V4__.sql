-- Add column to RAM table
ALTER TABLE ram
    ADD COLUMN IF NOT EXISTS is_ecc boolean;

-- Backfill
UPDATE ram
SET is_ecc = COALESCE(is_ecc, false)
WHERE is_ecc IS NULL;

-- NOT NULL + Default
ALTER TABLE ram
    ALTER COLUMN is_ecc SET DEFAULT false,
    ALTER COLUMN is_ecc SET NOT NULL;
