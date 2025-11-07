package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.Storage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface StorageRepository extends HardwareSpecificRepo<Storage> {
    @Override
    @EntityGraph(value = "Storage.All")
    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<Storage> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(value = "Storage.All")
    Optional<Storage> findByEan(String ean);

    @Override
    @EntityGraph(value = "Storage.All")
    Optional<Storage> findByMPNIgnoreCase(String mpn);

    @EntityGraph(value = "Storage.All")
    @Query("""
           select distinct h
           from #{#entityName} h
           join h.EANs e
           where e in :eans
           """)
    List<Storage> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @EntityGraph(value = "Storage.All")
    @Query("select h from HardwareSpec h where h.MPN in :mpns")
    List<Storage> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);
}
