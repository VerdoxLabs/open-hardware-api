package de.verdox.hwapi.benchmark.repository;

import de.verdox.hwapi.benchmark.entity.CPUBenchmarkResults;
import org.springframework.stereotype.Repository;

@Repository
public interface CPUBenchmarkRepository extends BenchmarkResultRepository<CPUBenchmarkResults> {
}
