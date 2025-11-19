package de.verdox.hwapi.component.repository;

import de.verdox.hwapi.model.HardwareSpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface HardwareSpecificRepo<HARDWARE extends HardwareSpec<HARDWARE>> extends JpaRepository<HARDWARE, Long>, JpaSpecificationExecutor<HARDWARE> {
    static String normalizeEAN_MPN(String s) {
        if (s == null) return null;
        return s.trim().replaceAll("[\\s\\-_.]", "").toUpperCase();
    }

    @Query("""
                select h.id
                from #{#entityName} h
                order by h.id asc
            """)
    Page<Long> findPageIds(Pageable pageable);

    @Query("""
                select h
                from #{#entityName} h
                where h.id in :ids
                order by h.id asc
            """)
    List<HARDWARE> findAllByIdInOrderByIdAsc(@Param("ids") List<Long> ids);

    @Query("""
            select e, h
            from CPU h
            join h.EANs e
            where e in :eans
            """)
    List<Object[]> findPairsByEans(@Param("eans") Set<String> eans);

    @Query("""
            select e, h
            from CPU h
            join h.MPNs e
            where e in :mpns
            """)
    List<Object[]> findPairsByMpns(@Param("eans") Set<String> mpns);

    @Query("""
            select h
            from #{#entityName} h
             where :ean member of h.EANs
            """)
    Optional<HARDWARE> findByEan(@Param("ean") String ean);

    @Query("""
            select h
            from #{#entityName} h
             where :mpn member of h.MPNs
            """)
    Optional<HARDWARE> findByMPN(String mpn);

    @Query("""
            select distinct h
            from CPU h
            join h.EANs e
            where e in :eans
            """)
    List<HARDWARE> findAllByEANNormIn(@Param("eans") Set<String> eans);

    @Query("""
            select distinct h
            from #{#entityName} h
            join h.MPNs e
            where e in :eans
            """)
    List<HARDWARE> findAllByMPNNormIn(@Param("mpns") Set<String> mpns);

    // Bequeme Map<EAN, Entity>
    default Map<String, HARDWARE> findAllByEanIn(Set<String> eans) {
        if (eans == null || eans.isEmpty()) return Map.of();
        var rows = findPairsByEans(eans);
        Map<String, HARDWARE> out = new LinkedHashMap<>(rows.size());
        for (Object[] r : rows) {
            String ean = (String) r[0];
            @SuppressWarnings("unchecked") HARDWARE hw = (HARDWARE) r[1];
            out.put(ean, hw);
        }
        return out;
    }

    default Map<String, HARDWARE> findAllByMPNInNormalized(Set<String> mpns) {
        if (mpns == null || mpns.isEmpty()) return Map.of();
        var rows = findPairsByMpns(mpns);
        Map<String, HARDWARE> out = new LinkedHashMap<>(rows.size());
        for (Object[] r : rows) {
            String mpn = (String) r[0];
            @SuppressWarnings("unchecked") HARDWARE hw = (HARDWARE) r[1];
            out.put(mpn, hw);
        }
        return out;
    }
}
