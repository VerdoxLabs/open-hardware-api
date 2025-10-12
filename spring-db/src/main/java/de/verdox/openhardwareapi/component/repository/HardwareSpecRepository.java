package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.HardwareSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface HardwareSpecRepository extends JpaRepository<HardwareSpec, Long>, JpaSpecificationExecutor<HardwareSpec> {
    Optional<HardwareSpec> findByModelIgnoreCase(String model);
    boolean existsByModelIgnoreCase(String model);
    boolean existsByModelIgnoreCaseAndIdNot(String model, Long id);

    Optional<HardwareSpec> findByEANIgnoreCaseOrMPNIgnoreCaseOrUPCIgnoreCase(String ean, String mpn, String upc);

    Optional<HardwareSpec> findByEANIgnoreCaseAndMPNIgnoreCaseAndUPCIgnoreCase(String ean, String mpn, String upc);
    Optional<HardwareSpec> findByEANIgnoreCaseAndMPNIgnoreCase(String ean, String mpn);
    Optional<HardwareSpec> findByEANIgnoreCaseAndUPCIgnoreCase(String ean, String upc);
    Optional<HardwareSpec> findByMPNIgnoreCaseAndUPCIgnoreCase(String mpn, String upc);

    Optional<HardwareSpec> findByEANIgnoreCase(String ean);
    Optional<HardwareSpec> findByMPNIgnoreCase(String mpn);
    Optional<HardwareSpec> findByUPCIgnoreCase(String upc);

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
}
