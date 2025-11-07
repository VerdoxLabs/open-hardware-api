package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.CPU;
import de.verdox.openhardwareapi.model.CPUCooler;
import jakarta.persistence.NamedEntityGraph;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CPURepository extends HardwareSpecificRepo<CPU> {
    @Override
    @EntityGraph(value = "CPU.All")
    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<CPU> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(value = "CPU.All")
    Optional<CPU> findByEan(String ean);

    @Override
    @EntityGraph(value = "CPU.All")
    Optional<CPU> findByMPNIgnoreCase(String mpn);

    @EntityGraph(value = "CPU.All")
    @Query("""
           select distinct h
           from #{#entityName} h
           join h.EANs e
           where e in :eans
           """)
    List<CPU> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @EntityGraph(value = "CPU.All")
    @Query("select h from HardwareSpec h where h.MPN in :mpns")
    List<CPU> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);
}
