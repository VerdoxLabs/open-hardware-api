package de.verdox.hwapi.benchmarkapi.repository;

import de.verdox.hwapi.benchmarkapi.entity.CPUBenchmarkResults;
import org.springframework.stereotype.Repository;

@Repository
public interface CPUBenchmarkRepository extends BenchmarkResultRepository<CPUBenchmarkResults> {
}
