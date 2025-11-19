CREATE SEQUENCE IF NOT EXISTS benchmark_results_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS cpu_benchmark_results_seq START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE IF NOT EXISTS gpu_benchmark_results_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE benchmark_results
(
    id                    BIGINT      NOT NULL,
    benchmark_result_type VARCHAR(31) NOT NULL,
    model_name            VARCHAR(255),
    source                VARCHAR(255),
    CONSTRAINT pk_benchmarkresults PRIMARY KEY (id)
);

CREATE TABLE cpu_benchmark_results
(
    id                BIGINT           NOT NULL,
    cpu_mark_score    DOUBLE PRECISION NOT NULL,
    thread_mark_score DOUBLE PRECISION NOT NULL,
    CONSTRAINT pk_cpu_benchmark_results PRIMARY KEY (id)
);

CREATE TABLE gpu_benchmark_results
(
    id            BIGINT           NOT NULL,
    g3dmark_score DOUBLE PRECISION NOT NULL,
    g2dmark_score DOUBLE PRECISION NOT NULL,
    CONSTRAINT pk_gpu_benchmark_results PRIMARY KEY (id)
);


ALTER TABLE cpu_benchmark_results
    ADD CONSTRAINT FK_CPU_BENCHMARK_RESULTS_ON_ID FOREIGN KEY (id) REFERENCES benchmark_results (id);

ALTER TABLE gpu_benchmark_results
    ADD CONSTRAINT FK_GPU_BENCHMARK_RESULTS_ON_ID FOREIGN KEY (id) REFERENCES benchmark_results (id);

CREATE TABLE hardware_spec_picture_urls (
                                            spec_id      BIGINT      NOT NULL,
                                            url          VARCHAR(1024) NOT NULL,

                                            CONSTRAINT fk_hw_spec_picture_urls_on_spec
                                                FOREIGN KEY (spec_id)
                                                    REFERENCES hardware_spec (id)
                                                    ON DELETE CASCADE
);

CREATE INDEX idx_hw_spec_picture_urls_spec_id
    ON hardware_spec_picture_urls (spec_id);