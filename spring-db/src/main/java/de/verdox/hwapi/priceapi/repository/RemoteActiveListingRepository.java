package de.verdox.hwapi.priceapi.repository;

import de.verdox.hwapi.priceapi.model.RemoteActiveListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface RemoteActiveListingRepository extends JpaRepository<RemoteActiveListing, UUID> {
    Optional<RemoteActiveListing> findByMarketPlaceDomainAndMarketPlaceItemID(
            String marketPlaceDomain,
            String marketPlaceItemID
    );
}