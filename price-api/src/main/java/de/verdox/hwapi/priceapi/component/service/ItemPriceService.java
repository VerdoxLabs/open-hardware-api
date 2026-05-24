package de.verdox.hwapi.priceapi.component.service;

import de.verdox.hwapi.client.PriceSeriesDTO;
import de.verdox.hwapi.client.PriceSeriesResponseDTO;
import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.model.HardwareSpec;
import de.verdox.hwapi.model.values.ItemCondition;
import de.verdox.hwapi.priceapi.component.service.amazon.AmazonPriceService;
import de.verdox.hwapi.priceapi.component.service.amazon.AmazonTrackActiveListingsService;
import de.verdox.hwapi.priceapi.component.service.ebay.EbayCompletedListingsService;
import de.verdox.hwapi.priceapi.component.service.ebay.EbayFeedPriceService;
import de.verdox.hwapi.priceapi.model.ListingEnums;
import de.verdox.hwapi.priceapi.model.ListingPricePoint;
import de.verdox.hwapi.priceapi.model.RemoteSoldItem;
import de.verdox.hwapi.priceapi.repository.ListingPricePointRepository;
import de.verdox.hwapi.priceapi.repository.RemoteSoldItemRepository;
import de.verdox.hwapi.productidregistry.ProductRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemPriceService {

    private static final Logger LOGGER = Logger.getLogger(ItemPriceService.class.getName());

    private final ListingPricePointRepository remoteActiveListingPriceRepository; // ✅ NEW
    private final RemoteSoldItemRepository remoteSoldItemRepository;
    private final EbayCompletedListingsService ebayCompletedListingsService;

    private final Map<Long, PriceSeriesResponseDTO> jobs = new ConcurrentHashMap<>();
    private final Map<Integer, PriceSeriesResponseDTO> jobsById = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final ExecutorService backgroundFetcher = Executors.newSingleThreadExecutor();
    private final ExecutorService frontFetcher = Executors.newSingleThreadExecutor();
    private final HardwareSpecService hardwareSpecService;

    private final AmazonPriceService amazonPriceService;

    private final AmazonTrackActiveListingsService amazonTrackActiveListingsService;
    private final EbayFeedPriceService ebayFeedPriceService;

    // ------------------------------------------------------------------------
    // DB-Fetch: Completed (unverändert)
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
                                                                ListingEnums.Country.US,
                                                                "",
                                                                "",
                                                                remoteSoldItem.getMarketPlaceDomain(),
                                                                "",
                                                                remoteSoldItem.getMarketPlaceItemID(),
                                                                remoteSoldItem.getSellDate()
                                                                        .atStartOfDay()
                                                                        .toInstant(ZoneOffset.UTC),
                                                                remoteSoldItem.getSellPrice(),
                                                                remoteSoldItem.getCurrency()
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
    // DB-Fetch: Active (JETZT aus remote_active_listing_price)
    // ------------------------------------------------------------------------
    @Transactional
    public PriceSeriesResponseDTO fetchActiveSeriesDataFromDB(HardwareSpec<?> hardwareSpec,
                                                              Set<ItemCondition> conditions,
                                                              int monthSince) {

        // conditions existieren in active-price nicht mehr (nach deiner Ansage)
        // -> wir ignorieren 'conditions' hier bewusst, damit Signatur kompatibel bleibt.

        List<PriceSeriesDTO> result = new ArrayList<>();

        Set<String> mpns = hardwareSpec.getMPNs() != null ? hardwareSpec.getMPNs() : Set.of("");
        Set<String> eans = hardwareSpec.getEANs() != null ? hardwareSpec.getEANs() : Set.of("");
        String manufacturer = hardwareSpec.getManufacturer();

        Instant since = Instant.now().minus(monthSince * 30L, ChronoUnit.DAYS);

        boolean hasMpns = !mpns.isEmpty();
        boolean hasEans = !eans.isEmpty();

        List<ListingPricePoint> points = remoteActiveListingPriceRepository.findPricePoints("%"+manufacturer.trim().toLowerCase()+"%", mpns, eans, hasMpns, hasEans, since);

        PriceSeriesDTO series = new PriceSeriesDTO(ItemCondition.NEW, false, new LinkedHashMap<>());

        points.stream()
                .filter(p -> p.getCurrency() != null && p.getPrice() != null)
                .collect(Collectors.groupingBy(ListingPricePoint::getCurrency))
                .forEach((currency, byCurrency) -> {
                    series.prices().put(
                            currency,
                            byCurrency.stream()
                                    .map(p -> new PriceSeriesDTO.PricePointDTO(
                                            p.getListing().getPrimaryRegion(),
                                            p.getListing().getMarketPlaceName(),
                                            p.getListing().getItemUrl(),
                                            p.getListing().getMerchantImageUrl(),
                                            p.getListing().getMarketPlaceDomain(),
                                            p.getListing().getMarketPlaceItemID(),
                                            p.getCapturedAt(),
                                            p.getPrice(),
                                            p.getCurrency()
                                    ))
                                    .toList()
                    );
                });

        if(!series.prices().isEmpty()) {
            result.add(series);
        }

        return new PriceSeriesResponseDTO(false, result);
    }

    // ------------------------------------------------------------------------
    // Remote-Fetch (wie bei dir; aktuell auskommentiert)
    // ------------------------------------------------------------------------
    @Transactional
    public PriceSeriesResponseDTO fetchSeriesDataFromRemote(HardwareSpec<?> spec, boolean background) {

        PriceSeriesResponseDTO existing = jobs.get(spec.getId());
        if (existing != null) return existing;

        int ticketId = idCounter.incrementAndGet();
        PriceSeriesResponseDTO dto = new PriceSeriesResponseDTO(true, new CopyOnWriteArrayList<>());

        jobs.put(spec.getId(), dto);
        jobsById.put(ticketId, dto);

        Set<String> eans = spec.getEANs() != null ? Set.copyOf(spec.getEANs()) : Set.of();
        Set<String> mpns = spec.getMPNs() != null ? Set.copyOf(spec.getMPNs()) : Set.of();

        String firstEan = eans.stream().findFirst().orElse(null);
        String firstMpn = mpns.stream().findFirst().orElse(null);
        String title = spec.getManufacturer() + " " + spec.getModel();

        /*
        CompletableFuture.runAsync(() -> {
            try {
                trackEbayCompletedListings(background, mpns, eans);
                trackEbayFeed(eans, mpns, spec.getId());
                trackAmazonPrice(firstEan, firstMpn, title, spec.getId());
            } finally {
                jobs.remove(spec.getId());
            }
        }, background ? backgroundFetcher : frontFetcher);
        */

        return dto;
    }

    private void trackAmazonPrice(String firstEan, String firstMpn, String title, Long specId) {
        if ((firstEan != null && !firstEan.isBlank()) || (firstMpn != null && !firstMpn.isBlank())) {
            try {
/*                amazonTrackActiveListingsService.enrichWithAmazonPrice(
                        AmazonMarketplace.DE,
                        firstEan,
                        firstMpn,
                        title
                );*/
            } catch (Exception ex) {
                LOGGER.warning("Error enriching Amazon price for spec " + specId + ": " + ex.getMessage());
            }
        }
    }

    private void trackEbayCompletedListings(boolean background, Set<String> mpns, Set<String> eans) {
        mpns.forEach(s -> ebayCompletedListingsService.fetchDataFromAllEbayMarketPlaces(s, background));
        eans.forEach(s -> ebayCompletedListingsService.fetchDataFromAllEbayMarketPlaces(s, background));
    }

    private void trackEbayFeed(Set<String> eans, Set<String> mpns, Long specId) {
        try {
            if (!eans.isEmpty() || !mpns.isEmpty()) {
                ebayFeedPriceService.trackActiveListingsByEanAndMpn(eans, mpns);
            }
        } catch (Exception ex) {
            LOGGER.warning("Error fetching eBay active listings from feed for spec " + specId + ": " + ex.getMessage());
        }
    }

    public PriceSeriesResponseDTO getJobByTicketId(int ticketId) {
        return jobsById.get(ticketId);
    }

    private final Set<Long> specIdsToFetch = ConcurrentHashMap.newKeySet();

    @Async
    public void addToBackgroundJob(HardwareSpec<?> hardwareSpec) {
        specIdsToFetch.add(hardwareSpec.getId());
    }

    @Scheduled(fixedDelayString = "${sync.flush-interval-ms:5000}")
    @Transactional
    public void runBackgroundFetcher() {
        if (!jobs.isEmpty()) return;

        Long specId = specIdsToFetch.stream().findFirst().orElse(null);
        if (specId == null) return;

        specIdsToFetch.remove(specId);

        HardwareSpec<?> found = hardwareSpecService.findById(specId);
        if (found == null) return;

        PriceSeriesResponseDTO existing =
                fetchCompletedSeriesDataFromDB(found, EnumSet.allOf(ItemCondition.class), 12);
        if (existing.series() != null && !existing.series().isEmpty()) return;

        fetchSeriesDataFromRemote(found, true);
    }

    @Transactional(readOnly = true)
    public long countAllPricePoints() {
        // sold + active snapshots
        long sold = remoteSoldItemRepository.count();
        long active = remoteActiveListingPriceRepository.count();
        return sold + active;
    }

    @Transactional(readOnly = true)
    public long countTrackedListings() {
        return remoteActiveListingPriceRepository.countDistinctListings();
    }

    @Transactional(readOnly = true)
    public long countDistinctSpecsWithAnyPricePoints() {
        return remoteActiveListingPriceRepository.countDistinctSpecsByEanOrMpn();
    }

    @Transactional
    public void deleteAllPriceData() {
        remoteActiveListingPriceRepository.deleteAll();
        remoteSoldItemRepository.deleteAll();
    }

    @Transactional
    public void enrichWithAmazonPrice(HardwareSpec<?> spec) {
        String ean = spec.getEANs() != null ? spec.getEANs().stream().findFirst().orElse(null) : null;
        String mpn = spec.getMPNs() != null ? spec.getMPNs().stream().findFirst().orElse(null) : null;
        String title = spec.getManufacturer() + " " + spec.getModel();

        if ((ean == null || ean.isBlank()) && (mpn == null || mpn.isBlank())) return;

/*        amazonTrackActiveListingsService.enrichWithAmazonPrice(
                AmazonMarketplace.DE,
                ean,
                mpn,
                title
        );*/
    }
}
