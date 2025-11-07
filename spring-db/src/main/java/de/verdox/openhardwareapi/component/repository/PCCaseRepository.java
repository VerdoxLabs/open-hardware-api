package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.Motherboard;
import de.verdox.openhardwareapi.model.PCCase;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PCCaseRepository extends HardwareSpecificRepo<PCCase> {
    @Override
    @EntityGraph(value = "PCCase.All")
    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<PCCase> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(value = "PCCase.All")
    Optional<PCCase> findByEan(String ean);

    @Override
    @EntityGraph(value = "PCCase.All")
    Optional<PCCase> findByMPNIgnoreCase(String mpn);

    @EntityGraph(value = "PCCase.All")
    @Query("""
           select distinct h
           from #{#entityName} h
           join h.EANs e
           where e in :eans
           """)
    List<PCCase> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @EntityGraph(value = "PCCase.All")
    @Query("select h from HardwareSpec h where h.MPN in :mpns")
    List<PCCase> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);
}
