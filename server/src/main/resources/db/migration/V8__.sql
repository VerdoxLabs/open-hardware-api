CREATE SEQUENCE IF NOT EXISTS product_identifier_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS product_identity_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE product_identifier
(
    id          BIGINT       NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    identifier  VARCHAR(512) NOT NULL,
    identity_id BIGINT,
    CONSTRAINT pk_product_identifier PRIMARY KEY (id)
);

CREATE TABLE product_identifier_sources
(
    identifier_id BIGINT NOT NULL,
    source        VARCHAR(255)
);

CREATE TABLE product_identity
(
    id BIGINT NOT NULL,
    CONSTRAINT pk_product_identity PRIMARY KEY (id)
);

ALTER TABLE product_identifier
    ADD CONSTRAINT uc_dc04a03d0202df86713febb3b UNIQUE (type, identifier);

CREATE INDEX idx_dc04a03d0202df86713febb3b ON product_identifier (type, identifier);

CREATE INDEX idx_e5b63caf2f8a1a5dc32289ea1 ON product_identifier (identifier);

CREATE INDEX idx_f6b64d4030f69bb40794d058e ON product_identifier (type);

ALTER TABLE product_identifier
    ADD CONSTRAINT FK_PRODUCT_IDENTIFIER_ON_IDENTITY FOREIGN KEY (identity_id) REFERENCES product_identity (id);

ALTER TABLE product_identifier_sources
    ADD CONSTRAINT fk_product_identifier_sources_on_product_identifier FOREIGN KEY (identifier_id) REFERENCES product_identifier (id);