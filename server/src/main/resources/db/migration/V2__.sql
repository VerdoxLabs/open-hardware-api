CREATE SEQUENCE IF NOT EXISTS remote_sold_item_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE remote_sold_item
(
    id                  BIGINT NOT NULL,
    market_place_domain VARCHAR(255),
    market_place_itemid VARCHAR(255),
    ean                 VARCHAR(255),
    sell_price          DECIMAL,
    currency            VARCHAR(255),
    sell_date           date,
    CONSTRAINT pk_remotesolditem PRIMARY KEY (id)
);

CREATE INDEX idx_rsi_ean ON remote_sold_item (ean);

CREATE INDEX idx_rsi_ean_sellprice ON remote_sold_item (ean, sell_price);