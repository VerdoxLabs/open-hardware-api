package de.verdox.hwapi.priceapi.component.service.awin;

import de.verdox.hwapi.client.admin.HardwareAdminDtos;
import de.verdox.hwapi.component.repository.HardwareSpecRepository;
import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.priceapi.component.dto.AwinProductRecord;
import de.verdox.hwapi.priceapi.component.service.RemoteActiveListingWriterService;
import de.verdox.hwapi.priceapi.model.RemoteActiveListing;
import de.verdox.hwapi.productid.ProductIdentifier;
import de.verdox.hwapi.productidregistry.ProductRegistryService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class AwinTrackActiveListingsService {

    private static final Logger LOGGER = Logger.getLogger(AwinTrackActiveListingsService.class.getSimpleName());
    private static final String SOURCE_AWIN = "AWIN";

    private final AwinFeedOverviewService awinFeedOverviewService;
    private final AwinFeedService awinFeedService;

    private final RemoteActiveListingWriterService listingWriter; // ✅ NEW

    private final ProductRegistryService productRegistryService;
    private final HardwareSpecService hardwareSpecService;
    private final HardwareSpecRepository hardwareSpecRepository;


    private final AtomicBoolean fetchRunning = new AtomicBoolean(false);
    private final AtomicReference<Instant> fetchStartedAt = new AtomicReference<>(null);
    private final AtomicReference<Instant> fetchLastFinishedAt = new AtomicReference<>(null);
    private final AtomicReference<String> fetchMessage = new AtomicReference<>("");
    private final AtomicReference<String> fetchLastError = new AtomicReference<>(null);

    private final AtomicReference<String> currentFeedAdvertiser = new AtomicReference<>(null);
    private final AtomicReference<String> currentFeedRegion = new AtomicReference<>(null);
    private final AtomicLong currentFeedIndex = new AtomicLong(0);
    private final AtomicLong totalFeeds = new AtomicLong(0);

    private final AtomicLong currentFeedProcessed = new AtomicLong(0);
    private final AtomicLong currentFeedTotal = new AtomicLong(0);

    private final AtomicLong overallProcessed = new AtomicLong(0);
    private final AtomicLong overallTotal = new AtomicLong(0);

    public HardwareAdminDtos.AwinFetchStatus getFetchStatus() {
        Instant started = fetchStartedAt.get();
        Instant lastFinished = fetchLastFinishedAt.get();

        // "Nächster Lauf" bei stündlicher Ausführung: nächste volle Stunde nach Start/Finish.
        Instant base = (started != null) ? started : (lastFinished != null ? lastFinished : Instant.now());
        Instant next = base.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS);

        return new HardwareAdminDtos.AwinFetchStatus(
                fetchRunning.get(),
                next,
                started,
                lastFinished,
                fetchMessage.get(),
                fetchLastError.get(),
                currentFeedAdvertiser.get(),
                currentFeedRegion.get(),
                currentFeedIndex.get(),
                totalFeeds.get(),
                currentFeedProcessed.get(),
                currentFeedTotal.get(),
                overallProcessed.get(),
                overallTotal.get()
        );
    }

    private Map<String, Long> mpnToSpecId = Map.of();

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    @Async
    public void runDailyAwinImport() {
        rebuildMpnIndex();
        updateFromAwinFeed();
    }

    @Transactional
    @PostConstruct
    public void init() {
        CompletableFuture.runAsync(() -> {
            rebuildMpnIndex();
            updateFromAwinFeed();
        });
    }

    private void rebuildMpnIndex() {
        List<HardwareSpecRepository.HardwareSpecMpnProjection> mappings = hardwareSpecRepository.findAllMpnMappings();
        Map<String, Long> tmp = new HashMap<>(mappings.size());

        for (HardwareSpecRepository.HardwareSpecMpnProjection p : mappings) {
            String key = normalizeMpn(p.getMpn());
            if (key == null || key.isBlank()) continue;
            tmp.putIfAbsent(key, p.getSpecId());
        }

        this.mpnToSpecId = tmp;
        LOGGER.info("MPN index built with " + mpnToSpecId.size() + " entries");
    }

    private String normalizeMpn(String mpn) {
        return mpn == null ? null : mpn.trim();
    }

    @Transactional
    public void updateFromAwinFeed() {
        long start = System.currentTimeMillis();
        LOGGER.log(Level.INFO, "Updating awin feed...");

        fetchRunning.set(true);
        fetchStartedAt.set(Instant.now());
        fetchMessage.set("Starting AWIN import…");
        fetchLastError.set(null);

        currentFeedAdvertiser.set(null);
        currentFeedRegion.set(null);
        currentFeedIndex.set(0);
        totalFeeds.set(0);

        currentFeedProcessed.set(0);
        currentFeedTotal.set(0);
        overallProcessed.set(0);
        overallTotal.set(0);

        try {
            long upserted = 0;

            List<AwinFeed> feeds = awinFeedOverviewService.loadActiveFeeds();
            totalFeeds.set(feeds.size());

            for (int i = 0; i < feeds.size(); i++) {
                AwinFeed feed = feeds.get(i);

                currentFeedIndex.set(i + 1);
                currentFeedAdvertiser.set(feed.advertiser());
                currentFeedRegion.set(
                        feed.primaryRegion() != null
                                ? feed.primaryRegion().toString()
                                : null
                );

                currentFeedProcessed.set(0);
                currentFeedTotal.set(0); // bleibt unbekannt

                fetchMessage.set(
                        "Downloading/parsing feed " + (i + 1) + "/" + feeds.size()
                                + " (" + feed.advertiser() + ")…"
                );

                LOGGER.log(Level.INFO, "Streaming feed from " + feed.advertiser());

                awinFeedService.downloadAndParseSingleFeed(feed, record -> {
                    try {
                        mapRecordToActiveListing(feed, record);
                    } catch (Exception e) {
                        fetchLastError.set(e.getMessage());
                        LOGGER.log(
                                Level.WARNING,
                                "Error while processing AWIN record "
                                        + record.getAwProductId()
                                        + " from " + feed.advertiser(),
                                e
                        );
                    }

                    currentFeedProcessed.incrementAndGet();
                    overallProcessed.incrementAndGet();
                });

                LOGGER.log(
                        Level.INFO,
                        "Finished feed from " + feed.advertiser()
                                + ", processed " + currentFeedProcessed.get() + " records"
                );
            }

            fetchMessage.set("Done. Upserted " + upserted + " listings.");
            LOGGER.log(
                    Level.INFO,
                    "Awin Feed updated [" + upserted + "] took "
                            + (System.currentTimeMillis() - start) + "ms"
            );

        } catch (Exception e) {
            fetchLastError.set(e.getMessage());
            fetchMessage.set("Error while importing AWIN feed");
            LOGGER.log(Level.SEVERE, "Error while importing AWIN feed", e);
        } finally {
            fetchRunning.set(false);
            fetchLastFinishedAt.set(Instant.now());
            currentFeedAdvertiser.set(null);
            currentFeedRegion.set(null);
        }
    }

    private RemoteActiveListing mapRecordToActiveListing(AwinFeed awinFeed, AwinProductRecord r) {
        String marketPlaceDomain = extractDomain(
                r.getMerchantDeepLink() != null ? r.getMerchantDeepLink() : r.getAwDeepLink(),
                r.getMerchantName()
        );
        if (marketPlaceDomain == null) {
            return null;
        }

        String marketPlaceItemId = firstNonBlank(r.getAwProductId(), r.getMerchantProductId());
        if (marketPlaceItemId == null) {
            return null;
        }

        String ean = r.getEan();
        String mpn = r.getMpn();
        String title = r.getProductName();
        String itemUrl = firstNonBlank(r.getAwDeepLink(), r.getMerchantDeepLink());
        String merchantImageUrl = firstNonBlank(r.getAwImageUrl(), r.getMerchantImageUrl());

        //register(r, ean, mpn, title);

        BigDecimal price = firstNonNull(
                r.getStorePrice(),
                r.getSearchPrice(),
                r.getDisplayPrice() != null ? r.getDisplayPrice().price() : null
        );
        if (price != null) price = price.setScale(2, RoundingMode.HALF_UP);

        BigDecimal shippingPrice = r.getDeliveryCost();
        if (shippingPrice != null) shippingPrice = shippingPrice.setScale(2, RoundingMode.HALF_UP);

        Currency currency = r.getCurrency();
        if (currency == null && r.getDisplayPrice() != null) currency = r.getDisplayPrice().currency();


        return listingWriter.upsertIdentityAndDailyPrice(
                awinFeed.primaryRegion(),
                r.getMerchantName(),
                marketPlaceDomain,
                marketPlaceItemId,
                ean,
                mpn,
                title,
                r.getManufacturerName(),
                itemUrl,
                merchantImageUrl,
                price,
                currency,
                shippingPrice,
                null
        );
    }

    private void register(AwinProductRecord r, String ean, String mpn, String title) {
        // Registry
        try {
            if (ean != null && !ean.isBlank())
                productRegistryService.register(ProductIdentifier.IdentifierType.EAN, ean, SOURCE_AWIN);
            if (mpn != null && !mpn.isBlank())
                productRegistryService.register(ProductIdentifier.IdentifierType.MPN, mpn, SOURCE_AWIN);
            if (title != null && !title.isBlank()) {
                productRegistryService.register(ProductIdentifier.IdentifierType.TITLE, title, SOURCE_AWIN);
                productRegistryService.register(ProductIdentifier.IdentifierType.TITLE_NORMALIZED, title, SOURCE_AWIN);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error while registering identifiers in ProductRegistry for AWIN record: " + r, ex);
        }
    }

    private static String extractDomain(String url, String fallback) {
        if (url == null || url.isBlank()) return fallback != null ? fallback.trim().toLowerCase() : null;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isBlank()) return fallback != null ? fallback.trim().toLowerCase() : null;
            return host.toLowerCase();
        } catch (Exception e) {
            e.printStackTrace();
            return fallback != null ? fallback.trim().toLowerCase() : null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values == null) return null;
        for (T v : values) if (v != null) return v;
        return null;
    }
}
