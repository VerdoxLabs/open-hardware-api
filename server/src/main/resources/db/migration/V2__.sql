CREATE INDEX idx_rsi_ean_curr_date_price
    ON remote_sold_item (ean, currency, sell_date, sell_price);