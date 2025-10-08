CREATE SEQUENCE IF NOT EXISTS cpu_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS cpucooler_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS display_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS gpu_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS gpuchip_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS hardware_spec_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS motherboard_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS pccase_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS psu_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS ram_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS storage_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE cooler_supported_sockets
(
    spec_id BIGINT NOT NULL,
    socket  VARCHAR(255)
);

CREATE TABLE cpu
(
    id                         BIGINT           NOT NULL,
    socket                     VARCHAR(255)     NOT NULL,
    integrated_graphics        VARCHAR(255),
    cores                      INTEGER          NOT NULL,
    efficiency_cores           INTEGER          NOT NULL,
    performance_cores          INTEGER          NOT NULL,
    threads                    INTEGER          NOT NULL,
    base_clock_mhz             DOUBLE PRECISION NOT NULL,
    base_clock_mhz_efficiency  DOUBLE PRECISION NOT NULL,
    base_clock_mhz_performance DOUBLE PRECISION NOT NULL,
    boost_clock_mhz            DOUBLE PRECISION NOT NULL,
    l3cache_mb                 INTEGER          NOT NULL,
    tdp_watts                  INTEGER          NOT NULL,
    CONSTRAINT pk_cpu PRIMARY KEY (id)
);

CREATE TABLE cpucooler
(
    id                 BIGINT           NOT NULL,
    type               VARCHAR(255),
    radiator_length_mm DOUBLE PRECISION NOT NULL,
    tdp_watts          INTEGER,
    CONSTRAINT pk_cpucooler PRIMARY KEY (id)
);

CREATE TABLE display
(
    id                  BIGINT NOT NULL,
    refresh_rate        INTEGER,
    display_panel       VARCHAR(255),
    hdmi_ports          INTEGER,
    display_ports       INTEGER,
    dvi_ports           INTEGER,
    vga_ports           INTEGER,
    response_timems     INTEGER,
    inch_size           DOUBLE PRECISION,
    res_width           INTEGER,
    res_height          INTEGER,
    integrated_speakers BOOLEAN,
    curved              BOOLEAN,
    adjustable_size     BOOLEAN,
    CONSTRAINT pk_display PRIMARY KEY (id)
);

CREATE TABLE display_sync
(
    spec_id       BIGINT NOT NULL,
    display_syncs VARCHAR(255)
);

CREATE TABLE gpu
(
    id           BIGINT           NOT NULL,
    length_mm    DOUBLE PRECISION NOT NULL,
    gpu_chip_id  BIGINT,
    pcie_version VARCHAR(255),
    vram_type    VARCHAR(255),
    vram_gb      DOUBLE PRECISION NOT NULL,
    tdp          DOUBLE PRECISION NOT NULL,
    CONSTRAINT pk_gpu PRIMARY KEY (id)
);

CREATE TABLE gpu_power_connectors
(
    spec_id  BIGINT       NOT NULL,
    type     VARCHAR(255) NOT NULL,
    quantity INTEGER
);

CREATE TABLE gpuchip
(
    id              BIGINT           NOT NULL,
    canonical_model VARCHAR(255),
    pcie_version    VARCHAR(255),
    vram_type       VARCHAR(255),
    vram_gb         DOUBLE PRECISION NOT NULL,
    length_mm       DOUBLE PRECISION NOT NULL,
    tdp             DOUBLE PRECISION NOT NULL,
    CONSTRAINT pk_gpuchip PRIMARY KEY (id)
);

CREATE TABLE hardware_spec
(
    id           BIGINT      NOT NULL,
    spec_type    VARCHAR(31) NOT NULL,
    manufacturer VARCHAR(255),
    model        VARCHAR(255),
    ean          VARCHAR(255),
    mpn          VARCHAR(255),
    upc          VARCHAR(255),
    launch_date  date,
    CONSTRAINT pk_hardwarespec PRIMARY KEY (id)
);

CREATE TABLE hardware_spec_attributes
(
    spec_id    BIGINT       NOT NULL,
    attr_value VARCHAR(2000),
    attr_key   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_hardware_spec_attributes PRIMARY KEY (spec_id, attr_key)
);

CREATE TABLE hardware_spec_tags
(
    spec_id BIGINT NOT NULL,
    tag     VARCHAR(255)
);

CREATE TABLE m2slots
(
    spec_id             BIGINT NOT NULL,
    pcie_version        VARCHAR(255),
    supported_interface VARCHAR(255),
    quantity            INTEGER
);

CREATE TABLE mb_case_support
(
    spec_id     BIGINT NOT NULL,
    form_factor VARCHAR(255)
);

CREATE TABLE motherboard
(
    id           BIGINT       NOT NULL,
    socket       VARCHAR(255) NOT NULL,
    chipset      VARCHAR(255) NOT NULL,
    form_factor  VARCHAR(255) NOT NULL,
    ram_type     VARCHAR(255) NOT NULL,
    ram_slots    INTEGER,
    ram_capacity INTEGER,
    sata_slots   INTEGER,
    usb3headers  INTEGER      NOT NULL,
    CONSTRAINT pk_motherboard PRIMARY KEY (id)
);

CREATE TABLE pccase
(
    id                       BIGINT           NOT NULL,
    size_class               VARCHAR(255),
    max_gpu_length_mm        DOUBLE PRECISION NOT NULL,
    max_cpu_cooler_height_mm DOUBLE PRECISION NOT NULL,
    width                    DOUBLE PRECISION,
    height                   DOUBLE PRECISION,
    depth                    DOUBLE PRECISION,
    CONSTRAINT pk_pccase PRIMARY KEY (id)
);

