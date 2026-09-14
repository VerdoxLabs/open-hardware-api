package de.verdox.hwapi.pricing.repository;

import de.verdox.hwapi.pricing.model.RemoteActiveListing;
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