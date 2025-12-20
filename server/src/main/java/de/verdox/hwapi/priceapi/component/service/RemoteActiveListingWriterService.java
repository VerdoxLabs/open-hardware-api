package de.verdox.hwapi.priceapi.component.service;

import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.model.HardwareSpec;
import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.priceapi.model.ListingEnums;
import de.verdox.hwapi.priceapi.model.ListingPricePoint;
import de.verdox.hwapi.priceapi.model.RemoteActiveListing;
import de.verdox.hwapi.priceapi.repository.ListingPricePointRepository;
import de.verdox.hwapi.priceapi.repository.RemoteActiveListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RemoteActiveListingWriterService {

    private final RemoteActiveListingRepository listingRepository;
    private final ListingPricePointRepository priceRepository;
    private final HardwareSpecService hardwareSpecService;

    // Dedupe: pro (ean|mpn) nur ein Update merken
    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();

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

        String nean = normalize(ean);
        String nmpn = normalize(mpn);
        String nimg = normalize(merchantImageUrl);

        if (nean != null || nmpn != null) {
            pendingLinks.put(nean + "|" + nmpn, new PendingLink(nean, nmpn, nimg));
        }

        return listing;
    }

    // Alle X ms: alles was gesammelt wurde in einem Rutsch updaten
    // (Wert kannst du per Property überschreiben)
    @Scheduled(fixedDelayString = "${hwapi.specLinkFlush.delayMs:1500}")
    @Transactional
    public void flushPendingSpecLinks() {
        if (pendingLinks.isEmpty()) return;

        // Snapshot + clear (damit upserts weiter sammeln können)
        List<PendingLink> batch = new ArrayList<>(pendingLinks.values());
        pendingLinks.clear();

        Set<String> eans = new HashSet<>();
        Set<String> mpns = new HashSet<>();
        for (PendingLink p : batch) {
            if (p.ean != null) eans.add(p.ean);
            if (p.mpn != null) mpns.add(p.mpn);
        }
        if (eans.isEmpty() && mpns.isEmpty()) return;

        // 1x Batch-Fetch
        List<HardwareSpec<?>> specs = hardwareSpecService.findAllByEANOrMPN(Stream.concat(eans.stream(), mpns.stream()).toList());

        // Indizes bauen
        Map<String, HardwareSpec<?>> byEan = new HashMap<>();
        Map<String, HardwareSpec<?>> byMpn = new HashMap<>();
        for (HardwareSpec<?> s : specs) {
            if (s.getEANs() != null) {
                for (String e : s.getEANs()) {
                    String ne = normalize(e);
                    if (ne != null) byEan.putIfAbsent(ne, s);
                }
            }
            if (s.getMPNs() != null) {
                for (String m : s.getMPNs()) {
                    String nm = normalize(m);
                    if (nm != null) byMpn.putIfAbsent(nm, s);
                }
            }
        }

        // Updates anwenden
        Set<HardwareSpec<?>> changed = new HashSet<>();
        for (PendingLink p : batch) {
            if (p.mpn != null) {
                HardwareSpec<?> s = byMpn.get(p.mpn);
                if (s != null) {
                    boolean dirty = false;
                    if (p.ean != null && !s.getEANs().contains(p.ean)) { s.getEANs().add(p.ean); dirty = true; }
                    if (p.img != null && !s.getPictureUrls().contains(p.img)) { s.getPictureUrls().add(p.img); dirty = true; }
                    if (dirty) changed.add(s);
                }
            }
            if (p.ean != null) {
                HardwareSpec<?> s = byEan.get(p.ean);
                if (s != null) {
                    boolean dirty = false;
                    if (p.mpn != null && !s.getMPNs().contains(p.mpn)) { s.getMPNs().add(p.mpn); dirty = true; }
                    if (p.img != null && !s.getPictureUrls().contains(p.img)) { s.getPictureUrls().add(p.img); dirty = true; }
                    if (dirty) changed.add(s);
                }
            }
        }

        if (!changed.isEmpty()) {
            hardwareSpecService.saveHardwareBatch(changed);
        }
    }

    private static final class PendingLink {
        final String ean;
        final String mpn;
        final String img;

        private PendingLink(String ean, String mpn, String img) {
            this.ean = ean;
            this.mpn = mpn;
            this.img = img;
        }
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
