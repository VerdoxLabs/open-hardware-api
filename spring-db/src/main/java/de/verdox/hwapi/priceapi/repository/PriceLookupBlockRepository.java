package de.verdox.hwapi.priceapi.repository;

import de.verdox.hwapi.priceapi.model.PriceLookupBlock;
import de.verdox.hwapi.model.values.Currency;
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
