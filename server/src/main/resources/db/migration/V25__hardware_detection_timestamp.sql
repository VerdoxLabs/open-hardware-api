ALTER TABLE hardware_spec ADD COLUMN IF NOT EXISTS detected_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_hardware_spec_detected_at ON hardware_spec (detected_at);
