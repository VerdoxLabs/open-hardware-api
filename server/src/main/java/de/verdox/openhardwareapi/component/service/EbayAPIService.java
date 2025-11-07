package de.verdox.openhardwareapi.component.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.verdox.openhardwareapi.component.repository.HardwareSpecRepository;
import de.verdox.openhardwareapi.component.repository.RemoteSoldItemRepository;
import de.verdox.openhardwareapi.configuration.EbayAPIConfig;
import de.verdox.openhardwareapi.io.ebay.api.EbayBrowseSearchRequest;
import de.verdox.openhardwareapi.io.ebay.api.EbayCategory;
import de.verdox.openhardwareapi.io.ebay.api.EbayDeveloperAPIClient;
import de.verdox.openhardwareapi.io.ebay.api.EbayMarketplace;
import de.verdox.openhardwareapi.model.HardwareSpec;
import de.verdox.openhardwareapi.model.RemoteSoldItem;
import de.verdox.openhardwareapi.model.values.Currency;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@Component
public class EbayAPIService {
    private static final Logger LOGGER = Logger.getLogger(EbayAPIService.class.getSimpleName());

    private final EbayDeveloperAPIClient ebayDeveloperAPIClient;

    // Neu: Repository zum Persistieren von RemoteSoldItem
    private final RemoteSoldItemRepository remoteSoldItemRepository;

    // Persistente Menge aller getrackten EANs (Schnelle Existenzprüfung)
    private final Set<String> eansTracked = ConcurrentHashMap.newKeySet();
    // Zusatz: geordnete Liste um eine Resume-Position zu behalten
    private final CopyOnWriteArrayList<String> eanOrder = new CopyOnWriteArrayList<>();
    // Zeitstempel der letzten erfolgreichen Abfrage pro EAN
    private final ConcurrentHashMap<String, Long> lastChecked = new ConcurrentHashMap<>();
    // Set für EANs, die explizit auf nächsten Run verschoben wurden (optional)
    private final Set<String> scheduledForNextRun = ConcurrentHashMap.newKeySet();

    // Quota state (vereinfacht)
    private final AtomicInteger quotaRemaining = new AtomicInteger(Integer.MAX_VALUE);
    private final HardwareSpecService hardwareSpecService;
    private final EbayAPIConfig ebayAPIConfig;
    private final HardwareSpecRepository hardwareSpecRepository;
    private volatile long quotaResetEpochSec = 0L;

    // Resume-Index: an welcher Position in eanOrder wir weitermachen
    private final AtomicInteger resumeIndex = new AtomicInteger(0);

    // Interval in Sekunden, wie oft eine EAN mindestens erneut abgefragt werden soll (z.B. 1h)
    private final long recheckIntervalSec = Long.parseLong(System.getenv().getOrDefault("EAN_RECHECK_INTERVAL_SEC", "3600"));

    // Ersetze den bisherigen parameterlosen Konstruktor durch einen, der das Repository injiziert
    public EbayAPIService(RemoteSoldItemRepository remoteSoldItemRepository, HardwareSpecService hardwareSpecService, EbayAPIConfig ebayAPIConfig, HardwareSpecRepository hardwareSpecRepository) {
        this.remoteSoldItemRepository = remoteSoldItemRepository;
        this.ebayDeveloperAPIClient = new EbayDeveloperAPIClient(ebayAPIConfig.get().getEbayAPI(), ebayAPIConfig.get().getEbayClientID(), ebayAPIConfig.get().getEbayClientSecret());
        this.hardwareSpecService = hardwareSpecService;
        this.ebayAPIConfig = ebayAPIConfig;
        this.ebayAPIConfig.replace(this.ebayAPIConfig.get());

        int lastIndex = this.ebayAPIConfig.get().getLastIndex();
        if (lastIndex > 0) {
            LOGGER.info("Resuming ebay price fetcher at index: " + lastIndex);
            this.resumeIndex.set(this.ebayAPIConfig.get().getLastIndex());
        }

        this.hardwareSpecRepository = hardwareSpecRepository;
        hardwareSpecRepository.findAll().forEach((hardwareSpec) -> {
            for (String ean : hardwareSpec.getEANs()) {
                trackEAN(ean);
            }
        });
        LOGGER.info("Tracking ebay prices of "+eansTracked.size()+" products.");
    }