CREATE TABLE pcie_slots
(
    spec_id  BIGINT NOT NULL,
    version  VARCHAR(255),
    lanes    INTEGER,
    quantity INTEGER
);

CREATE TABLE psu
(
    id                BIGINT  NOT NULL,
    wattage           INTEGER NOT NULL,
    efficiency_rating VARCHAR(255),
    modularity        VARCHAR(255),
    size              VARCHAR(255),
    CONSTRAINT pk_psu PRIMARY KEY (id)
);

CREATE TABLE psu_connectors
(
    spec_id  BIGINT       NOT NULL,
    type     VARCHAR(255) NOT NULL,
    quantity INTEGER
);

CREATE TABLE ram
(
    id                                  BIGINT       NOT NULL,
    type                                VARCHAR(255) NOT NULL,
    sticks                              INTEGER      NOT NULL,
    size_gb                             INTEGER      NOT NULL,
    speed_mtps                          INTEGER,
    cas_latency                         INTEGER,
    row_address_to_column_address_delay INTEGER,
    row_precharge_time                  INTEGER,
    row_active_time                     INTEGER,
    CONSTRAINT pk_ram PRIMARY KEY (id)
);

CREATE TABLE storage
(
    id                BIGINT       NOT NULL,
    storage_type      VARCHAR(255) NOT NULL,
    storage_interface VARCHAR(255) NOT NULL,
    capacity_gb       INTEGER      NOT NULL,
    CONSTRAINT pk_storage PRIMARY KEY (id)
);

CREATE TABLE usb_port
(
    spec_id  BIGINT NOT NULL,
    type     VARCHAR(255),
    version  VARCHAR(255),
    quantity INTEGER
);

ALTER TABLE cpucooler
    ADD CONSTRAINT FK_CPUCOOLER_ON_ID FOREIGN KEY (id) REFERENCES hardware_spec (id);

ALTER TABLE cpu
    ADD CONSTRAINT FK_CPU_ON_ID FOREIGN KEY (id) REFERENCES hardware_spec (id);

ALTER TABLE display
    ADD CONSTRAINT FK_DISPLAY_ON_ID FOREIGN KEY (id) REFERENCES hardware_spec (id);

ALTER TABLE gpuchip
    ADD CONSTRAINT FK_GPUCHIP_ON_ID FOREIGN KEY (id) REFERENCES hardware_spec (id);

ALTER TABLE gpu
    ADD CONSTRAINT FK_GPU_ON_ID FOREIGN KEY (id) REFERENCES hardware_spec (id);

ALTER TABLE gpu
    ADD CONSTRAINT FK_GPU__CHIP FOREIGN KEY (gpu_chip_id) REFERENCES gpuchip (id);

ALTER TABLE motherboard
    ADD CONSTRAINT FK_MOTHERBOARD_ON_ID FOREIGN KEY (id) REFERENCES hardware_spec (id);

ALTER TABLE pccase
    ADD CONSTRAINT FK_PCCASE_ON_ID FOREIGN KEY (id) REFERENCES hardware_spec (id);

ALTER TABLE psu
    ADD CONSTRAINT FK_PSU_ON_ID FOREIGN KEY (id) REFERENCES hardware_spec (id);

ALTER TABLE ram
    ADD CONSTRAINT FK_RAM_ON_ID FOREIGN KEY (id) REFERENCES hardware_spec (id);

ALTER TABLE storage
    ADD CONSTRAINT FK_STORAGE_ON_ID FOREIGN KEY (id) REFERENCES hardware_spec (id);

ALTER TABLE cooler_supported_sockets
    ADD CONSTRAINT fk_cooler_supported_sockets_on_c_p_u_cooler FOREIGN KEY (spec_id) REFERENCES cpucooler (id);

ALTER TABLE display_sync
    ADD CONSTRAINT fk_display_sync_on_display FOREIGN KEY (spec_id) REFERENCES display (id);

ALTER TABLE gpu_power_connectors
    ADD CONSTRAINT fk_gpu_power_connectors_on_g_p_u_chip FOREIGN KEY (spec_id) REFERENCES gpuchip (id);

ALTER TABLE hardware_spec_attributes
    ADD CONSTRAINT fk_hardware_spec_attributes_on_g_p_u_chip FOREIGN KEY (spec_id) REFERENCES gpuchip (id);

ALTER TABLE hardware_spec_tags
    ADD CONSTRAINT fk_hardware_spec_tags_on_g_p_u_chip FOREIGN KEY (spec_id) REFERENCES gpuchip (id);

ALTER TABLE m2slots
    ADD CONSTRAINT fk_m2slots_on_motherboard FOREIGN KEY (spec_id) REFERENCES motherboard (id);

ALTER TABLE mb_case_support
    ADD CONSTRAINT fk_mb_case_support_on_p_c_case FOREIGN KEY (spec_id) REFERENCES pccase (id);

ALTER TABLE pcie_slots
    ADD CONSTRAINT fk_pcieslots_on_motherboard FOREIGN KEY (spec_id) REFERENCES motherboard (id);

ALTER TABLE psu_connectors
    ADD CONSTRAINT fk_psu_connectors_on_p_s_u FOREIGN KEY (spec_id) REFERENCES psu (id);

ALTER TABLE usb_port
    ADD CONSTRAINT fk_usbport_on_motherboard FOREIGN KEY (spec_id) REFERENCES motherboard (id);