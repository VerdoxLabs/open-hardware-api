package de.verdox.hwapi.priceapi.component.service;

import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.priceapi.model.ListingEnums;
import de.verdox.hwapi.priceapi.model.ListingPricePoint;
import de.verdox.hwapi.priceapi.model.RemoteActiveListing;
import de.verdox.hwapi.priceapi.repository.ListingPricePointRepository;
import de.verdox.hwapi.priceapi.repository.RemoteActiveListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RemoteActiveListingWriterService {

    private final RemoteActiveListingRepository listingRepository;
    private final ListingPricePointRepository priceRepository;

    @Transactional
    public RemoteActiveListing upsertIdentityAndDailyPrice(
            ListingEnums.Country primaryRegion,
            String merchantName,
            String marketPlaceDomain,
            String marketPlaceItemId,
            String ean,
            String mpn,
            String title,
            String manufacturer,
            String itemUrl,
            String merchantImageUrl,
            BigDecimal price,
            Currency currency,
            BigDecimal shippingPrice,
            Integer availableQuantity
    ) {
        marketPlaceDomain = normalizeLower(marketPlaceDomain);
        marketPlaceItemId = normalize(marketPlaceItemId);

        Instant now = Instant.now();
        ZoneId zone = ZoneId.of("Europe/Berlin");
        LocalDate day = LocalDate.ofInstant(now, zone);

        RemoteActiveListing listing = listingRepository
                .findByMarketPlaceDomainAndMarketPlaceItemID(marketPlaceDomain, marketPlaceItemId)
                .orElseGet(RemoteActiveListing::new);

        if (listing.getFirstSeenAt() == null) listing.setFirstSeenAt(now);
        listing.setLastSeenAt(now);
        listing.setStillActive(true);

        listing.setMarketPlaceDomain(marketPlaceDomain);
        listing.setMarketPlaceItemID(marketPlaceItemId);
        listing.setEan(ean);
        listing.setMpn(mpn);
        listing.setTitle(title);
        listing.setItemUrl(itemUrl);
        listing.setMerchantImageUrl(merchantImageUrl);
        listing.setProductManufacturer(manufacturer);
        listing.setMarketPlaceName(merchantName);
        listing.setPrimaryRegion(primaryRegion);

        listing = listingRepository.save(listing);

        // pro Tag genau ein Preis-Snapshot
        ListingPricePoint snap = priceRepository
                .findByListingAndSnapshotDate(listing, day)
                .orElseGet(ListingPricePoint::new);

        snap.setListing(listing);
        snap.setSnapshotDate(day);
        snap.setCapturedAt(now);
        snap.setPrice(price);
        snap.setCurrency(currency);
        snap.setShippingPrice(shippingPrice);
        snap.setAvailableQuantity(availableQuantity);
        priceRepository.save(snap);
        return listing;
    }

    private record PendingLink(String ean, String mpn, String img) {
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static String normalizeLower(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t.toLowerCase();
    }
}
