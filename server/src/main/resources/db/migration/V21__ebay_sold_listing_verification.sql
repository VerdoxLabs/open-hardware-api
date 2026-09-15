ALTER TABLE remote_sold_item
    ADD COLUMN IF NOT EXISTS listing_title VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS matched_ean VARCHAR(14),
    ADD COLUMN IF NOT EXISTS matched_mpn VARCHAR(512),
    ADD COLUMN IF NOT EXISTS ebay_match_status VARCHAR(32) NOT NULL DEFAULT 'HIGH_CONFIDENCE',
    ADD COLUMN IF NOT EXISTS ebay_match_reason VARCHAR(1024);

CREATE INDEX IF NOT EXISTS idx_rsi_verified_prices
    ON remote_sold_item (ean, ebay_match_status, sell_date);
