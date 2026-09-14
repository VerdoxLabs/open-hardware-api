package de.verdox.hwapi.pricing.repository;

import de.verdox.hwapi.pricing.model.ListingPricePoint;
import de.verdox.hwapi.pricing.model.RemoteActiveListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public interface ListingPricePointRepository extends JpaRepository<ListingPricePoint, UUID> {

    Optional<ListingPricePoint> findByListingAndSnapshotDate(RemoteActiveListing listing, LocalDate snapshotDate);

    /**
     * Performance:
     * - join fetch => kein N+1 wenn du später listing-Felder liest
     * - hasMpns/hasEans => leere Sets verursachen keine kaputten IN()-Teile
     * - manufacturerLower sollte bereits lowercased + mit %...% kommen
     */
    @Query("""
   select p
   from ListingPricePoint p
   join fetch p.listing l
   where
     ( (:hasMpns = true and l.mpn in :mpns) or (:hasEans = true and l.ean in :eans) )
     and p.capturedAt >= :since
     and lower(l.productManufacturer) like lower(:manufacturer)
   order by p.capturedAt asc
""")
    List<ListingPricePoint> findPricePoints(
            @Param("manufacturer") String manufacturer,
            @Param("mpns") Set<String> mpns,
            @Param("eans") Set<String> eans,
            @Param("hasMpns") boolean hasMpns,
            @Param("hasEans") boolean hasEans,
            @Param("since") Instant since
    );

    @Query("select count(distinct p.listing.mpn) from ListingPricePoint p")
    long countDistinctListings();

    @Query("""
        select count(distinct p.listing.mpn)
        from ListingPricePoint p
    """)
    long countDistinctSpecsByEanOrMpn();
}
