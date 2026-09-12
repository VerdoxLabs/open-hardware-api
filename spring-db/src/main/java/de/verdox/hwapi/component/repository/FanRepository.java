package de.verdox.hwapi.component.repository;

import de.verdox.hwapi.model.Fan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface FanRepository extends HardwareSpecificRepo<Fan> {
    @Override
    @EntityGraph(value = "Fan.All")
    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<Fan> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(value = "Fan.All")
    Optional<Fan> findByEan(String ean);

    @Override
    @EntityGraph(value = "Fan.All")
    Optional<Fan> findByMPN(String mpn);

    @EntityGraph(value = "Fan.All")
    @Query("""
           select distinct h
           from #{#entityName} h
           join h.EANs e
           where e in :eans
           """)
    List<Fan> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @EntityGraph(value = "Fan.All")
    @Query("""
            select distinct h
            from #{#entityName} h
            join h.MPNs e
            where e in :eans
            """)
    List<Fan> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);
}
