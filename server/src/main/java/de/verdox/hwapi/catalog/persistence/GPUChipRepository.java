package de.verdox.hwapi.catalog.persistence;

import de.verdox.hwapi.catalog.domain.Display;
import de.verdox.hwapi.catalog.domain.GPUChip;
import de.verdox.hwapi.catalog.domain.HardwareTypes;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface GPUChipRepository extends HardwareSpecificRepo<GPUChip> {

    Optional<GPUChip> findByCanonicalModelIgnoreCase(String canonicalModel);

    Optional<GPUChip> findFirstByCanonicalModelIgnoreCaseAndVramType(String canonicalModel, HardwareTypes.VRAM_TYPE vramType);

    Optional<GPUChip> findFirstByCanonicalModelIgnoreCaseAndVramTypeAndVramGb(String canonicalModel, HardwareTypes.VRAM_TYPE vramType, double vramGb);

    @Override
    @EntityGraph(value = "GPUChip.All")
    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<GPUChip> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(value = "GPUChip.All")
    Optional<GPUChip> findByEan(String ean);

    @Override
    @EntityGraph(value = "GPUChip.All")
    Optional<GPUChip> findByMPN(String mpn);

    @EntityGraph(value = "GPUChip.All")
    @Query("""
           select distinct h
           from #{#entityName} h
           join h.EANs e
           where e in :eans
           """)
    List<GPUChip> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @EntityGraph(value = "GPUChip.All")
    @Query("""
            select distinct h
            from #{#entityName} h
            join h.MPNs e
            where e in :eans
            """)
    List<GPUChip> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);
}
