package de.verdox.hwapi.priceapi.component.service;

import de.verdox.hwapi.client.PriceSeriesDTO;
import de.verdox.hwapi.client.PriceSeriesResponseDTO;
import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.model.HardwareSpec;
import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.model.values.ItemCondition;
import de.verdox.hwapi.priceapi.io.ebay.api.EbayCategory;
import de.verdox.hwapi.priceapi.io.ebay.api.EbayMarketplace;
import de.verdox.hwapi.priceapi.model.PriceLookupBlock;
import de.verdox.hwapi.priceapi.model.RemoteActiveListing;
import de.verdox.hwapi.priceapi.model.RemoteSoldItem;
import de.verdox.hwapi.priceapi.repository.PriceLookupBlockRepository;
import de.verdox.hwapi.priceapi.repository.RemoteActiveListingRepository;
import de.verdox.hwapi.priceapi.repository.RemoteSoldItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemPriceService {
    private static final Logger LOGGER = Logger.getLogger(ItemPriceService.class.getName());

    private static final Duration NEGATIVE_CACHE_DURATION = Duration.ofHours(24);

    private final PriceLookupBlockRepository priceLookupBlockRepository;
    private final RemoteActiveListingRepository remoteActiveListingRepository;
    private final RemoteSoldItemRepository remoteSoldItemRepository;
    private final EbayCompletedListingsService ebayCompletedListingsService;
    private final EbayAPITrackActiveListingsService ebayAPITrackActiveListingsService;

    private final Map<Long, PriceSeriesResponseDTO> jobs = new ConcurrentHashMap<>();
    private final Map<Integer, PriceSeriesResponseDTO> jobsById = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final ExecutorService backgroundFetcher = Executors.newSingleThreadExecutor();
    private final ExecutorService frontFetcher = Executors.newSingleThreadExecutor();
    private final HardwareSpecService hardwareSpecService;

    // ------------------------------------------------------------------------
    // DB-Fetch: Completed
    // ------------------------------------------------------------------------
    @Transactional
    public PriceSeriesResponseDTO fetchCompletedSeriesDataFromDB(HardwareSpec<?> hardwareSpec,
                                                                 Set<ItemCondition> conditions,
                                                                 int monthSince) {
        List<PriceSeriesDTO> result = new ArrayList<>();

        List<RemoteSoldItem> pricePointsFromSoldItems =
                remoteSoldItemRepository.findPricePoints(
                        hardwareSpec.getMPNs(),
                        hardwareSpec.getEANs(),
                        conditions,
                        monthSince
                );

        pricePointsFromSoldItems.stream()
                .collect(Collectors.groupingBy(RemoteSoldItem::getCondition))
                .forEach((itemCondition, remoteSoldItems) -> {
                    PriceSeriesDTO priceSeriesDTO =
                            new PriceSeriesDTO(itemCondition, true, new LinkedHashMap<>());

                    remoteSoldItems.stream()
                            .collect(Collectors.groupingBy(RemoteSoldItem::getCurrency))
                            .forEach((currency, soldItemsByCurrency) -> {
                                priceSeriesDTO.prices().put(
                                        currency,
                                        soldItemsByCurrency.stream()
                                                .map(remoteSoldItem ->
                                                        new PriceSeriesDTO.PricePointDTO(
                                                                remoteSoldItem.getMarketPlaceDomain(),
                                                                remoteSoldItem.getMarketPlaceItemID(),
                                                                remoteSoldItem.getSellDate()
                                                                        .atStartOfDay()
                                                                        .toInstant(ZoneOffset.UTC),
                                                                remoteSoldItem.getSellPrice()
                                                        )
                                                )
                                                .toList()
                                );
                            });

                    result.add(priceSeriesDTO);
                });

        return new PriceSeriesResponseDTO(false, result);
    }

    // ------------------------------------------------------------------------
    // DB-Fetch: Active
    // ------------------------------------------------------------------------
    @Transactional
    public PriceSeriesResponseDTO fetchActiveSeriesDataFromDB(HardwareSpec<?> hardwareSpec,
                                                              Set<ItemCondition> conditions,
                                                              int monthSince) {
        List<PriceSeriesDTO> result = new ArrayList<>();

        List<RemoteActiveListing> remoteActiveListingsForSpec =
                remoteActiveListingRepository.findPricePoints(
                        hardwareSpec.getMPNs(),
                        hardwareSpec.getEANs(),
                        conditions,
                        monthSince
                );

        remoteActiveListingsForSpec.stream()
                .collect(Collectors.groupingBy(RemoteActiveListing::getCondition))
                .forEach((itemCondition, listingsByCondition) -> {
                    PriceSeriesDTO priceSeriesDTO =
                            new PriceSeriesDTO(itemCondition, true, new LinkedHashMap<>());

                    listingsByCondition.stream()
                            .collect(Collectors.groupingBy(RemoteActiveListing::getCurrency))
                            .forEach((currency, listingsByCurrency) -> {
                                priceSeriesDTO.prices().put(
                                        currency,
                                        listingsByCurrency.stream()
                                                .map(l -> new PriceSeriesDTO.PricePointDTO(
                                                        l.getMarketPlaceDomain(),
                                                        l.getMarketPlaceItemID(),
                                                        l.getFirstSeenAt(),
                                                        l.getPrice()
                                                ))
                                                .toList()
                                );
                            });

                    result.add(priceSeriesDTO);
                });

        return new PriceSeriesResponseDTO(false, result);
    }

    // ------------------------------------------------------------------------
    // Remote-Fetch (Background-Job) + Negative Cache via PriceLookupBlock
    // ------------------------------------------------------------------------
    @Transactional
    public PriceSeriesResponseDTO fetchSeriesDataFromRemote(HardwareSpec<?> spec, boolean background) {
        // 1) Abgelaufene Blocks bereinigen -> läuft im Request-Thread mit aktiver Transaktion
        cleanupExpiredBlocks();

        PriceSeriesResponseDTO existing = jobs.get(spec.getId());
        if (existing != null) {
            // es läuft schon ein Job für diese Spec -> DTO zurückgeben
            return existing;
        }

        int ticketId = idCounter.incrementAndGet();
        PriceSeriesResponseDTO dto = new PriceSeriesResponseDTO(true, new CopyOnWriteArrayList<>());

        jobs.put(spec.getId(), dto);
        jobsById.put(ticketId, dto);

        Long specId = spec.getId();
        @SuppressWarnings("unchecked")
        Class<? extends HardwareSpec<?>> clazz = (Class<? extends HardwareSpec<?>>) spec.getClass();
        Set<String> eans = spec.getEANs() != null ? Set.copyOf(spec.getEANs()) : Set.of();
        Set<String> mpns = spec.getMPNs() != null ? Set.copyOf(spec.getMPNs()) : Set.of();

        // 2) Async-Job auf eigenem Executor
        CompletableFuture.runAsync(() -> {
            try {
                // Completed Listings
                mpns.forEach(s -> ebayCompletedListingsService.fetchDataFromAllEbayMarketPlaces(s, background));
                eans.forEach(s -> ebayCompletedListingsService.fetchDataFromAllEbayMarketPlaces(s, background));

                // Active Listings
                fetchActiveListings(eans, mpns, Set.of(Currency.EURO), EbayMarketplace.GERMANY, clazz);
                fetchActiveListings(eans, mpns, Set.of(Currency.US_DOLLAR), EbayMarketplace.USA, clazz);
                fetchActiveListings(eans, mpns, Set.of(Currency.CANADIAN_DOLLAR), EbayMarketplace.CANADA_EN, clazz);
            } catch (Throwable ex) {
                ex.printStackTrace();
            } finally {
                // wenn fertig: Job als beendet markieren
                jobs.remove(specId);
            }
        }, background ? backgroundFetcher : frontFetcher);

        return dto;
    }


    public PriceSeriesResponseDTO getJobByTicketId(int ticketId) {
        return jobsById.get(ticketId);
    }

    // ------------------------------------------------------------------------
    // Active-Listings-Remote-Fetch mit PriceLookupBlock
    // ------------------------------------------------------------------------
    @Transactional
    public Map<String, List<RemoteActiveListing>> fetchActiveListings(
            Set<String> eans,
            Set<String> mpns,
            Set<Currency> currencies,
            EbayMarketplace marketplace,
            Class<? extends HardwareSpec<?>> hardwareType
    ) {
        Map<String, List<RemoteActiveListing>> result = new HashMap<>();

        if (eans == null) eans = Set.of();
        if (mpns == null) mpns = Set.of();
        if (currencies == null || currencies.isEmpty()) {
            currencies = Set.of(Currency.EURO);
        }

        EbayCategory ebayCategory = EbayCategory.fromType(hardwareType);
        if (ebayCategory == null) {
            return result;
        }

        record KeyCurrency(String key, Currency currency) {
        }
        Set<KeyCurrency> attempted = new HashSet<>();
        Set<KeyCurrency> gotData = new HashSet<>();

        for (Currency currency : currencies) {

            // 1) EANs
            for (String ean : eans) {
                if (ean == null || ean.isBlank()) continue;
                String key = ean.trim();

                if (isBlocked(key, currency)) {
                    // innerhalb Block-Zeitraum -> kein neuer Ebay-Call
                    continue;
                }

                KeyCurrency kc = new KeyCurrency(key, currency);
                attempted.add(kc);

                List<RemoteActiveListing> listings =
                        ebayAPITrackActiveListingsService.fetchActiveBySingleIdentifier(
                                marketplace, ebayCategory, key, null, currency
                        );

                if (!listings.isEmpty()) {
                    gotData.add(kc);
                    result.computeIfAbsent(key, k -> new ArrayList<>())
                            .addAll(listings);
                }
            }

            // 2) MPNs
            for (String mpn : mpns) {
                if (mpn == null || mpn.isBlank()) continue;
                String key = mpn.trim();

                if (isBlocked(key, currency)) {
                    continue;
                }

                KeyCurrency kc = new KeyCurrency(key, currency);
                attempted.add(kc);

                List<RemoteActiveListing> listings =
                        ebayAPITrackActiveListingsService.fetchActiveBySingleIdentifier(
                                marketplace, ebayCategory, null, key, currency
                        );

                if (!listings.isEmpty()) {
                    gotData.add(kc);
                    result.computeIfAbsent(key, k -> new ArrayList<>())
                            .addAll(listings);
                }
            }
        }

        // Für alle Versuche ohne Ergebnis: negative Cache-Blocks setzen
        attempted.stream()
                .filter(kc -> !gotData.contains(kc))
                .forEach(kc -> createOrUpdateNegativeBlock(kc.key(), kc.currency()));

        return result;
    }

    // ------------------------------------------------------------------------
    // Helper: PriceLookupBlock
    // ------------------------------------------------------------------------

    @Transactional
    public void cleanupExpiredBlocks() {
        priceLookupBlockRepository.deleteByBlockedUntilBefore(Instant.now());
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(String identifier, Currency currency) {
        Instant now = Instant.now();
        return priceLookupBlockRepository.findByEanAndCurrency(identifier, currency)
                .filter(block -> block.getBlockedUntil() != null &&
                        block.getBlockedUntil().isAfter(now))
                .isPresent();
    }

    /**
     * Setzt/verlängert einen negativen Cache-Eintrag für (identifier, currency).
     * Wird aufgerufen, wenn ein Ebay-Call 0 Ergebnisse gebracht hat.
     */
    @Transactional
    public void createOrUpdateNegativeBlock(String identifier, Currency currency) {
        Instant blockedUntil = Instant.now().plus(NEGATIVE_CACHE_DURATION);

        PriceLookupBlock block = priceLookupBlockRepository
                .findByEanAndCurrency(identifier, currency)
                .orElseGet(() -> {
                    PriceLookupBlock b = new PriceLookupBlock();
                    b.setEan(identifier);
                    b.setCurrency(currency);
                    return b;
                });

        block.setBlockedUntil(blockedUntil);
        priceLookupBlockRepository.save(block);
    }

    private final Set<Long> specIdsToFetch = ConcurrentHashMap.newKeySet();

    @Async
    public void addToBackgroundJob(HardwareSpec<?> hardwareSpec) {
        specIdsToFetch.add(hardwareSpec.getId());
    }

    @Scheduled(fixedDelayString = "${sync.flush-interval-ms:5000}")
    @Transactional
    public void runBackgroundFetcher() {
        // Wenn noch ein Remote-Job läuft -> nichts Neues starten
        if (!jobs.isEmpty()) {
            return;
        }

        // Einen Spec aus der Queue holen (pro Durchlauf genau einen)
        Long specId = specIdsToFetch.stream().findFirst().orElse(null);
        if (specId == null) {
            return;
        }

        // direkt aus der Queue entfernen, damit er nicht mehrfach abgearbeitet wird
        specIdsToFetch.remove(specId);

        HardwareSpec<?> found = hardwareSpecService.findById(specId);
        if (found == null) {
            return;
        }

        // Wenn DB schon Daten hat, keinen Remote-Fetch mehr anstoßen
        PriceSeriesResponseDTO existing =
                fetchCompletedSeriesDataFromDB(found, EnumSet.allOf(ItemCondition.class), 12);
        if (existing.series() != null && !existing.series().isEmpty()) {
            return;
        }

        fetchSeriesDataFromRemote(found, true);
    }
}

