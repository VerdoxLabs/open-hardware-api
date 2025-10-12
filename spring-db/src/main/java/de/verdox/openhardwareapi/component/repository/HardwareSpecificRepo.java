package de.verdox.openhardwareapi.component.repository;

import de.verdox.openhardwareapi.model.HardwareSpec;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

public interface HardwareSpecificRepo<HARDWARE extends HardwareSpec<HARDWARE>> extends JpaRepository<HARDWARE, Long>, JpaSpecificationExecutor<HARDWARE> {
    static String normalizeEANMPNUPC(String s) {
        if (s == null) return null;
        return s.trim().replaceAll("[\\s\\-_.]", "").toUpperCase();
    }

    Page<HARDWARE> findByModelContainingIgnoreCase(String model, Pageable pageable);

    @Query("select h from HardwareSpec h where type(h) = :clazz")
    Page<HARDWARE> findAllExact(@Param("clazz") Class<HARDWARE> clazz, Pageable pageable);

    @Query("select count(h) from HardwareSpec h where type(h) = :clazz")
    long countExact(@Param("clazz") Class<? extends HardwareSpec> clazz);

    @Query("""
               select h from HardwareSpec h
               where type(h) = :clazz
                 and lower(h.model) like lower(concat('%', :tokens, '%'))
            """)
    Page<HARDWARE> searchByModelTokensExact(@Param("clazz") Class<HARDWARE> clazz, @Param("tokens") String tokens, Pageable pageable);

    @Query("""
               select count(h) from HardwareSpec h
               where type(h) = :clazz
                 and lower(h.model) like lower(concat('%', :tokens, '%'))
            """)
    long countByModelTokensExact(@Param("clazz") Class<? extends HardwareSpec> clazz, @Param("tokens") String tokens);

    default Page<HARDWARE> searchByModelTokens(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return findAll(pageable);
        }

        String[] tokens = search.toLowerCase().split("\\s+");

        Specification<HARDWARE> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (String token : tokens) {
                predicates.add(cb.like(cb.lower(root.get("model")), "%" + token + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return findAll(spec, pageable);
    }

    long countByModelContainingIgnoreCase(String model);

    Optional<HARDWARE> findByEANIgnoreCase(String ean);

    Optional<HARDWARE> findByMPNIgnoreCase(String mpn);

    Optional<HARDWARE> findByUPCIgnoreCase(String mpn);

    List<HARDWARE> findByEAN(String ean);

    Optional<HARDWARE> findByEANIgnoreCaseOrMPNIgnoreCaseOrUPCIgnoreCase(String ean, String mpn, String upc);

    @Query("select h from HardwareSpec h where h.EAN in :eans")
    List<HARDWARE> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @Query("select h from HardwareSpec h where h.MPN in :mpns")
    List<HARDWARE> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);

    @Query("select h from HardwareSpec h where h.UPC in :upcs")
    List<HARDWARE> findAllByUPCNormIn(@Param("upcs") Set<String> upcs);

    // Helper zum Map-Bau:
    default Map<String, HARDWARE> findAllByEANInNormalized(Set<String> eans) {
        if (eans.isEmpty()) return Map.of();
        return findAllByEANNormIn(eans).stream()
                .collect(Collectors.toMap(HARDWARE::getEAN, h -> h));
    }

    default Map<String, HARDWARE> findAllByMPNInNormalized(Set<String> mpns) {
        if (mpns.isEmpty()) return Map.of();
        return findAllByMPNNormIn(mpns).stream()
                .collect(Collectors.toMap(HARDWARE::getMPN, h -> h));
    }

    default Map<String, HARDWARE> findAllByUPCInNormalized(Set<String> upcs) {
        if (upcs.isEmpty()) return Map.of();
        return findAllByUPCNormIn(upcs).stream()
                .collect(Collectors.toMap(HARDWARE::getUPC, h -> h));
    }
}
