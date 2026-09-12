-- V16: Datenmodell-Verbesserung
-- Neue Komponente FAN, neue Spec-Felder auf bestehenden Komponenten,
-- logische Catalog-Links (hardware_spec_id) in Preisen/Benchmarks + Hot-Indizes.
--
-- Alle Anweisungen idempotent (IF NOT EXISTS) wie V11/V14, damit die Migration
-- sowohl auf einer frischen als auch einer bestehenden Produktion läuft.
-- Dev (H2 + ddl-auto:update) wird von Hibernate unabhängig versorgt.

-- ============================================================
-- 1) Storage
-- ============================================================
ALTER TABLE storage ADD COLUMN IF NOT EXISTS storage_form_factor VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE storage ADD COLUMN IF NOT EXISTS nand_type VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE storage ADD COLUMN IF NOT EXISTS nvme_version VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE storage ADD COLUMN IF NOT EXISTS read_speed_mbs INTEGER NOT NULL DEFAULT 0;
ALTER TABLE storage ADD COLUMN IF NOT EXISTS write_speed_mbs INTEGER NOT NULL DEFAULT 0;
ALTER TABLE storage ADD COLUMN IF NOT EXISTS has_dram_cache BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================================
-- 2) GPU (Board)
-- ============================================================
ALTER TABLE gpu ADD COLUMN IF NOT EXISTS base_clock_mhz DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE gpu ADD COLUMN IF NOT EXISTS boost_clock_mhz DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE gpu ADD COLUMN IF NOT EXISTS memory_bus_width_bit INTEGER NOT NULL DEFAULT 0;
ALTER TABLE gpu ADD COLUMN IF NOT EXISTS recommended_psu_watts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE gpu ADD COLUMN IF NOT EXISTS slot_width DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE gpu ADD COLUMN IF NOT EXISTS fan_count INTEGER NOT NULL DEFAULT 0;

-- ============================================================
-- 3) GPUChip
-- ============================================================
ALTER TABLE gpuchip ADD COLUMN IF NOT EXISTS architecture VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE gpuchip ADD COLUMN IF NOT EXISTS memory_bus_width_bit INTEGER NOT NULL DEFAULT 0;
ALTER TABLE gpuchip ADD COLUMN IF NOT EXISTS memory_bandwidth_gbps DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE gpuchip ADD COLUMN IF NOT EXISTS boost_clock_mhz DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE gpuchip ADD COLUMN IF NOT EXISTS process_node_nm INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_gpuchip_canonical_model ON gpuchip (canonical_model);

-- ============================================================
-- 4) CPU
-- ============================================================
ALTER TABLE cpu ADD COLUMN IF NOT EXISTS process_node_nm INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cpu ADD COLUMN IF NOT EXISTS memory_bandwidth_gbps DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE cpu ADD COLUMN IF NOT EXISTS memory_channels INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cpu ADD COLUMN IF NOT EXISTS memory_type VARCHAR(255);

-- ============================================================
-- 5) RAM
-- ============================================================
ALTER TABLE ram ADD COLUMN IF NOT EXISTS rank VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE ram ADD COLUMN IF NOT EXISTS profile VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN';

-- ============================================================
-- 6) Display
-- ============================================================
ALTER TABLE display ADD COLUMN IF NOT EXISTS brightness_nits INTEGER NOT NULL DEFAULT 0;
ALTER TABLE display ADD COLUMN IF NOT EXISTS panel_bit_depth INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS display_hdr_standards (
    spec_id      BIGINT      NOT NULL,
    hdr_standard VARCHAR(255),
    CONSTRAINT pk_display_hdr_standards PRIMARY KEY (spec_id, hdr_standard),
    CONSTRAINT fk_display_hdr_standards_on_display FOREIGN KEY (spec_id) REFERENCES display (id) ON DELETE CASCADE
);

