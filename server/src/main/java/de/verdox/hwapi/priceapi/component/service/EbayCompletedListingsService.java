package de.verdox.hwapi.priceapi.component.service;

import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.hardwareapi.component.service.ScrapingService;
import de.verdox.hwapi.model.HardwareSpec;
import de.verdox.hwapi.model.dto.PricePointUploadDto;
import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.priceapi.io.ebay.EbayScraper;
import de.verdox.hwapi.priceapi.io.ebay.EbaySoldItem;
import de.verdox.hwapi.priceapi.io.ebay.api.EbayCategory;
import de.verdox.hwapi.priceapi.io.ebay.api.EbayMarketplace;
import de.verdox.hwapi.priceapi.model.PriceLookupBlock;
import de.verdox.hwapi.priceapi.model.RemoteSoldItem;
import de.verdox.hwapi.priceapi.repository.PriceLookupBlockRepository;
import de.verdox.hwapi.priceapi.repository.RemoteSoldItemRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@Transactional
public class EbayCompletedListingsService {
    private static final Logger LOGGER = Logger.getLogger(EbayCompletedListingsService.class.getName());

    public enum PriceLookupStatus {
        FOUND,
        NOT_FOUND_CACHED_24H,
        NOT_FOUND
    }

    public record PriceLookupResult(
            PriceLookupStatus status,
            String ean,
            Currency currency,
            BigDecimal value
    ) {
    }

    private record SpecLookupResult(
            HardwareSpec<?> spec,
            String canonicalEan // worunter wir Preise & Cache führen
    ) {
    }

    private SpecLookupResult resolveSpecAndCanonicalEan(String identifier) {
        identifier = normalize(identifier);
        if (identifier == null || identifier.isBlank()) {
            return null;
        }

        // wichtig: EAN ODER MPN
        long start = System.currentTimeMillis();
        HardwareSpec<?> spec = hardwareSpecService.findByEANOrMPN(identifier);
        if (spec == null) {
            // kein Spec gefunden → wir fallen auf "identifier" zurück, damit du
            // im Zweifel trotzdem etwas speichern kannst
            return new SpecLookupResult(null, identifier);
        }
        long end = System.currentTimeMillis() - start;
        if(end >= Duration.ofMillis(500).toMillis()) {
            LOGGER.info("\tTook ("+end+" ms) to find the hardware. We need more optimization!");
        }

        List<String> eans = (spec.getEANs() != null && !spec.getEANs().isEmpty()) ? List.copyOf(spec.getEANs()) : List.copyOf(spec.getMPNs());
        String canonicalEan = !eans.isEmpty() ? normalize(eans.getFirst()) : identifier;

        return new SpecLookupResult(spec, canonicalEan);
    }

    private final EbayAPITrackActiveListingsService ebayAPITrackActiveListingsService;
    private final PriceLookupBlockRepository priceLookupBlockRepository;
    private final RemoteSoldItemRepository repo;
    private final PricePointSyncService pricePointSyncService;
    private final EbayScraper ebayBackgroundScraper = new EbayScraper("background_job");
    private final EbayScraper ebayInstant = new EbayScraper("instant_service");
    private final HardwareSpecService hardwareSpecService;
    private final Map<String, CompletableFuture<Void>> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public EbayCompletedListingsService(EbayAPITrackActiveListingsService ebayAPITrackActiveListingsService, PriceLookupBlockRepository priceLookupBlockRepository, RemoteSoldItemRepository repo, PricePointSyncService pricePointSyncService, HardwareSpecService hardwareSpecService) {
        this.ebayAPITrackActiveListingsService = ebayAPITrackActiveListingsService;
        this.priceLookupBlockRepository = priceLookupBlockRepository;
        this.repo = repo;
        this.pricePointSyncService = pricePointSyncService;
        this.hardwareSpecService = hardwareSpecService;
    }

    // --------------------------
    // Public API
    // --------------------------

