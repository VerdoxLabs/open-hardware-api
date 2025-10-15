package de.verdox.openhardwareapi.component.service;

import de.verdox.openhardwareapi.component.repository.RemoteSoldItemRepository;
import de.verdox.openhardwareapi.io.ebay.EbayMarketplace;
import de.verdox.openhardwareapi.io.ebay.EbayScraper;
import de.verdox.openhardwareapi.model.RemoteSoldItem;
import de.verdox.openhardwareapi.model.values.Currency;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

@Service
@Transactional
public class RemoteSoldItemService {
    private final RemoteSoldItemRepository repo;
    private final EbayScraper ebayBackgroundScraper = new EbayScraper("background_job");
    private final EbayScraper ebayInstant = new EbayScraper("instant_service");

    public RemoteSoldItemService(RemoteSoldItemRepository repo) {
        this.repo = repo;
    }

    /**
     * Durchschnitt der letzten 3 Monate (rollierend bis heute).
     *
     * @return Optional<BigDecimal> mit Scale=2, falls Daten vorhanden.
     */
    // NEU: mit currency-Filter
    public Optional<BigDecimal> getCurrentAveragePriceForEan(String ean, Currency currency, int monthSince) {
        fetchDataFromAllEbayMarketPlaces(ean, false);
        LocalDate from = LocalDate.now().minusMonths(normalizeMonths(monthSince));

        return repo.findAveragePriceSinceByCurrency(ean, from, currency).map(d -> BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Komplette Preisreihe (für Graphen).
     */
    public List<RemoteSoldItemRepository.PricePoint> getAllPricesForEan(String ean) {
        fetchDataFromAllEbayMarketPlaces(ean, false);
        return repo.findPriceSeriesByEan(ean);
    }

    /**
     * Preisreihe (z. B. letzte 3 Monate), praktisch für „aktuelle“ Graphen.
     */
    public List<RemoteSoldItemRepository.PricePoint> getRecentPricesForEan(String ean, int monthSince) {
        fetchDataFromAllEbayMarketPlaces(ean, false);
        LocalDate from = LocalDate.now().minusMonths(normalizeMonths(monthSince));
        return repo.findPriceSeriesByEanSince(ean, from);
    }

    public Set<RemoteSoldItem> fetchDataFromAllEbayMarketPlaces(String EAN, boolean background) {
        ScrapingService.LOGGER.log(Level.INFO, "Fetching price history for " + EAN);
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

    private List<RemoteSoldItem> fetchDataFromEbay(EbayScraper ebayScraper, EbayMarketplace ebayMarketplace, String EAN) {
        try {
            List<RemoteSoldItem> scraped = ebayScraper.fetchByEan(ebayMarketplace, EAN, 1).stream().map(ebaySoldItem -> {
                RemoteSoldItem remoteSoldItem = new RemoteSoldItem();
                remoteSoldItem.setMarketPlaceDomain(ebayMarketplace.getDomain());
                remoteSoldItem.setMarketPlaceItemID(ebaySoldItem.itemId());
                remoteSoldItem.setEAN(EAN);
                remoteSoldItem.setSellPrice(ebaySoldItem.price().value());
                remoteSoldItem.setCurrency(ebaySoldItem.price().currency());
                remoteSoldItem.setSellDate(ebaySoldItem.soldDate());
                return remoteSoldItem;
            }).toList();
            repo.saveAll(scraped);
            return scraped;
        } catch (Throwable e) {
            ScrapingService.LOGGER.log(Level.SEVERE, "Could not scrape price for " + EAN + " on " + ebayMarketplace, e.getMessage());
            return List.of();
        }
    }

    private int normalizeMonths(int monthsSince) {
        // <=0 → Default 3, und z. B. max 24 Monate als Sicherheitskappe
        if (monthsSince <= 0) return 3;
        return Math.min(monthsSince, 24);
    }
}