DROP TABLE IF EXISTS spec_color;

CREATE TABLE IF NOT EXISTS ram_color (
                                         ram_id BIGINT NOT NULL,
                                         color  VARCHAR(255) NOT NULL,
                                         CONSTRAINT pk_ram_color PRIMARY KEY (ram_id, color),
                                         CONSTRAINT fk_ram_color__ram FOREIGN KEY (ram_id) REFERENCES ram(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ram_color_color ON ram_color(color);

CREATE TABLE IF NOT EXISTS gpu_color (
                                         gpu_id BIGINT NOT NULL,
                                         color  VARCHAR(255) NOT NULL,
                                         CONSTRAINT pk_gpu_color PRIMARY KEY (gpu_id, color),
                                         CONSTRAINT fk_gpu_color__gpu FOREIGN KEY (gpu_id) REFERENCES gpu(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_gpu_color_color ON gpu_color(color);

CREATE TABLE IF NOT EXISTS psu_color (
                                         psu_id BIGINT NOT NULL,
                                         color  VARCHAR(255) NOT NULL,
                                         CONSTRAINT pk_psu_color PRIMARY KEY (psu_id, color),
                                         CONSTRAINT fk_psu_color__psu FOREIGN KEY (psu_id) REFERENCES psu(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_psu_color_color ON psu_color(color);

CREATE TABLE IF NOT EXISTS pccase_color (
                                            pccase_id BIGINT NOT NULL,
                                            color     VARCHAR(255) NOT NULL,
                                            CONSTRAINT pk_pccase_color PRIMARY KEY (pccase_id, color),
                                            CONSTRAINT fk_pccase_color__pccase FOREIGN KEY (pccase_id) REFERENCES pccase(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_pccase_color_color ON pccase_color(color);

CREATE TABLE IF NOT EXISTS motherboard_color (
                                                 motherboard_id BIGINT NOT NULL,
                                                 color          VARCHAR(255) NOT NULL,
                                                 CONSTRAINT pk_motherboard_color PRIMARY KEY (motherboard_id, color),
                                                 CONSTRAINT fk_motherboard_color__motherboard FOREIGN KEY (motherboard_id) REFERENCES motherboard(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_motherboard_color_color ON motherboard_color(color);

CREATE TABLE IF NOT EXISTS cpucooler_color (
                                               cpucooler_id BIGINT NOT NULL,
                                               color        VARCHAR(255) NOT NULL,
                                               CONSTRAINT pk_cpucooler_color PRIMARY KEY (cpucooler_id, color),
                                               CONSTRAINT fk_cpucooler_color__cpucooler FOREIGN KEY (cpucooler_id) REFERENCES cpucooler(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_cpucooler_color_color ON cpucooler_color(color);