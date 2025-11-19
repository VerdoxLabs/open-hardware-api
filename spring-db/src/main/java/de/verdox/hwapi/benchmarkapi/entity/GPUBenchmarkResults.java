package de.verdox.hwapi.benchmarkapi.entity;

import de.verdox.hwapi.model.GPU;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("GPU")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "gpu_benchmark_results",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source", "model_name"})
)
public class GPUBenchmarkResults extends BenchmarkResults<GPU> {
    private double g3DMarkScore = 0;
    private double g2DMarkScore = 0;

    @Override
    public String toString() {
        return "GPUBenchmarkResults{" +
                "source='" + source + '\'' +
                ", modelName='" + modelName + '\'' +
                ", g2DMarkScore=" + g2DMarkScore +
                ", g3DMarkScore=" + g3DMarkScore +
                '}';
    }
}
