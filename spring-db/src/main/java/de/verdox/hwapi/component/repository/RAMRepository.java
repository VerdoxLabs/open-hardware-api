package de.verdox.hwapi.component.repository;

import de.verdox.hwapi.model.HardwareTypes;
import de.verdox.hwapi.model.RAM;
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
public interface RAMRepository extends HardwareSpecificRepo<RAM> {
    @Override
    @EntityGraph(value = "RAM.All")
    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<RAM> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(value = "RAM.All")
    Optional<RAM> findByEan(String ean);

    @Override
    @EntityGraph(value = "RAM.All")
    Optional<RAM> findByMPN(String mpn);

    @EntityGraph(value = "RAM.All")
    @Query("""
           select distinct h
           from #{#entityName} h
           join h.EANs e
           where e in :eans
           """)
    List<RAM> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @EntityGraph(value = "RAM.All")
    @Query("""
            select distinct h
            from #{#entityName} h
            join h.MPNs e
            where e in :eans
            """)
    List<RAM> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);

    List<RAM> findByType(HardwareTypes.RamType type);

    Page<RAM> findByType(HardwareTypes.RamType type, Pageable pageable);

    Page<RAM> findByTypeAndSpeedMtpsEquals(HardwareTypes.RamType type, Integer speedMtps, Pageable pageable);

    List<RAM> findByTypeAndSpeedMtpsEquals(HardwareTypes.RamType type, Integer speedMtps);
}