    public void trackEAN(String ean) {
        if (ean == null || ean.isBlank()) {
            return;
        }
        boolean added = eansTracked.add(ean);
        if (added) {
            // neue EAN zur Order-Liste ergänzen (nur wenn neu)
            eanOrder.add(ean);
            lastChecked.putIfAbsent(ean, 0L);
        }
    }

    @PreDestroy
    public void saveOldPosition() {
        EbayAPIConfig.ClientConfig clientConfig = this.ebayAPIConfig.get();
        clientConfig.setLastIndex(resumeIndex.get());
        LOGGER.info("Saving index of fetcher: " + resumeIndex.get());
        this.ebayAPIConfig.replace(clientConfig);
    }

    // Neu: Beim Tracken einer Listing-ID als "remote sold item" in Repo speichern
    public void trackListingAsSold(String marketPlaceDomain,
                                   String marketPlaceItemID,
                                   String ean,
                                   BigDecimal sellPrice,
                                   Currency currency,
                                   LocalDate sellDate) {
        try {
            RemoteSoldItem item = new RemoteSoldItem(marketPlaceDomain, marketPlaceItemID, ean, sellPrice, currency, sellDate);
            remoteSoldItemRepository.save(item);
        } catch (Exception ex) {
            // leichtes Logging, aber kein Absturz des Services
            ex.printStackTrace();
        }
        // Sicherstellen, dass die EAN auch im Tracking bleibt / hinzugefügt wird
        if (ean != null && !ean.isBlank()) {
            trackEAN(ean);
        }
    }

