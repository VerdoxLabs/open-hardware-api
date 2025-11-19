ALTER TABLE remote_sold_item
    ADD COLUMN condition VARCHAR(255);

-- Optional: bestehende Daten standardmäßig als "USED" markieren
UPDATE remote_sold_item
SET condition = 'USED'
WHERE condition IS NULL;


CREATE TABLE remote_active_listing (
                                       uuid                UUID            NOT NULL,
                                       market_place_domain VARCHAR(255)    NOT NULL,
                                       market_place_item_id VARCHAR(255)   NOT NULL,
                                       ean                 VARCHAR(255),
                                       mpn                 VARCHAR(255),
                                       title               VARCHAR(512),
                                       item_url            TEXT,
                                       price               NUMERIC(18,2),
                                       currency            VARCHAR(255),
                                       condition           VARCHAR(255),
                                       still_active        BOOLEAN         NOT NULL DEFAULT TRUE,
                                       shipping_price      NUMERIC(18,2),
                                       available_quantity  INTEGER,
                                       first_seen_at       TIMESTAMP,
                                       last_seen_at        TIMESTAMP,
                                       CONSTRAINT pk_remote_active_listing PRIMARY KEY (uuid)
);

-- Eindeutig pro Marktplatz + Listing-ID
ALTER TABLE remote_active_listing
    ADD CONSTRAINT ux_ral_marketplace_item
        UNIQUE (market_place_domain, market_place_item_id);

-- Schnelle Suchen/Filter
CREATE INDEX idx_ral_ean
    ON remote_active_listing (ean);

CREATE INDEX idx_ral_mpn
    ON remote_active_listing (mpn);

CREATE INDEX idx_ral_ean_price
    ON remote_active_listing (ean, price);



CREATE TABLE price_lookup_block (
                                    id             BIGSERIAL       NOT NULL,
                                    ean            VARCHAR(255)     NOT NULL,
                                    currency       VARCHAR(255)     NOT NULL,
                                    blocked_until  TIMESTAMP       NOT NULL,
                                    CONSTRAINT pk_price_lookup_block PRIMARY KEY (id)
);

-- Jede (ean, currency)-Kombination nur einmal
ALTER TABLE price_lookup_block
    ADD CONSTRAINT ux_price_lookup_ean_currency
        UNIQUE (ean, currency);

-- Optional: Index für Aufräumjobs / Queries nach Ablauf
CREATE INDEX idx_price_lookup_block_blocked_until
    ON price_lookup_block (blocked_until);