package de.verdox.hwapi.pricing.application;

import de.verdox.hwapi.integration.client.PriceSeriesDTO;
import de.verdox.hwapi.integration.client.PriceSeriesResponseDTO;
import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.domain.HardwareSpec;
import de.verdox.hwapi.catalog.domain.values.ItemCondition;
import de.verdox.hwapi.pricing.model.ListingEnums;
import de.verdox.hwapi.pricing.model.ListingPricePoint;
import de.verdox.hwapi.pricing.model.RemoteSoldItem;
import de.verdox.hwapi.pricing.repository.ListingPricePointRepository;
import de.verdox.hwapi.pricing.repository.RemoteSoldItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemPriceService {

    private final ListingPricePointRepository remoteActiveListingPriceRepository; // ✅ NEW
    private final RemoteSoldItemRepository remoteSoldItemRepository;

    private final Map<Long, PriceSeriesResponseDTO> jobs = new ConcurrentHashMap<>();
    private final Map<Integer, PriceSeriesResponseDTO> jobsById = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final HardwareSpecService hardwareSpecService;

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

        return dto;
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

}
