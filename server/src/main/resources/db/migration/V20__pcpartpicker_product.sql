CREATE TABLE pcpartpicker_product (
    id BIGINT NOT NULL PRIMARY KEY,
    category VARCHAR(255) NOT NULL,
    CONSTRAINT fk_pcpartpicker_product_id FOREIGN KEY (id) REFERENCES hardware_spec (id)
);

CREATE TABLE pcpartpicker_product_spec (
    spec_id BIGINT NOT NULL,
    spec_key VARCHAR(255) NOT NULL,
    spec_value VARCHAR(4000) NOT NULL,
    PRIMARY KEY (spec_id, spec_key),
    CONSTRAINT fk_pcpartpicker_product_spec_id FOREIGN KEY (spec_id) REFERENCES pcpartpicker_product (id) ON DELETE CASCADE
);