-- ============================================================
-- 7) Motherboard
-- ============================================================
ALTER TABLE motherboard ADD COLUMN IF NOT EXISTS wlan_standard VARCHAR(255);
ALTER TABLE motherboard ADD COLUMN IF NOT EXISTS ethernet_speed VARCHAR(255);
ALTER TABLE motherboard ADD COLUMN IF NOT EXISTS has_bluetooth BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE motherboard ADD COLUMN IF NOT EXISTS has_front_usb_c BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS motherboard_display_outputs (
    spec_id     BIGINT      NOT NULL,
    output_type VARCHAR(255),
    CONSTRAINT pk_motherboard_display_outputs PRIMARY KEY (spec_id, output_type),
    CONSTRAINT fk_motherboard_display_outputs_on_motherboard FOREIGN KEY (spec_id) REFERENCES motherboard (id) ON DELETE CASCADE
);

-- ============================================================
-- 8) PSU
-- ============================================================
ALTER TABLE psu ADD COLUMN IF NOT EXISTS rail_12v_current DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE psu ADD COLUMN IF NOT EXISTS is_fanless BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE psu ADD COLUMN IF NOT EXISTS is_8_plus_4_pin BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================================
-- 9) PCCase
-- ============================================================
ALTER TABLE pccase ADD COLUMN IF NOT EXISTS max_aio_radiator_mm INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pccase ADD COLUMN IF NOT EXISTS fan_mounts_front INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pccase ADD COLUMN IF NOT EXISTS fan_mounts_top INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pccase ADD COLUMN IF NOT EXISTS fan_mounts_rear INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pccase ADD COLUMN IF NOT EXISTS has_front_usb_c BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================================
-- 10) CPUCooler
-- ============================================================
ALTER TABLE cpucooler ADD COLUMN IF NOT EXISTS fan_connector_type VARCHAR(255);
ALTER TABLE cpucooler ADD COLUMN IF NOT EXISTS has_rgb BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================================
-- 11) Neue Komponente: FAN
-- ============================================================
CREATE SEQUENCE IF NOT EXISTS fan_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS fan (
    id                       BIGINT           NOT NULL,
    connector_type           VARCHAR(255)     NOT NULL DEFAULT 'UNKNOWN',
    diameter_mm              INTEGER,
    rpm_max                  INTEGER,
    count                    INTEGER,
    airflow_cfm              DOUBLE PRECISION NOT NULL DEFAULT 0,
    static_pressure_mm_h2o   DOUBLE PRECISION NOT NULL DEFAULT 0,
    noise_level_db           DOUBLE PRECISION NOT NULL DEFAULT 0,
    has_rgb                  BOOLEAN          NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_fan PRIMARY KEY (id),
    CONSTRAINT fk_fan_on_id FOREIGN KEY (id) REFERENCES hardware_spec (id)
);

CREATE TABLE IF NOT EXISTS fan_mount_positions (
    spec_id  BIGINT      NOT NULL,
    position VARCHAR(255),
    CONSTRAINT fk_fan_mount_positions_on_fan FOREIGN KEY (spec_id) REFERENCES fan (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_fan_mount_positions_spec_id ON fan_mount_positions (spec_id);

-- ============================================================
-- 12) Logische Catalog-Links (hardware_spec_id) + Hot-Indizes
-- ============================================================
ALTER TABLE remote_active_listing ADD COLUMN IF NOT EXISTS hardware_spec_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_ral_spec_id ON remote_active_listing (hardware_spec_id);

ALTER TABLE remote_sold_item ADD COLUMN IF NOT EXISTS hardware_spec_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_rsi_spec_id ON remote_sold_item (hardware_spec_id);
-- Deckt die Hot-Preis-Queries ab: ean [+currency] + sell_date >= from
CREATE INDEX IF NOT EXISTS idx_rsi_ean_currency_sell_date ON remote_sold_item (ean, currency, sell_date);

-- Benchmark → Catalog-Link + model_name-Index (fuzzy-Lookup)
ALTER TABLE benchmark_results ADD COLUMN IF NOT EXISTS hardware_spec_id BIGINT;
ALTER TABLE benchmark_results ALTER COLUMN model_name TYPE VARCHAR(512);
CREATE INDEX IF NOT EXISTS idx_benchmark_model_name ON benchmark_results (model_name);
CREATE INDEX IF NOT EXISTS idx_benchmark_spec_id ON benchmark_results (hardware_spec_id);
