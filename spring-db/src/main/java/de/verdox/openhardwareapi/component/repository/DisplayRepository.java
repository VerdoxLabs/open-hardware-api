package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.CPU;
import de.verdox.openhardwareapi.model.CPUCooler;
import de.verdox.openhardwareapi.model.Display;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface DisplayRepository extends HardwareSpecificRepo<Display> {
    @Override
    @EntityGraph(value = "Display.All")
    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<Display> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(value = "Display.All")
    Optional<Display> findByEan(String ean);

    @Override
    @EntityGraph(value = "Display.All")
    Optional<Display> findByMPNIgnoreCase(String mpn);

    @EntityGraph(value = "Display.All")
    @Query("""
           select distinct h
           from #{#entityName} h
           join h.EANs e
           where e in :eans
           """)
    List<Display> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @EntityGraph(value = "Display.All")
    @Query("select h from HardwareSpec h where h.MPN in :mpns")
    List<Display> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);
}