    /**
     * Batch-Upload (idempotent). Rückgabe = nur tatsächlich eingefügte Entities (ohne IDs).
     */
    public List<RemoteSoldItem> createAll(Collection<PricePointUploadDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return List.of();
        List<RemoteSoldItem> inserted = new ArrayList<>();

        for (PricePointUploadDto dto : dtos) {
            save(dto);
        }
        return inserted;
    }

    /**
     * Durchschnitt der letzten Monate (rollierend bis heute).
     */
    public Optional<BigDecimal> getCurrentAveragePriceForEan(String ean, Currency currency, int monthSince) {
        LocalDate from = LocalDate.now().minusMonths(normalizeMonths(monthSince));
        return repo.findAveragePriceSinceByCurrency(ean, from, currency).map(d -> BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Komplette Serie.
     */
    public List<RemoteSoldItemRepository.PricePoint> getAllPricesForEan(String ean) {
        return repo.findPriceSeriesByEan(ean);
    }

    /**
     * Serie seit X Monaten.
     */
    public List<RemoteSoldItemRepository.PricePoint> getRecentPricesForEan(String ean, int monthSince) {
        LocalDate from = LocalDate.now().minusMonths(normalizeMonths(monthSince));
        return repo.findPriceSeriesByEanSince(ean, from);
    }

    /**
     * On-Demand Lookup für einen einzelnen EAN + Currency.
     * Wird vom Frontend-Button ausgelöst.
     */
    public PriceLookupResult lookupPriceNow(String identifier, Currency currency) {
        identifier = normalize(identifier);
        if (identifier == null || identifier.isBlank()) {
            return new PriceLookupResult(PriceLookupStatus.NOT_FOUND, null, currency, null);
        }

        // 0) Spec & kanonische EAN bestimmen (EAN oder MPN ist egal)
        SpecLookupResult specResult = resolveSpecAndCanonicalEan(identifier);
        if (specResult == null) {
            return new PriceLookupResult(PriceLookupStatus.NOT_FOUND, identifier, currency, null);
        }

        String canonicalEan = specResult.canonicalEan();
        HardwareSpec<?> spec = specResult.spec();

        // 1) Negativ-Cache prüfen (pro canonicalEan + Currency)
        Optional<PriceLookupBlock> blockOpt = priceLookupBlockRepository.findByEanAndCurrency(canonicalEan, currency);
        if (blockOpt.isPresent() && blockOpt.get().getBlockedUntil().isAfter(Instant.now())) {
            return new PriceLookupResult(PriceLookupStatus.NOT_FOUND_CACHED_24H, canonicalEan, currency, null);
        }

        // 2) DB prüfen (letzte 3 Monate)
        Optional<BigDecimal> existing = getCurrentAveragePriceForEan(canonicalEan, currency, 3);

        if (existing.isPresent()) {
            return new PriceLookupResult(PriceLookupStatus.FOUND, canonicalEan, currency, existing.get());
        }


        // 3) Noch nichts: einen einmaligen Job für diese Spec+Currency starten/verwenden
        String jobKey = canonicalEan + "|" + currency.name();
        CompletableFuture<Void> jobFuture = jobs.computeIfAbsent(jobKey, key ->
                CompletableFuture.runAsync(() -> {
                    // 3.1 completed listings via Scraper (DE + US)
                    try {
                        Set<RemoteSoldItem> soldItems = fetchDataFromAllEbayMarketPlacesOnDemand(canonicalEan);
                        for (RemoteSoldItem remoteSoldItem : soldItems) {
                            save(
                                    remoteSoldItem.getMarketPlaceDomain(),
                                    remoteSoldItem.getMarketPlaceItemID(),
                                    canonicalEan,
                                    remoteSoldItem.getSellPrice(),
                                    remoteSoldItem.getCurrency(),
                                    remoteSoldItem.getSellDate()
                            );
                        }
                    } catch (Exception e) {
                        LOGGER.severe("Could not fetch completed listings");
                    }

                    // 3.2 aktive Listings via API (falls Spec bekannt)
                    if (spec != null) {
                        @SuppressWarnings("unchecked")
                        Class<? extends HardwareSpec<?>> clazz =
                                (Class<? extends HardwareSpec<?>>) spec.getClass();
                        try {
                            ebayAPITrackActiveListingsService.fetchActiveListings(
                                    spec.getEANs(),
                                    spec.getMPNs(),
                                    Set.of(currency),
                                    EbayMarketplace.GERMANY,
                                    clazz
                            );

                        } catch (Exception e) {
                            LOGGER.severe("Could not fetch active listings");
                        }
                    }
                }, executorService).whenComplete((r, t) -> {
                    // Job nach Abschluss aus der Map werfen
                    jobs.remove(key);
                })
        );

        // dieser Request wartet auf den ersten Job (wenn er schon läuft)
        try {
            jobFuture.join();
        } catch (CompletionException ex) {
            ScrapingService.LOGGER.log(Level.WARNING, "Error during lookup job for " + canonicalEan, ex);
        }

        // 4) Danach erneut DB prüfen
        existing = getCurrentAveragePriceForEan(canonicalEan, currency, 3);
        if (existing.isPresent()) {
            return new PriceLookupResult(PriceLookupStatus.FOUND, canonicalEan, currency, existing.get());
        }

        // 5) Immer noch nichts → 24h blocken
        Instant blockedUntil = Instant.now().plus(24, ChronoUnit.HOURS);

        PriceLookupBlock block = priceLookupBlockRepository
                .findByEanAndCurrency(canonicalEan, currency)
                .orElseGet(PriceLookupBlock::new);

        block.setEan(canonicalEan);
        block.setCurrency(currency);
        block.setBlockedUntil(blockedUntil);
        priceLookupBlockRepository.save(block);

        return new PriceLookupResult(PriceLookupStatus.NOT_FOUND, canonicalEan, currency, null);
    }

    /**
     * On-Demand-Variante deiner bestehenden Scraper-Logik.
     * Nutzt ebayInstant statt background-Scraper.
     */
    public Set<RemoteSoldItem> fetchDataFromAllEbayMarketPlacesOnDemand(String ean) {
        Set<RemoteSoldItem> remoteItems = new HashSet<>();
        remoteItems.addAll(fetchDataFromEbay(ebayInstant, EbayMarketplace.GERMANY, ean));
        remoteItems.addAll(fetchDataFromEbay(ebayInstant, EbayMarketplace.USA, ean));
        return remoteItems;
    }

    // --------------------------
    // Background Queue
    // --------------------------

    // --------------------------
    // Internals
    // --------------------------

    /**
     * Scrape alle gewünschten eBay-Marktplätze.
     */
    private Set<RemoteSoldItem> fetchDataFromAllEbayMarketPlaces(String EAN, boolean background) {

        Set<RemoteSoldItem> remoteItems = new HashSet<>();
        EbayScraper ebayScraper = background ? ebayBackgroundScraper : ebayInstant;

        remoteItems.addAll(fetchDataFromEbay(ebayScraper, EbayMarketplace.GERMANY, EAN));
        remoteItems.addAll(fetchDataFromEbay(ebayScraper, EbayMarketplace.USA, EAN));

        return remoteItems;
    }

    private final Set<String> bufferA = ConcurrentHashMap.newKeySet();
    private final Set<String> bufferB = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean currentBuffer = new AtomicBoolean(false);

    public void queueForPriceFetch(String EAN) {
        if (EAN == null || EAN.isBlank()) return;
        if (currentBuffer.get())
            bufferA.add(EAN);
        else
            bufferB.add(EAN);
    }

    @Scheduled(timeUnit = TimeUnit.MINUTES, fixedDelay = 1)
    public void travelQueue() {
        Set<String> setToTravel = currentBuffer.get() ? bufferB : bufferA;
        currentBuffer.set(!currentBuffer.get());
        int count = setToTravel.size();
        if (count > 0) {
            ScrapingService.LOGGER.log(Level.INFO, "Collecting prices from ebay marketplaces for " + count + " products");
        }
        int elementsWithPrices = 0;
        for (String EAN : setToTravel) {
            var result = fetchDataFromAllEbayMarketPlaces(EAN, true);
            if (!result.isEmpty()) {
                elementsWithPrices++;
            }
        }
        if (elementsWithPrices > 0) {
            ScrapingService.LOGGER.log(Level.INFO, "Collected prices for " + elementsWithPrices + " / " + count + " products.");
        }
    }

    private Set<RemoteSoldItem> fetchDataFromEbay(EbayScraper ebayScraper, EbayMarketplace ebayMarketplace, String EAN) {
        try {
            Set<RemoteSoldItem> remoteItems = new HashSet<>();
            final String ean = normalize(EAN);
            HardwareSpec<?> hardwareSpec = hardwareSpecService.findByEANOrMPN(EAN);
            if (hardwareSpec == null) {
                return Set.of();
            }

            Class<? extends HardwareSpec<?>> clazz = (Class<? extends HardwareSpec<?>>) hardwareSpec.getClass();
            EbayCategory ebayCategory = EbayCategory.fromType(clazz);
            if (ebayCategory == null) {
                return Set.of();
            }


            List<EbaySoldItem> fetched = new ArrayList<>();
            fetched.addAll(ebayScraper.fetchByEan(ebayMarketplace, ean, ebayCategory, 1));

            for (EbaySoldItem ebaySoldItem : fetched) {
                var saved = save(ebayMarketplace.getDomain(), ebaySoldItem.itemId(), EAN, ebaySoldItem.price().value(), ebaySoldItem.price().currency(), ebaySoldItem.soldDate());
                if (saved == null) continue;
                remoteItems.add(saved);
            }
            return remoteItems;
        } catch (Throwable e) {
            ScrapingService.LOGGER.log(Level.FINE, "Could not scrape price for " + EAN + " on " + ebayMarketplace, e);
            return Set.of();
        }
    }

    private synchronized RemoteSoldItem save(String marketPlaceDomain, String marketPlaceItemID, String ean, BigDecimal sellPrice, Currency currency, LocalDate sellDate) {

        marketPlaceDomain = normalizeLower(marketPlaceDomain);
        marketPlaceItemID = normalize(marketPlaceItemID);
        ean = normalize(ean);
        sellPrice = normalizePrice(sellPrice);

        UUID derived = RemoteSoldItem.deriveUUID(marketPlaceDomain, marketPlaceItemID, ean, sellPrice, currency, sellDate);
        if (repo.findById(derived).isPresent()) {
            return null;
        }
        return repo.save(new RemoteSoldItem(marketPlaceDomain, marketPlaceItemID, ean, sellPrice, currency, sellDate));
    }

    private void save(PricePointUploadDto pricePointUploadDto) {
        save(pricePointUploadDto.marketPlaceDomain(), pricePointUploadDto.marketPlaceItemID(), pricePointUploadDto.EAN(), pricePointUploadDto.sellPrice(), pricePointUploadDto.currency(), pricePointUploadDto.sellDate());
    }

    private static int normalizeMonths(int monthsSince) {
        if (monthsSince <= 0) return 3;
        return Math.min(monthsSince, 24);
    }

    private static String normalize(String s) {
        return s == null ? null : s.trim();
    }

    private static String normalizeLower(String s) {
        return s == null ? null : s.trim().toLowerCase();
    }

    private static BigDecimal normalizePrice(BigDecimal p) {
        return p == null ? null : p.setScale(2, RoundingMode.HALF_UP);
    }

    private static String key(String domain, String itemId, String ean, BigDecimal price, String currency, LocalDate date) {
        return stringOrEmpty(domain) + "|" + stringOrEmpty(itemId) + "|" + stringOrEmpty(ean) + "|" + (price == null ? "" : price.stripTrailingZeros().toPlainString()) + "|" + stringOrEmpty(currency) + "|" + (date == null ? "" : date.toString());
    }

    private static String stringOrEmpty(String s) {
        return s == null ? "" : s;
    }
}
