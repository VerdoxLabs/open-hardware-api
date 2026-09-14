package de.verdox.hwapi.benchmark.repository;

import de.verdox.hwapi.benchmark.entity.BenchmarkResults;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.Set;

public interface BenchmarkResultRepository <ENTITY extends BenchmarkResults<?>> extends JpaRepository<ENTITY, Long>, JpaSpecificationExecutor<ENTITY> {
    Set<ENTITY> findByModelName(String modelName);

    Optional<ENTITY> findByModelNameAndSource(String modelName, String source);

    Set<ENTITY> findAllByModelNameAndSource(String modelName, String source);

    Set<ENTITY> findBySource(String source);
}
