package de.verdox.hwapi.benchmarkapi.repository;

import de.verdox.hwapi.benchmarkapi.entity.GPUBenchmarkResults;
import org.springframework.stereotype.Repository;

@Repository
public interface GPUBenchmarkRepository extends BenchmarkResultRepository<GPUBenchmarkResults> {
}
