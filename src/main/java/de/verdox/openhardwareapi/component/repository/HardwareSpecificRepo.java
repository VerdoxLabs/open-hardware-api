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

import java.util.ArrayList;
import java.util.List;

public interface HardwareSpecificRepo<HARDWARE extends HardwareSpec> extends JpaRepository<HARDWARE, Long>, JpaSpecificationExecutor<HARDWARE> {
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
}
