package de.verdox.hwapi.component.repository;

import de.verdox.hwapi.model.GPU;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface GPURepository extends HardwareSpecificRepo<GPU> {
    @Override
    @EntityGraph(value = "GPU.All")
    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<GPU> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(value = "GPU.All")
    Optional<GPU> findByEan(String ean);

    @Override
    @EntityGraph(value = "GPU.All")
    Optional<GPU> findByMPN(String mpn);

    @EntityGraph(value = "GPU.All")
    @Query("""
           select distinct h
           from #{#entityName} h
           join h.EANs e
           where e in :eans
           """)
    List<GPU> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @EntityGraph(value = "GPU.All")
    @Query("""
            select distinct h
            from #{#entityName} h
            join h.MPNs e
            where e in :eans
            """)
    List<GPU> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);

    List<GPU> findByGpuCanonicalNameContainingIgnoreCase(String gpuCanonicalName);

    Page<GPU> findByGpuCanonicalNameContainingIgnoreCase(String gpuCanonicalName, Pageable pageable);
}
