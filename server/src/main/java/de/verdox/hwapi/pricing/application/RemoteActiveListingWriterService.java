package de.verdox.hwapi.pricing.application;

import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.domain.HardwareSpec;
import de.verdox.hwapi.catalog.domain.values.Currency;
import de.verdox.hwapi.catalog.ingestion.images.ProductImageStore;
import de.verdox.hwapi.pricing.model.ListingEnums;
import de.verdox.hwapi.pricing.model.ListingPricePoint;
import de.verdox.hwapi.pricing.model.RemoteActiveListing;
import de.verdox.hwapi.pricing.repository.ListingPricePointRepository;
import de.verdox.hwapi.pricing.repository.RemoteActiveListingRepository;
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
import java.util.logging.Logger;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RemoteActiveListingWriterService {

    private final RemoteActiveListingRepository listingRepository;
    private final ListingPricePointRepository priceRepository;
    private final HardwareSpecService hardwareSpecService;
    private static final Logger LOGGER = Logger.getLogger(RemoteActiveListingWriterService.class.getName());

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

        if (nmpn != null) {
            pendingLinks.put(nmpn, new PendingLink(nean, nmpn, nimg, firstNonBlank(itemUrl, "https://www.awin.com/")));
        }

        if (nean != null) {
            pendingLinks.put(nean, new PendingLink(nean, nmpn, nimg, firstNonBlank(itemUrl, "https://www.awin.com/")));
        }

        return listing;
    }

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
        Set<String> attachedImages = new HashSet<>();
        for (PendingLink p : batch) {
            if (p.mpn != null) {
                HardwareSpec<?> s = byMpn.get(p.mpn);
                if (s != null) {
                    boolean dirty = false;
                    if (p.ean != null && !s.getEANs().contains(p.ean)) { s.getEANs().add(p.ean); dirty = true; }
                    if (p.img != null && attachedImages.add(s.getId() + "|" + p.img)) dirty |= ProductImageStore.storeExternal(p.img, s, p.sourcePage);
                    if (dirty) changed.add(s);
                }
            }
            if (p.ean != null) {
                HardwareSpec<?> s = byEan.get(p.ean);
                if (s != null) {
                    boolean dirty = false;
                    if (p.mpn != null && !s.getMPNs().contains(p.mpn)) { s.getMPNs().add(p.mpn); dirty = true; }
                    if (p.img != null && attachedImages.add(s.getId() + "|" + p.img)) dirty |= ProductImageStore.storeExternal(p.img, s, p.sourcePage);
                    if (dirty) changed.add(s);
                }
            }
        }

        if (!changed.isEmpty()) {
            hardwareSpecService.saveHardwareBatch(changed);
            LOGGER.info("Flushing info from price endpoints into hardware db for "+changed.size()+" pending articles");
        }
    }

    private record PendingLink(String ean, String mpn, String img, String sourcePage) {
    }

    /** Links images from already imported Awin listings to newly imported catalog records. */
    public void attachImagesForHardware(Collection<? extends HardwareSpec<?>> importedSpecs) {
        if (importedSpecs == null || importedSpecs.isEmpty()) return;
        Set<String> identifiers = new HashSet<>();
        for (HardwareSpec<?> spec : importedSpecs) {
            if (spec.getEANs() != null) for (String value : spec.getEANs()) {
                String normalized = HardwareSpec.normalizeEan(value);
                if (normalized != null) identifiers.add(normalized);
            }
            if (spec.getMPNs() != null) for (String value : spec.getMPNs()) {
                String normalized = HardwareSpec.normalizeMpn(value);
                if (normalized != null) identifiers.add(normalized);
            }
        }
        if (identifiers.isEmpty()) return;

        List<HardwareSpec<?>> specs = hardwareSpecService.findAllByEANOrMPN(new ArrayList<>(identifiers));
        Map<String, HardwareSpec<?>> byIdentifier = new HashMap<>();
        for (HardwareSpec<?> spec : specs) {
            if (spec.getEANs() != null) for (String value : spec.getEANs()) {
                String normalized = HardwareSpec.normalizeEan(value);
                if (normalized != null) byIdentifier.putIfAbsent(normalized, spec);
            }
            if (spec.getMPNs() != null) for (String value : spec.getMPNs()) {
                String normalized = HardwareSpec.normalizeMpn(value);
                if (normalized != null) byIdentifier.putIfAbsent(normalized, spec);
            }
        }

        Set<HardwareSpec<?>> changed = new HashSet<>();
        Set<String> attachedImages = new HashSet<>();
        for (RemoteActiveListing listing : listingRepository.findAllByMerchantImageUrlIsNotNull()) {
            HardwareSpec<?> spec = findByIdentifier(byIdentifier, listing.getEan(), listing.getMpn());
            if (spec == null || listing.getMerchantImageUrl() == null
                    || !attachedImages.add(spec.getId() + "|" + listing.getMerchantImageUrl())) continue;
            if (ProductImageStore.storeExternal(listing.getMerchantImageUrl(), spec,
                    firstNonBlank(listing.getItemUrl(), "https://www.awin.com/"))) changed.add(spec);
        }
        if (!changed.isEmpty()) {
            hardwareSpecService.saveHardwareBatch(changed);
            LOGGER.info("Attached " + changed.size() + " Awin product images to hardware specs");
        }
    }

    private static HardwareSpec<?> findByIdentifier(Map<String, HardwareSpec<?>> byIdentifier, String ean, String mpn) {
        String normalizedEan = HardwareSpec.normalizeEan(ean);
        HardwareSpec<?> spec = normalizedEan == null ? null : byIdentifier.get(normalizedEan);
        if (spec != null) return spec;
        String normalizedMpn = HardwareSpec.normalizeMpn(mpn);
        return normalizedMpn == null ? null : byIdentifier.get(normalizedMpn);
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
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
