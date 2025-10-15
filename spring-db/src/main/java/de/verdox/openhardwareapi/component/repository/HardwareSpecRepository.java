package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.HardwareSpec;
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
        String getEAN();
        String getMPN();
    }

    Optional<HardwareSpec<?>> findByModelIgnoreCase(String model);
    boolean existsByModelIgnoreCase(String model);
    boolean existsByModelIgnoreCaseAndIdNot(String model, Long id);

    Optional<HardwareSpec<?>> findByEANIgnoreCase(String ean);
    Optional<HardwareSpec<?>> findByMPNIgnoreCase(String mpn);

    // Rohwerte (Groß-/Kleinschreibung wie gespeichert)
    @Query("""
           select distinct h.manufacturer
           from HardwareSpec h
           where h.manufacturer is not null and h.manufacturer <> ''
           """)
    Set<String> findAllManufacturers();

    // Normalisiert (case-insensitiv, getrimmt) – liefert z.B. "asus", "msi", ...
    @Query("""
           select distinct lower(trim(h.manufacturer))
           from HardwareSpec h
           where h.manufacturer is not null and h.manufacturer <> ''
           """)
    Set<String> findAllManufacturersNormalized();

    // Optional: sortiert für UI (dann List statt Set)
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
              upper(h.EAN) as EAN,
              upper(h.MPN) as MPN
       from HardwareSpec h
    """)
    Page<HardwareLightView> findAllLight(Pageable pageable);

    Optional<HardwareSpec<?>> findById(long id);

    // Für Locking bei Merge:
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HardwareSpec h where h.id in :ids")
    List<HardwareSpec<?>> findAllForUpdate(@Param("ids") Collection<Long> ids);

}
