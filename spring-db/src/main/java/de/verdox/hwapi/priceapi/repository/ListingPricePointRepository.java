package de.verdox.hwapi.priceapi.repository;

import de.verdox.hwapi.priceapi.model.ListingPricePoint;
import de.verdox.hwapi.priceapi.model.RemoteActiveListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public interface ListingPricePointRepository extends JpaRepository<ListingPricePoint, UUID> {
    Optional<ListingPricePoint> findByListingAndSnapshotDate(RemoteActiveListing listing, LocalDate snapshotDate);

    @Query("""
           select p
           from ListingPricePoint p
           join p.listing l
           where
             ( (l.mpn in :mpns) or (l.ean in :eans) )
             and p.capturedAt >= :since
             and lower(l.productManufacturer) like lower(:manufacturer)
           order by p.capturedAt asc
           """)
    List<ListingPricePoint> findPricePoints(
            @Param("manufacturer") String manufacturer,
            @Param("mpns") Set<String> mpns,
            @Param("eans") Set<String> eans,
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
