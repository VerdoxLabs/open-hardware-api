package de.verdox.hwapi.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductIdentifierRepository extends JpaRepository<ProductIdentifier, Long> {

    @org.springframework.data.jpa.repository.Query("select p from ProductIdentifier p where p.type = :type and p.normalizedValue = :value")
    Optional<ProductIdentifier> findByTypeAndIdentifier(@org.springframework.data.repository.query.Param("type") ProductIdentifier.IdentifierType type,
                                                        @org.springframework.data.repository.query.Param("value") String value);

    /**
     * Compatibility lookup for rows written before normalized_value was populated
     * canonically. New writes must use {@link #findByTypeAndIdentifier}.
     */
    Optional<ProductIdentifier> findFirstByTypeAndNormalizedValueIgnoreCase(
            ProductIdentifier.IdentifierType type, String normalizedValue);

    @org.springframework.data.jpa.repository.Query("select p from ProductIdentifier p where p.type = :type and p.normalizedValue in :values")
    List<ProductIdentifier> findAllByTypeAndIdentifierIn(@org.springframework.data.repository.query.Param("type") ProductIdentifier.IdentifierType type,
                                                         @org.springframework.data.repository.query.Param("values") Collection<String> values);

    List<ProductIdentifier> findAllByType(ProductIdentifier.IdentifierType type);

    @org.springframework.data.jpa.repository.Query("select count(p) > 0 from ProductIdentifier p where p.type = :type and p.normalizedValue = :value")
    boolean existsByTypeAndIdentifier(@org.springframework.data.repository.query.Param("type") ProductIdentifier.IdentifierType type,
                                      @org.springframework.data.repository.query.Param("value") String value);

    @org.springframework.data.jpa.repository.Query("select p from ProductIdentifier p where p.type = :type and lower(p.normalizedValue) like concat('%', lower(:value), '%')")
    List<ProductIdentifier> findTop50ByTypeAndIdentifierContaining(@org.springframework.data.repository.query.Param("type") ProductIdentifier.IdentifierType type,
                                                                    @org.springframework.data.repository.query.Param("value") String value);
}
