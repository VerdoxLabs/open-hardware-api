package de.verdox.hwapi.productid;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductIdentifierRepository extends JpaRepository<ProductIdentifier, Long> {

    Optional<ProductIdentifier> findByTypeAndIdentifier(ProductIdentifier.IdentifierType type, String value);

    List<ProductIdentifier> findAllByTypeAndIdentifierIn(ProductIdentifier.IdentifierType type, Collection<String> values);

    List<ProductIdentifier> findAllByType(ProductIdentifier.IdentifierType type);

    boolean existsByTypeAndIdentifier(ProductIdentifier.IdentifierType type, String value);

    List<ProductIdentifier> findTop50ByTypeAndIdentifierContaining(ProductIdentifier.IdentifierType type, String value);
}
