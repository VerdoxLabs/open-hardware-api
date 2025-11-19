package de.verdox.hwapi.component.repository;

import de.verdox.hwapi.model.HardwareTypes;
import de.verdox.hwapi.model.PSU;
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
public interface PSURepository extends HardwareSpecificRepo<PSU> {
    @Override
    @EntityGraph(value = "PSU.All")
    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<PSU> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(value = "PSU.All")
    Optional<PSU> findByEan(String ean);

    @Override
    @EntityGraph(value = "PSU.All")
    Optional<PSU> findByMPN(String mpn);

    @EntityGraph(value = "PSU.All")
    @Query("""
           select distinct h
           from #{#entityName} h
           join h.EANs e
           where e in :eans
           """)
    List<PSU> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @EntityGraph(value = "PSU.All")
    @Query("""
            select distinct h
            from #{#entityName} h
            join h.MPNs e
            where e in :eans
            """)
    List<PSU> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);

    List<PSU> findByWattageAndEfficiencyRatingAndModularity(Integer wattage, HardwareTypes.PsuEfficiencyRating efficiencyRating, HardwareTypes.PSU_MODULARITY modularity);

    Page<PSU> findByWattageAndEfficiencyRatingAndModularity(Integer wattage, HardwareTypes.PsuEfficiencyRating efficiencyRating, HardwareTypes.PSU_MODULARITY modularity, Pageable pageable);

    List<PSU> findByWattageAndEfficiencyRating(Integer wattage, HardwareTypes.PsuEfficiencyRating efficiencyRating);

    Page<PSU> findByWattageAndEfficiencyRating(Integer wattage, HardwareTypes.PsuEfficiencyRating efficiencyRatingPageable, Pageable pageable);
}
