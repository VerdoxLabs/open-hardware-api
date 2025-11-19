package de.verdox.hwapi.benchmarkapi.entity;

import de.verdox.hwapi.model.HardwareSpec;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "benchmark_result_type")
@Getter
public abstract class BenchmarkResults<HARDWARE extends HardwareSpec<HARDWARE>> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotBlank
    protected String modelName;

    @NotBlank
    protected String source;

    public void setIdentifiers(String source, String modelName) {
        this.source = source;
        this.modelName = modelName;
    }
}
