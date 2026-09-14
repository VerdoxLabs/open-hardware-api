package de.verdox.hwapi.benchmark.repository;

import de.verdox.hwapi.benchmark.entity.GPUBenchmarkResults;
import org.springframework.stereotype.Repository;

@Repository
public interface GPUBenchmarkRepository extends BenchmarkResultRepository<GPUBenchmarkResults> {
}
