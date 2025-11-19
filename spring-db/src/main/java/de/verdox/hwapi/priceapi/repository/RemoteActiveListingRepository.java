package de.verdox.hwapi.priceapi.repository;

import de.verdox.hwapi.priceapi.model.RemoteActiveListing;
import de.verdox.hwapi.model.values.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RemoteActiveListingRepository extends JpaRepository<RemoteActiveListing, UUID> {

    Optional<RemoteActiveListing> findFirstByEanAndCurrencyAndStillActiveIsTrueOrderByPriceAsc(String ean, Currency currency);

    List<RemoteActiveListing> findByEanInAndCurrencyAndStillActiveIsTrue(Collection<String> eans, Currency currency);

    List<RemoteActiveListing> findByMpnInAndCurrencyAndStillActiveIsTrue(Collection<String> mpns, Currency currency);

    Optional<RemoteActiveListing> findByMarketPlaceDomainAndMarketPlaceItemID(String marketPlaceDomain, String marketPlaceItemId);
}