    /**
     * Läuft jede Minute und verarbeitet so viele EANs wie das aktuelle Quota erlaubt.
     * Bei Quota-Exhaustion merken wir uns die Resume-Position, damit wir beim nächsten Lauf genau dort weitermachen.
     */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void scheduledFlush() {
        int fetched = 0;
        try {
            long now = Instant.now().getEpochSecond();

            // --- neu: Aktuelle Quota vom Analytics-Endpoint abrufen (resource "buy.browse")
            try {
                EbayDeveloperAPIClient.EbayRateInfo rateInfo = ebayDeveloperAPIClient.getRateLimitForResource("buy.browse").block();
                if (rateInfo != null && rateInfo.remaining >= 0) {
                    quotaRemaining.set(rateInfo.remaining);
                    if (rateInfo.resetEpochSec > 0) quotaResetEpochSec = rateInfo.resetEpochSec;
                    LOGGER.log(Level.INFO, "Updated quota from analytics endpoint: " + rateInfo);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Could not refresh rate limits from analytics endpoint", ex);
            }
            // --- end neu

            // Falls wir einen Reset hatten und scheduled items vorhanden sind, wieder aufnehmen
            if (!scheduledForNextRun.isEmpty() && (quotaResetEpochSec == 0L || now >= quotaResetEpochSec)) {
                // scheduledForNextRun sind EANs, die wir bewusst verschoben haben; da wir EANs persistent halten,
                // reicht es, sie hier nur zu löschen, damit sie wieder regulär durch die Order-Liste kommen.
                scheduledForNextRun.clear();
            }

            if (eansTracked.isEmpty() || eanOrder.isEmpty()) return;

            int available = quotaRemaining.get();
            if (available <= 0 && quotaResetEpochSec != 0L && now < quotaResetEpochSec) {
                return;
            }
            int batchSize = Math.min(100, Math.max(1, available == Integer.MAX_VALUE ? 10 : available));

            // Wähle Kandidaten (aber wir führen jetzt pro-EAN Einzelrequests aus)
            List<String> batch = buildBatch(now, batchSize);

            if (batch.isEmpty()) return;
            LOGGER.log(Level.INFO, "Running ebay price fetching with " + available + " remaining quota. The batch left in this run has " + batch.size() + " elements.");


            // Für jede EAN: individuellen Request erstellen (Kategorie setzen wenn möglich) und einzeln abfragen
            for (int i = 0; i < batch.size(); i++) {
                String currentEan = batch.get(i);
                HardwareSpec<? extends HardwareSpec<?>> hardwareSpec = hardwareSpecService.findByEAN(currentEan);
                if (hardwareSpec == null) {
                    // keine Spezifikation -> skip, advance resume damit wir nicht ewig an dieser Stelle hängen bleiben
                    advanceResumeForProcessed(currentEan);
                    lastChecked.put(currentEan, now);
                    continue;
                }
                Class<? extends HardwareSpec<?>> clazz = (Class<? extends HardwareSpec<?>>) hardwareSpec.getClass();
                EbayCategory ebayCategory = EbayCategory.fromType(clazz);
                if (ebayCategory == null) {
                    continue;
                }

                // Baue den Request individuell für diese EAN (damit Kategorie gesetzt werden kann)
                EbayBrowseSearchRequest.Builder reqBuilder = EbayBrowseSearchRequest
                        .builder(EbayMarketplace.GERMANY)
                        .limit(200)
                        .sellerType(EbayBrowseSearchRequest.SellerType.INDIVIDUAL)
                        .buyingOptions(EbayBrowseSearchRequest.BuyingOption.FIXED_PRICE)
                        .category(ebayCategory)
                        .gtin(currentEan);

                var req = reqBuilder.build();
                var res = ebayDeveloperAPIClient.search(req).block();

                if (res == null) {
                    advanceResumeForProcessed(currentEan);
                    lastChecked.put(currentEan, now);
                    continue;
                }

                // Aktualisiere Quota-Infos falls vorhanden
                if (res.rateLimitRemaining >= 0) {
                    quotaRemaining.set(res.rateLimitRemaining);
                }
                if (res.rateLimitResetEpoch > 0) {
                    quotaResetEpochSec = res.rateLimitResetEpoch;
                }

                // Parse Ergebnisse in price points / RemoteSoldItem
                fetched += parseJsonToPricePoints(res, currentEan);


                // Quota exhausted handling: falls wir über das Limit gestoßen sind -> Resume-Index merken und restliche EANs für nächsten Lauf sichern
                if (res.statusCode == 429 || (res.rateLimitRemaining == 0 && quotaResetEpochSec > 0 && Instant.now().getEpochSecond() < quotaResetEpochSec)) {
                    int startIdx = resumeIndex.get();
                    int newResume = (startIdx + i) % Math.max(1, eanOrder.size());
                    resumeIndex.set(newResume);

                    scheduledForNextRun.add(currentEan);
                    for (int j = i + 1; j < batch.size(); j++) scheduledForNextRun.add(batch.get(j));
                    return;
                } else {
                    lastChecked.put(currentEan, now);
                    advanceResumeForProcessed(currentEan);
                }
            }

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not run ebay api service", ex);
        }
        saveOldPosition();
        LOGGER.log(Level.INFO, "Done fetching ebay prices. In this run " + fetched + " were fetched.");
    }

    private int parseJsonToPricePoints(EbayDeveloperAPIClient.EbaySearchResult res, String currentEan) {
        // Parse JSON und extrahiere einzelne Items
        int count = 0;
        JsonElement jsonElement = JsonParser.parseString(res.body);
        try {
            JsonObject root = jsonElement.getAsJsonObject();
            if (root.has("itemSummaries") && root.get("itemSummaries").isJsonArray()) {
                var arr = root.getAsJsonArray("itemSummaries");
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject it = el.getAsJsonObject();

                    // item id / legacy id
                    String legacyId = null;
                    if (it.has("legacyItemId") && !it.get("legacyItemId").isJsonNull()) {
                        legacyId = it.get("legacyItemId").getAsString();
                    } else if (it.has("itemId") && !it.get("itemId").isJsonNull()) {
                        String raw = it.get("itemId").getAsString();
                        // fallback: v1|277445556612|0 -> take middle part if possible
                        String[] parts = raw.split("\\|");
                        if (parts.length >= 2) legacyId = parts[1];
                        else legacyId = raw;
                    }

                    // marketplace domain (prefer listingMarketplaceId)
                    String marketplace = null;
                    if (it.has("listingMarketplaceId") && !it.get("listingMarketplaceId").isJsonNull()) {
                        marketplace = it.get("listingMarketplaceId").getAsString();
                    } else if (it.has("itemWebUrl") && !it.get("itemWebUrl").isJsonNull()) {
                        try {
                            java.net.URI u = java.net.URI.create(it.get("itemWebUrl").getAsString());
                            marketplace = u.getHost();
                        } catch (Exception ignore) {
                            marketplace = "unknown";
                        }
                    } else {
                        marketplace = "unknown";
                    }

                    // price
                    BigDecimal priceVal = null;
                    String currencyStr = null;
                    if (it.has("price") && it.get("price").isJsonObject()) {
                        JsonObject p = it.getAsJsonObject("price");
                        if (p.has("value") && !p.get("value").isJsonNull()) {
                            try {
                                priceVal = new BigDecimal(p.get("value").getAsString());
                            } catch (Exception ignored) {
                            }
                        }
                        if (p.has("currency") && !p.get("currency").isJsonNull()) {
                            currencyStr = p.get("currency").getAsString();
                        }
                    }

                    // date: prefer itemOriginDate, then itemCreationDate, then itemEndDate
                    LocalDate date = null;
                    String[] dateFields = {"itemOriginDate", "itemCreationDate", "itemEndDate"};
                    for (String df : dateFields) {
                        if (it.has(df) && !it.get(df).isJsonNull()) {
                            String ds = it.get(df).getAsString();
                            try {
                                // ISO timestamp with Z -> parse Instant then to LocalDate UTC
                                Instant inst = Instant.parse(ds);
                                date = inst.atZone(ZoneId.of("UTC")).toLocalDate();
                            } catch (Exception ex) {
                                try {
                                    // fallback: take date prefix yyyy-MM-dd
                                    date = LocalDate.parse(ds.substring(0, 10));
                                } catch (Exception ignored) {
                                }
                            }
                            if (date != null) break;
                        }
                    }
                    if (date == null) date = LocalDate.now();

                    // currency enum
                    Currency currencyEnum = null;
                    if (currencyStr != null) {
                        try {
                            currencyEnum = Currency.findCurrency(currencyStr);
                        } catch (Exception ignored) {
                        }
                    }
                    if (currencyEnum == null) currencyEnum = Currency.EURO;

                    // Wenn keine priceVal vorhanden, skip
                    if (priceVal == null) continue;

                    // Persistiere das Listing als RemoteSoldItem (benutze currentEan als ean)
                    try {
                        trackListingAsSold(marketplace, legacyId, currentEan, priceVal, currencyEnum, date);
                        count++;
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, "Could not parse ebay json answer", ex);
                    }
                }
            }
        } catch (Exception parseEx) {
            // JSON parse error für dieses Ergebnis -> weiterhin resume/lastChecked Verhalten unten
            LOGGER.log(Level.SEVERE, "Could not parse ebay json answer", parseEx);
        }
        return count;
    }

    // Hilfsfunktion: baut Batch beginnend bei resumeIndex, berücksichtigt recheckInterval
    private List<String> buildBatch(long now, int batchSize) {
        int size = eanOrder.size();
        if (size == 0) return List.of();

        int start = resumeIndex.get() % size;
        List<String> batch = new java.util.ArrayList<>(batchSize);

        // Wir durchlaufen eanOrder maximal einmal, bis wir batchSize Kandidaten gesammelt haben
        for (int offset = 0; offset < size && batch.size() < batchSize; offset++) {
            int idx = (start + offset) % size;
            String ean = eanOrder.get(idx);
            long last = lastChecked.getOrDefault(ean, 0L);
            if (now - last >= recheckIntervalSec) {
                batch.add(ean);
            }
        }

        // Falls keine EAN eligibel, kann resumeIndex vorgerückt werden, damit wir beim nächsten Lauf andere EANs prüfen
        if (batch.isEmpty()) {
            resumeIndex.set((start + 1) % size);
        }

        return batch;
    }

    // Hilfsfunktion: advance resumeIndex so that next batch continues after the processed EAN
    private void advanceResumeForProcessed(String processedEan) {
        int size = eanOrder.size();
        if (size == 0) return;
        int pos = eanOrder.indexOf(processedEan);
        if (pos >= 0) {
            int next = (pos + 1) % size;
            resumeIndex.set(next);
        }
    }

}
