package de.verdox.hwapi.priceapi.repository;

import de.verdox.hwapi.model.values.ItemCondition;
import de.verdox.hwapi.priceapi.model.RemoteActiveListing;
import de.verdox.hwapi.model.values.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

@Repository
public interface RemoteActiveListingRepository extends JpaRepository<RemoteActiveListing, UUID> {

    Optional<RemoteActiveListing> findFirstByEanAndCurrencyAndStillActiveIsTrueOrderByPriceAsc(String ean, Currency currency);

    List<RemoteActiveListing> findByEanInAndCurrencyAndStillActiveIsTrue(Collection<String> eans, Currency currency);

    List<RemoteActiveListing> findByMpnInAndCurrencyAndStillActiveIsTrue(Collection<String> mpns, Currency currency);

    Optional<RemoteActiveListing> findByMarketPlaceDomainAndMarketPlaceItemID(String marketPlaceDomain, String marketPlaceItemId);

    @Query("""
        SELECT r
        FROM RemoteActiveListing r
        WHERE (r.ean IN :eans OR r.ean IN :mpns)
          AND r.condition IN :conditions
          AND r.lastSeenAt >= :fromInstant
        """)
    List<RemoteActiveListing> findPricePointsInternal(
            @Param("mpns") Set<String> mpns,
            @Param("eans") Set<String> eans,
            @Param("conditions") Set<ItemCondition> conditions,
            @Param("fromInstant") Instant fromInstant
    );

    default List<RemoteActiveListing> findPricePoints(
            Set<String> mpns,
            Set<String> eans,
            Set<ItemCondition> conditions,
            int monthSince
    ) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("conditions must not be null or empty");
        }

        Instant fromInstant = java.time.ZonedDateTime
                .now(java.time.ZoneOffset.UTC)
                .minusMonths(monthSince)
                .toInstant();

        return findPricePointsInternal(mpns, eans, conditions, fromInstant);
    }
}