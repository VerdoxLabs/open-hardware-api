package de.verdox.hwapi.component.repository;

import de.verdox.hwapi.model.HardwareSpec;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface HardwareSpecRepository extends JpaRepository<HardwareSpec<?>, Long>, JpaSpecificationExecutor<HardwareSpec<?>> {
    interface HardwareLightView {
        long getId();

        String getDiscriminator(); // z.B. via @DiscriminatorValue oder getClass().getName() per native query

        List<String> getEANs();

        String getMPN();
    }

    Optional<HardwareSpec<?>> findByModelIgnoreCase(String model);

    boolean existsByModelIgnoreCase(String model);

    boolean existsByModelIgnoreCaseAndIdNot(String model, Long id);

    @Query("""
    select distinct h
    from #{#entityName} h
    left join h.EANs e
    left join h.MPNs m
    where ( :hasEans = true and e in :allEans )
       or ( :hasMpns = true and m in :allMpns )
    """)
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
    Optional<HardwareSpec<?>> findByEan(@Param("ean") String ean);

    @Query("""
            select h
            from #{#entityName} h
            join h.MPNs m
            where m = :mpn
            """)
    Optional<HardwareSpec<?>> findByMPN(@Param("mpn") String mpn);

    @Query("""
       select h
       from #{#entityName} h
       left join h.EANs e
       left join h.MPNs m
       where e = :input or m = :input
       """)
    Optional<HardwareSpec<?>> findByEanOrMpn(@Param("input") String input);

    @Query("""
            select distinct h.manufacturer
            from HardwareSpec h
            where h.manufacturer is not null and h.manufacturer <> ''
            """)
    Set<String> findAllManufacturers();

    @Query("""
            select distinct lower(trim(h.manufacturer))
            from HardwareSpec h
            where h.manufacturer is not null and h.manufacturer <> ''
            """)
    Set<String> findAllManufacturersNormalized();

    @Query("""
            select distinct lower(trim(h.manufacturer))
            from HardwareSpec h
            where h.manufacturer is not null and h.manufacturer <> ''
            order by lower(trim(h.manufacturer))
            """)
    List<String> findAllManufacturersNormalizedOrdered();


    // Leichtgewichtiger Stream in Batches:
    @Query("""
               select h.id as id,
                      type(h) as discriminator,
                      upper(h.EANs) as EANs,
                      upper(h.MPNs) as MPN
               from HardwareSpec h
            """)
    Page<HardwareLightView> findAllLight(Pageable pageable);

    Optional<HardwareSpec<?>> findById(long id);

    // Für Locking bei Merge:
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HardwareSpec h where h.id in :ids")
    List<HardwareSpec<?>> findAllForUpdate(@Param("ids") Collection<Long> ids);

}
