ALTER TABLE remote_active_listing ALTER COLUMN primary_region SET DEFAULT 'DE';

UPDATE remote_active_listing SET primary_region = 'DE' WHERE primary_region IS NULL OR primary_region = '';