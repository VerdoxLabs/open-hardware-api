package de.verdox.hwapi.pricing.repository;

import de.verdox.hwapi.pricing.model.PriceLookupBlock;
import de.verdox.hwapi.catalog.domain.values.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceLookupBlockRepository extends JpaRepository<PriceLookupBlock, UUID> {

    Optional<PriceLookupBlock> findByEanAndCurrency(String ean, Currency currency);

    void deleteByBlockedUntilBefore(Instant now);
}
