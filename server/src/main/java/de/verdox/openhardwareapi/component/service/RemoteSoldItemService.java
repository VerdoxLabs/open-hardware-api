package de.verdox.openhardwareapi.component.service;

import de.verdox.openhardwareapi.component.repository.RemoteSoldItemRepository;
import de.verdox.openhardwareapi.io.ebay.EbayScraper;
import de.verdox.openhardwareapi.io.ebay.EbaySoldItem;
import de.verdox.openhardwareapi.io.ebay.api.EbayCategory;
import de.verdox.openhardwareapi.io.ebay.api.EbayMarketplace;
import de.verdox.openhardwareapi.model.HardwareSpec;
import de.verdox.openhardwareapi.model.RemoteSoldItem;
import de.verdox.openhardwareapi.model.dto.PricePointUploadDto;
import de.verdox.openhardwareapi.model.values.Currency;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

@Service
@Transactional
public class RemoteSoldItemService {

    private final RemoteSoldItemRepository repo;
    private final PricePointSyncService pricePointSyncService;
    private final EbayScraper ebayBackgroundScraper = new EbayScraper("background_job");
    private final EbayScraper ebayInstant = new EbayScraper("instant_service");
    private final HardwareSpecService hardwareSpecService;
    private final Map<String, CompletableFuture<Void>> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public RemoteSoldItemService(RemoteSoldItemRepository repo, PricePointSyncService pricePointSyncService, HardwareSpecService hardwareSpecService) {
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
        ScrapingService.LOGGER.log(Level.INFO, "Collecting prices from ebay marketplaces for " + count + " products");
        int elementsWithPrices = 0;
        for (String EAN : setToTravel) {
            var result = fetchDataFromAllEbayMarketPlaces(EAN, true);
            if (!result.isEmpty()) {
                elementsWithPrices++;
            }
        }
        ScrapingService.LOGGER.log(Level.INFO, "Collected prices for " + elementsWithPrices + " / " + count + " products.");
    }

    private Set<RemoteSoldItem> fetchDataFromEbay(EbayScraper ebayScraper, EbayMarketplace ebayMarketplace, String EAN) {
        try {
            Set<RemoteSoldItem> remoteItems = new HashSet<>();
            final String ean = normalize(EAN);

            HardwareSpec<?> hardwareSpec = hardwareSpecService.findByEAN(EAN);
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
