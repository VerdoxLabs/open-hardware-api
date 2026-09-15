package de.verdox.hwapi.pricing.application.ebay;

import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.ingestion.ScrapingService;
import de.verdox.hwapi.configuration.ScrapingEnabled;
import de.verdox.hwapi.catalog.domain.HardwareSpec;
import de.verdox.hwapi.pricing.api.PricePointUploadDto;
import de.verdox.hwapi.catalog.domain.values.Currency;
import de.verdox.hwapi.catalog.domain.values.ItemCondition;
import de.verdox.hwapi.identity.IdentifierNormalizer;
import de.verdox.hwapi.pricing.application.PricePointSyncService;
import de.verdox.hwapi.pricing.sources.ebay.EbayScraper;
import de.verdox.hwapi.pricing.sources.ebay.EbayListingDetails;
import de.verdox.hwapi.pricing.sources.ebay.EbaySoldItem;
import de.verdox.hwapi.integration.client.ebay.EbayCategory;
import de.verdox.hwapi.integration.client.ebay.EbayMarketplace;
import de.verdox.hwapi.pricing.model.RemoteSoldItem;
import de.verdox.hwapi.pricing.model.EbayMatchStatus;
import de.verdox.hwapi.pricing.repository.PriceLookupBlockRepository;
import de.verdox.hwapi.pricing.repository.RemoteSoldItemRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import java.util.regex.Pattern;

