package de.verdox.hwapi.benchmarkapi.entity;

import de.verdox.hwapi.model.HardwareSpec;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "benchmark_result_type")
@Getter
@Setter
public abstract class BenchmarkResults<HARDWARE extends HardwareSpec<HARDWARE>> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotBlank
    @Column(name = "model_name", length = 512)
    protected String modelName;

    @NotBlank
    @Column(name = "source")
    protected String source;

    /**
     * Logischer Verweis auf die zugehörige HardwareSpec (Catalog) – bewusst
     * ohne FK, da der Link über modelName (fuzzy) erfolgt und der Katalog
     * unabhängig verwaltet wird. Wird nach erfolgreichem Match gesetzt.
     */
    @Column(name = "hardware_spec_id")
    private Long hardwareSpecId;

    public void setIdentifiers(String source, String modelName) {
        this.source = source;
        this.modelName = modelName;
    }
}
