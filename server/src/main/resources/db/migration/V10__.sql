CREATE TABLE IF NOT EXISTS spec_color (
                                          spec_id BIGINT NOT NULL,
                                          color   VARCHAR(255) NOT NULL,
                                          CONSTRAINT pk_spec_color PRIMARY KEY (spec_id, color)
);

CREATE INDEX IF NOT EXISTS idx_spec_color_color ON spec_color(color);

ALTER TABLE cpucooler DROP COLUMN colors;
ALTER TABLE motherboard DROP COLUMN colors;
ALTER TABLE pccase DROP COLUMN colors;
ALTER TABLE psu DROP COLUMN colors;
ALTER TABLE ram DROP COLUMN colors;
ALTER TABLE gpu DROP COLUMN colors;