-- Neue Spalte für GPU
ALTER TABLE gpu
    ADD COLUMN gpu_canonical_name VARCHAR(255);

-- Bestehende Datensätze auf 'unknown' setzen
UPDATE gpu
SET gpu_canonical_name = 'unknown'
WHERE gpu_canonical_name IS NULL;

-- Spalte als NOT NULL markieren
ALTER TABLE gpu
    ALTER COLUMN gpu_canonical_name SET NOT NULL;

-- (Optional) Index für schnellere Suche
CREATE INDEX IF NOT EXISTS idx_gpu_canonical_name
    ON gpu (gpu_canonical_name);