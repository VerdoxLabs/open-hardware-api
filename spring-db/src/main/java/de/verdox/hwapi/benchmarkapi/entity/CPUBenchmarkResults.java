package de.verdox.hwapi.benchmarkapi.entity;

import de.verdox.hwapi.model.CPU;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("CPU")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "cpu_benchmark_results",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source", "model_name"})
)
public class CPUBenchmarkResults extends BenchmarkResults<CPU> {
    private double cpuMarkScore = 0;
    private double threadMarkScore = 0;

    @Override
    public String toString() {
        return "CPUBenchmarkResults{" +
                "source='" + source + '\'' +
                ", modelName='" + modelName + '\'' +
                ", threadMarkScore=" + threadMarkScore +
                ", cpuMarkScore=" + cpuMarkScore +
                '}';
    }
}
