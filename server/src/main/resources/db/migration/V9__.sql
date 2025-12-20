ALTER TABLE remote_active_listing_price ADD COLUMN IF NOT EXISTS currency VARCHAR(255);

ALTER TABLE remote_active_listing_price ADD COLUMN IF NOT EXISTS shipping_price NUMERIC(18, 2);

ALTER TABLE remote_active_listing_price ADD COLUMN IF NOT EXISTS available_quantity INTEGER;

ALTER TABLE remote_active_listing_price ALTER COLUMN condition TYPE VARCHAR(64);