@Service
@Transactional
public class EbayCompletedListingsService {
    private static final Logger LOGGER = Logger.getLogger(EbayCompletedListingsService.class.getName());
    private static final Duration EBAY_REQUEST_INTERVAL = Duration.ofSeconds(10);

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
        if (end >= Duration.ofMillis(500).toMillis()) {
            LOGGER.info("\tTook (" + end + " ms) to find the hardware. We need more optimization!");
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
    private final ScrapingEnabled scrapingEnabled;
    private final Map<String, CompletableFuture<Void>> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final BlockingQueue<UUID> ebayLookupQueue = new LinkedBlockingQueue<>();
    private final Map<UUID, EbayLookupJob> ebayLookupJobs = new ConcurrentHashMap<>();
    private final Map<String, UUID> activeEbayLookupsByIdentifier = new ConcurrentHashMap<>();
    private final Object ebayRateLimitLock = new Object();
    private long nextEbayRequestAtMillis;

    private static final class EbayLookupJob {
        private final UUID id;
        private final String identifier;
        private volatile String status = "QUEUED";
        private volatile int completedRegions;
        private volatile String currentRegion;
        private volatile String error;
        private final List<EbayRegionResult> results = new CopyOnWriteArrayList<>();

        private EbayLookupJob(UUID id, String identifier) {
            this.id = id;
            this.identifier = identifier;
        }
    }

    public record EbayRegionResult(String marketplace, String domain, String status, int listings,
                                   int verifiedListings, int highConfidenceListings, int rejectedListings,
                                   String error) {}

    public record EbayLookupJobResponse(UUID jobId, String identifier, String status,
                                        int totalRegions, int completedRegions, String currentRegion,
                                        List<EbayRegionResult> results, String error) {}

    private record EbayMatch(EbayMatchStatus status, String matchedEan, String matchedMpn, String reason) {}
    private static final Set<String> BUNDLE_TERMS = Set.of("bundle", "combo", "set", "komplett pc", "gaming pc", "desktop pc", "laptop", "notebook", "mainboard", "motherboard", "kuehler", "kühler", "cooler", "heatsink", "box only", "verpackung");

    public EbayCompletedListingsService(EbayAPITrackActiveListingsService ebayAPITrackActiveListingsService, PriceLookupBlockRepository priceLookupBlockRepository, RemoteSoldItemRepository repo, PricePointSyncService pricePointSyncService, HardwareSpecService hardwareSpecService, ScrapingEnabled scrapingEnabled) {
        this.ebayAPITrackActiveListingsService = ebayAPITrackActiveListingsService;
        this.priceLookupBlockRepository = priceLookupBlockRepository;
        this.repo = repo;
        this.pricePointSyncService = pricePointSyncService;
        this.hardwareSpecService = hardwareSpecService;
        this.scrapingEnabled = scrapingEnabled;
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


    /**
     * On-Demand-Variante deiner bestehenden Scraper-Logik.
     * Nutzt ebayInstant statt background-Scraper.
     */
    public Set<RemoteSoldItem> fetchDataFromAllEbayMarketPlacesOnDemand(String ean) {
        return fetchDataFromAllEbayMarketPlaces(ean, false);
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
    public Set<RemoteSoldItem> fetchDataFromAllEbayMarketPlaces(String EAN, boolean background) {
        if (!scrapingEnabled.isEnabled()) return Set.of();
        Set<RemoteSoldItem> remoteItems = new HashSet<>();
        EbayScraper ebayScraper = background ? ebayBackgroundScraper : ebayInstant;

        for (EbayMarketplace marketplace : EbayMarketplace.values()) {
            remoteItems.addAll(fetchDataFromEbay(ebayScraper, marketplace, EAN));
        }

        return remoteItems;
    }

    public List<EbayMarketplace> getSupportedMarketplaces() {
        return List.of(EbayMarketplace.values());
    }

    public UUID queueEbayLookup(String identifier) {
        String normalized = Optional.ofNullable(IdentifierNormalizer.mpn(identifier)).orElse(normalize(identifier));
        if (normalized == null || normalized.isBlank()) throw new IllegalArgumentException("EAN oder MPN fehlt");
        UUID activeJobId = activeEbayLookupsByIdentifier.get(normalized);
        EbayLookupJob activeJob = activeJobId == null ? null : ebayLookupJobs.get(activeJobId);
        if (activeJob != null && ("QUEUED".equals(activeJob.status) || "RUNNING".equals(activeJob.status))) return activeJobId;
        UUID jobId = UUID.randomUUID();
        ebayLookupJobs.put(jobId, new EbayLookupJob(jobId, normalized));
        activeEbayLookupsByIdentifier.put(normalized, jobId);
        ebayLookupQueue.add(jobId);
        return jobId;
    }

    public EbayLookupJobResponse getEbayLookupJob(UUID jobId) {
        EbayLookupJob job = ebayLookupJobs.get(jobId);
        if (job == null) return null;
        return new EbayLookupJobResponse(job.id, job.identifier, job.status, EbayMarketplace.values().length,
                job.completedRegions, job.currentRegion, List.copyOf(job.results), job.error);
    }

    @PostConstruct
    void startEbayLookupWorker() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    runEbayLookup(ebayLookupQueue.take());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable e) {
                    LOGGER.log(Level.SEVERE, "Unexpected eBay lookup worker failure", e);
                }
            }
        });
    }

    private void runEbayLookup(UUID jobId) {
        EbayLookupJob job = ebayLookupJobs.get(jobId);
        if (job == null) return;
        job.status = "RUNNING";
        for (EbayMarketplace marketplace : EbayMarketplace.values()) {
            job.currentRegion = marketplace.name();
            try {
                Set<RemoteSoldItem> found = fetchDataFromEbay(ebayInstant, marketplace, job.identifier);
                int verified = (int) found.stream().filter(item -> item.getEbayMatchStatus() == EbayMatchStatus.VERIFIED).count();
                int likely = (int) found.stream().filter(item -> item.getEbayMatchStatus() == EbayMatchStatus.HIGH_CONFIDENCE).count();
                int rejected = (int) found.stream().filter(item -> item.getEbayMatchStatus() == EbayMatchStatus.REJECTED).count();
                job.results.add(new EbayRegionResult(marketplace.name(), marketplace.getDomain(), "COMPLETED", found.size(), verified, likely, rejected, null));
            } catch (Throwable e) {
                job.results.add(new EbayRegionResult(marketplace.name(), marketplace.getDomain(), "FAILED", 0, 0, 0, 0, e.getMessage()));
            }
            job.completedRegions++;
        }
        job.currentRegion = null;
        job.status = "COMPLETED";
        activeEbayLookupsByIdentifier.remove(job.identifier, jobId);
    }

    private void awaitEbayRateLimit() throws InterruptedException {
        synchronized (ebayRateLimitLock) {
            long waitMillis = nextEbayRequestAtMillis - System.currentTimeMillis();
            if (waitMillis > 0) Thread.sleep(waitMillis);
            nextEbayRequestAtMillis = System.currentTimeMillis() + EBAY_REQUEST_INTERVAL.toMillis();
        }
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
        if (!scrapingEnabled.isEnabled()) return;
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

            Map<String, EbaySoldItem> candidates = new LinkedHashMap<>();
            for (String searchTerm : buildSearchTerms(hardwareSpec)) {
                awaitEbayRateLimit();
                ebayScraper.fetchByEan(ebayMarketplace, searchTerm, ebayCategory, 1).stream()
                        .filter(item -> isCandidateTitle(item.title(), hardwareSpec))
                        .forEach(item -> candidates.putIfAbsent(item.itemId(), item));
            }

            // Individual item pages are expensive and rate-limited as well. Five precise
            // candidates per marketplace provide evidence without turning one lookup into a crawl.
            for (EbaySoldItem ebaySoldItem : candidates.values().stream().limit(5).toList()) {
                awaitEbayRateLimit();
                EbayListingDetails details = ebayScraper.fetchListingDetails(ebayMarketplace, ebaySoldItem.itemId());
                EbayMatch match = matchListing(hardwareSpec, ebaySoldItem.title(), details);
                if (ebaySoldItem.price() == null || ebaySoldItem.price().value() == null) continue;
                var saved = save(ebayMarketplace.getDomain(), ebaySoldItem.condition(), ebaySoldItem.itemId(), ean, ebaySoldItem.price().value(), ebaySoldItem.price().currency(), ebaySoldItem.soldDate(), ebaySoldItem.title(), match);
                if (saved == null) continue;
                remoteItems.add(saved);
            }
            return remoteItems;
        } catch (Throwable e) {
            ScrapingService.LOGGER.log(Level.FINE, "Could not scrape price for " + EAN + " on " + ebayMarketplace, e);
            return Set.of();
        }
    }

    private List<String> buildSearchTerms(HardwareSpec<?> spec) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        spec.getEANs().stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).limit(2).forEach(terms::add);
        String sharedMpn = sharedMpnStem(spec.getMPNs());
        if (sharedMpn != null) terms.add(sharedMpn);
        else spec.getMPNs().stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).limit(2).forEach(terms::add);
        terms.add((spec.getManufacturer() + " " + spec.getModel()).trim());
        return terms.stream().filter(s -> !s.isBlank()).limit(4).toList();
    }

    private static String sharedMpnStem(Set<String> mpns) {
        List<String> values = mpns == null ? List.of() : mpns.stream().filter(Objects::nonNull).map(IdentifierNormalizer::mpn).filter(Objects::nonNull).sorted().toList();
        if (values.isEmpty()) return null;
        String prefix = values.getFirst();
        for (String value : values) {
            int limit = Math.min(prefix.length(), value.length()), i = 0;
            while (i < limit && prefix.charAt(i) == value.charAt(i)) i++;
            prefix = prefix.substring(0, i);
        }
        return prefix.length() >= 6 ? prefix : null;
    }

    private static boolean isCandidateTitle(String title, HardwareSpec<?> spec) {
        String normalizedTitle = normalizeComparable(title);
        if (normalizedTitle.isBlank() || containsBundleTerm(normalizedTitle)) return false;
        if (spec.getMPNs().stream().map(IdentifierNormalizer::mpn).filter(Objects::nonNull).anyMatch(normalizedTitle::contains)) return true;
        String model = normalizeComparable(spec.getModel());
        return model.length() >= 5 && normalizedTitle.contains(model);
    }

    private static EbayMatch matchListing(HardwareSpec<?> spec, String title, EbayListingDetails details) {
        String titleNormalized = normalizeComparable(title);
        if (containsBundleTerm(titleNormalized)) return new EbayMatch(EbayMatchStatus.REJECTED, null, null, "Bundle- oder Systembegriff im Titel");
        Set<String> knownEans = spec.getEANs().stream().map(IdentifierNormalizer::gtin).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<String> knownMpns = spec.getMPNs().stream().map(IdentifierNormalizer::mpn).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<String> foundEans = new HashSet<>(), foundMpns = new HashSet<>();
        String brand = null;
        for (Map.Entry<String, String> entry : details.itemSpecifics().entrySet()) {
            String key = normalizeComparable(entry.getKey());
            String value = entry.getValue();
            if (key.contains("GTIN") || key.contains("EAN") || key.contains("UPC")) {
                java.util.regex.Matcher matcher = Pattern.compile("\\d{8}|\\d{12,14}").matcher(value);
                while (matcher.find()) { String gtin = IdentifierNormalizer.gtin(matcher.group()); if (gtin != null) foundEans.add(gtin); }
            }
            if (key.contains("MPN") || key.contains("HERSTELLERNUMMER") || key.contains("PARTNUMBER")) {
                String mpn = IdentifierNormalizer.mpn(value); if (mpn != null) foundMpns.add(mpn);
            }
            if (key.contains("MARKE") || key.contains("BRAND") || key.contains("HERSTELLER")) brand = value;
        }
        if (brand != null && !normalizeComparable(brand).contains(normalizeComparable(spec.getManufacturer()))) {
            return new EbayMatch(EbayMatchStatus.REJECTED, null, null, "Abweichender Hersteller: " + brand);
        }
        for (String ean : foundEans) if (knownEans.contains(ean)) return new EbayMatch(EbayMatchStatus.VERIFIED, ean, null, "Exakte EAN/GTIN in eBay-Artikelmerkmalen");
        for (String mpn : foundMpns) if (knownMpns.contains(mpn)) return new EbayMatch(EbayMatchStatus.VERIFIED, null, mpn, "Exakte MPN in eBay-Artikelmerkmalen");
        if (!foundEans.isEmpty() || !foundMpns.isEmpty()) return new EbayMatch(EbayMatchStatus.REJECTED, null, null, "Artikelmerkmale enthalten eine abweichende EAN oder MPN");
        String model = normalizeComparable(spec.getModel());
        if (model.length() >= 5 && titleNormalized.contains(model)) return new EbayMatch(EbayMatchStatus.HIGH_CONFIDENCE, null, null, "Exakter Modellname im Titel, aber keine strukturierte Kennung");
        return new EbayMatch(EbayMatchStatus.REJECTED, null, null, "Keine verifizierbare Produktkennung");
    }

    private static boolean containsBundleTerm(String text) {
        return BUNDLE_TERMS.stream().map(EbayCompletedListingsService::normalizeComparable).anyMatch(text::contains);
    }

    private static String normalizeComparable(String value) {
        return value == null ? "" : java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private synchronized RemoteSoldItem save(String marketPlaceDomain, List<String> condition, String marketPlaceItemID, String ean, BigDecimal sellPrice, Currency currency, LocalDate sellDate, String listingTitle, EbayMatch match) {

        marketPlaceDomain = normalizeLower(marketPlaceDomain);
        marketPlaceItemID = normalize(marketPlaceItemID);
        ean = normalize(ean);
        sellPrice = normalizePrice(sellPrice);

        UUID derived = RemoteSoldItem.deriveUUID(marketPlaceDomain, marketPlaceItemID, ean, sellPrice, currency, sellDate);
        var found = repo.findById(derived);

        ItemCondition itemCondition = null;
        for (String conditionString : condition) {

            if (
                    conditionString.toLowerCase().contains("gebraucht") ||
                            conditionString.toLowerCase().contains("open box") ||
                            conditionString.toLowerCase().contains("used") ||
                            conditionString.toLowerCase().contains("pre-owned"
                            )
            ) {
                itemCondition = ItemCondition.USED;
            } else if (conditionString.toLowerCase().contains("refurbished")) {
                itemCondition = ItemCondition.REFURBISHED;
            } else if (conditionString.toLowerCase().contains("new") || conditionString.toLowerCase().contains("neu")) {
                itemCondition = ItemCondition.NEW;
            } else if (conditionString.toLowerCase().contains("defective") || conditionString.toLowerCase().contains("defekt")) {
                itemCondition = ItemCondition.DEFECTIVE;
            } else {
                continue;
            }
        }

        if (itemCondition == null) {
            return null;
        }

        RemoteSoldItem itemToSave;

        if (found.isPresent()) {
            itemToSave = found.get();
            found.get().setCondition(itemCondition);
        }
        else {
            itemToSave = new RemoteSoldItem(marketPlaceDomain, marketPlaceItemID, ean, sellPrice, currency, sellDate, itemCondition);
        }
        itemToSave.setListingTitle(listingTitle);
        itemToSave.setMatchedEan(match.matchedEan());
        itemToSave.setMatchedMpn(match.matchedMpn());
        itemToSave.setEbayMatchStatus(match.status());
        itemToSave.setEbayMatchReason(match.reason());
        return repo.save(itemToSave);
    }

    private void save(PricePointUploadDto pricePointUploadDto) {
        save(pricePointUploadDto.marketPlaceDomain(), List.of(ItemCondition.USED.name()), pricePointUploadDto.marketPlaceItemID(), pricePointUploadDto.EAN(), pricePointUploadDto.sellPrice(), pricePointUploadDto.currency(), pricePointUploadDto.sellDate(), null, new EbayMatch(EbayMatchStatus.VERIFIED, pricePointUploadDto.EAN(), null, "Manuell importierter Preispunkt"));
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
