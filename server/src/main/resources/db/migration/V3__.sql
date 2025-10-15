ALTER TABLE hardware_spec
    ADD CONSTRAINT uc_hardwarespec_ean UNIQUE (ean);

ALTER TABLE hardware_spec
DROP
COLUMN upc;

ALTER TABLE hardware_spec
ALTER
COLUMN ean TYPE VARCHAR(14) USING (ean::VARCHAR(14));