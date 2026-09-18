CREATE TABLE hardware_spec_image_attributions (
    spec_id BIGINT NOT NULL,
    local_url VARCHAR(1024) NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    source_page_url VARCHAR(2048) NOT NULL,
    CONSTRAINT fk_hw_spec_image_attributions_on_spec
        FOREIGN KEY (spec_id) REFERENCES hardware_spec (id) ON DELETE CASCADE,
    CONSTRAINT uq_hw_spec_image_attribution_local_url UNIQUE (spec_id, local_url)
);
