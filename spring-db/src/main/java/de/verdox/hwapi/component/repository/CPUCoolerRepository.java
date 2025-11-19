package de.verdox.hwapi.component.repository;

import de.verdox.hwapi.model.CPUCooler;
import jakarta.persistence.NamedAttributeNode;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository

public interface CPUCoolerRepository extends HardwareSpecificRepo<CPUCooler> {
    @Override
    @EntityGraph(value = "CPUCooler.All")
    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<CPUCooler> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(value = "CPUCooler.All")
    Optional<CPUCooler> findByEan(String ean);

    @Override
    @EntityGraph(value = "CPUCooler.All")
    Optional<CPUCooler> findByMPN(String mpn);

    @EntityGraph(value = "CPUCooler.All")
    @Query("""
           select distinct h
           from #{#entityName} h
           join h.EANs e
           where e in :eans
           """)
    List<CPUCooler> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @EntityGraph(value = "CPUCooler.All")
    @Query("""
            select distinct h
            from #{#entityName} h
            join h.MPNs e
            where e in :eans
            """)
    List<CPUCooler> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);

}
