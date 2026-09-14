package de.verdox.hwapi.catalog.persistence;

import de.verdox.hwapi.catalog.domain.HardwareSpec;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface HardwareSpecRepository extends JpaRepository<HardwareSpec<?>, Long>, JpaSpecificationExecutor<HardwareSpec<?>> {
    interface HardwareSpecMpnProjection {
        Long getSpecId();
        String getMpn();
    }

    interface HardwareLightView {
        long getId();

        String getDiscriminator(); // z.B. via @DiscriminatorValue oder getClass().getName() per native query

        List<String> getEANs();

        String getMPN();
    }

    @Query("""
        select h.id as specId, m as mpn
        from HardwareSpec h
        join h.MPNs m
        """)
    List<HardwareSpecMpnProjection> findAllMpnMappings();

    @Query("""
            select distinct h
            from #{#entityName} h
            left join h.EANs e
            left join h.MPNs m
            where ( :hasEans = true and e in :allEans )
               or ( :hasMpns = true and m in :allMpns )
            """)
    @EntityGraph(attributePaths = {
            "EANs", "MPNs"
    })
    List<HardwareSpec<?>> findAllByAnyEanOrMpnIn(
            @Param("allEans") Collection<String> allEans,
            @Param("allMpns") Collection<String> allMpns,
            @Param("hasEans") boolean hasEans,
            @Param("hasMpns") boolean hasMpns
    );

    @Query("""
            select distinct h
            from #{#entityName} h
            where (:hasEans = true and exists (
                      select 1 from h.EANs e where e in :eans))
               or (:hasMpns = true and exists (
                      select 1 from h.MPNs m where m in :mpns))
            """)
    @EntityGraph(attributePaths = {
            "EANs", "MPNs"
    })
    List<HardwareSpec<?>> findAllByAnyEanOrMpnExists(@Param("eans") Collection<String> eans,
                                                     @Param("mpns") Collection<String> mpns,
                                                     @Param("hasEans") boolean hasEans,
                                                     @Param("hasMpns") boolean hasMpns);

    @Query("""
            select h
            from #{#entityName} h
            join h.EANs e
            where e = :ean
            """)
    @EntityGraph(attributePaths = {
            "EANs", "MPNs"
    })
    Optional<HardwareSpec<?>> findByEan(@Param("ean") String ean);

    @Query("""
            select h
            from #{#entityName} h
            join h.MPNs m
            where m = :mpn
            """)
    @EntityGraph(attributePaths = {
            "EANs", "MPNs"
    })
    Optional<HardwareSpec<?>> findByMPN(@Param("mpn") String mpn);

    @EntityGraph(attributePaths = {"EANs", "MPNs"})
    @Query("""
        select distinct h
        from HardwareSpec h
        where :input in elements(h.EANs)
           or :input in elements(h.MPNs)
        """)
    Optional<HardwareSpec<?>> findByEanOrMpn(@Param("input") String input);

    @Query("""
            select distinct h
            from HardwareSpec h
            left join h.EANs e
            left join h.MPNs m
            where e in :inputs or m in :inputs
            """)
    @EntityGraph(attributePaths = {
            "EANs", "MPNs"
    })
    List<HardwareSpec<?>> findAllByEanOrMpn(@Param("inputs") Collection<String> inputs);

    @Query("""
            select distinct lower(trim(h.manufacturer))
            from HardwareSpec h
            where h.manufacturer is not null and h.manufacturer <> ''
            """)
    Set<String> findAllManufacturersNormalized();

    Optional<HardwareSpec<?>> findById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HardwareSpec h where h.id in :ids")
    List<HardwareSpec<?>> findAllForUpdate(@Param("ids") Collection<Long> ids);

    @Query("select count(h) from HardwareSpec h where type(h) = :clazz")
    long countByType(@Param("clazz") Class<? extends HardwareSpec<?>> clazz);

    @Query("select h.id from HardwareSpec h where type(h) = :clazz")
    Page<Long> findPageIdsByType(@Param("clazz") Class<? extends HardwareSpec<?>> clazz, Pageable pageable);

    @Query("select h from HardwareSpec h where h.id in :ids order by h.id asc")
    List<HardwareSpec<?>> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

}
