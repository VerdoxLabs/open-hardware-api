package de.verdox.hwapi.priceapi.component.service.ebay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.verdox.hwapi.client.ebay.EbayBrowseSearchRequest;
import de.verdox.hwapi.client.ebay.EbayCategory;
import de.verdox.hwapi.client.ebay.EbayDeveloperAPIClient;
import de.verdox.hwapi.client.ebay.EbayMarketplace;
import de.verdox.hwapi.hardwareapi.component.service.HardwareSpecService;
import de.verdox.hwapi.model.HardwareSpec;
import de.verdox.hwapi.model.values.Currency;
import de.verdox.hwapi.priceapi.component.service.RemoteActiveListingWriterService;
import de.verdox.hwapi.priceapi.configuration.EbayAPIConfig;
import de.verdox.hwapi.priceapi.model.RemoteActiveListing;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@Component
public class EbayAPITrackActiveListingsService {

    private static final Logger LOGGER = Logger.getLogger(EbayAPITrackActiveListingsService.class.getSimpleName());

    private final EbayDeveloperAPIClient ebayDeveloperAPIClient;
    private final HardwareSpecService hardwareSpecService;

    // ✅ zentraler Writer
    private final RemoteActiveListingWriterService listingWriter;

    public EbayAPITrackActiveListingsService(RemoteActiveListingWriterService listingWriter,
                                             HardwareSpecService hardwareSpecService,
                                             EbayAPIConfig ebayAPIConfig) {

        this.listingWriter = listingWriter;
        this.hardwareSpecService = hardwareSpecService;

        this.ebayDeveloperAPIClient = new EbayDeveloperAPIClient(
                ebayAPIConfig.get().getEbayAPI(),
                ebayAPIConfig.get().getEbayClientID(),
                ebayAPIConfig.get().getEbayClientSecret()
        );
    }

    @Transactional
    public Map<String, List<RemoteActiveListing>> fetchActiveListings(
            Set<String> eans,
            Set<String> mpns,
            Set<Currency> currencies,
            EbayMarketplace marketplace,
            Class<? extends HardwareSpec<?>> hardwareType
    ) {
        if (this.ebayDeveloperAPIClient.isSandbox()) return Map.of();

        Map<String, List<RemoteActiveListing>> result = new HashMap<>();
        if (eans == null) eans = Set.of();
        if (mpns == null) mpns = Set.of();
        if (currencies == null || currencies.isEmpty()) currencies = Set.of(Currency.EURO);

        EbayCategory ebayCategory = EbayCategory.fromType(hardwareType);
        if (ebayCategory == null) return result;

        for (Currency currency : currencies) {
            for (String ean : eans) {
                if (ean == null || ean.isBlank()) continue;
                String key = ean.trim();
                List<RemoteActiveListing> listings = fetchActiveBySingleIdentifier(
                        marketplace, ebayCategory, key, null, currency
                );
                if (!listings.isEmpty()) result.computeIfAbsent(key, k -> new ArrayList<>()).addAll(listings);
            }

            for (String mpn : mpns) {
                if (mpn == null || mpn.isBlank()) continue;
                String key = mpn.trim();
                List<RemoteActiveListing> listings = fetchActiveBySingleIdentifier(
                        marketplace, ebayCategory, null, key, currency
                );
                if (!listings.isEmpty()) result.computeIfAbsent(key, k -> new ArrayList<>()).addAll(listings);
            }
        }

        return result;
    }

    @Transactional
    public Map<String, List<RemoteActiveListing>> fetchActiveListingsForSpec(
            HardwareSpec<?> spec,
            Set<Currency> currencies,
            EbayMarketplace marketplace
    ) {
        if (spec == null) return Map.of();
        Set<String> eans = new HashSet<>(spec.getEANs() != null ? spec.getEANs() : List.of());
        Set<String> mpns = new HashSet<>(spec.getMPNs() != null ? spec.getMPNs() : List.of());

        @SuppressWarnings("unchecked")
        Class<? extends HardwareSpec<?>> type = (Class<? extends HardwareSpec<?>>) spec.getClass();

        return fetchActiveListings(eans, mpns, currencies, marketplace, type);
    }

    public List<RemoteActiveListing> fetchActiveBySingleIdentifier(
            EbayMarketplace marketplace,
            EbayCategory ebayCategory,
            @Nullable String ean,
            @Nullable String mpn,
            Currency currency
    ) {
        if (this.ebayDeveloperAPIClient.isSandbox()) return List.of();

        try {
            EbayBrowseSearchRequest.Builder builder = EbayBrowseSearchRequest
                    .builder(marketplace)
                    .limit(200)
                    .sellerType(EbayBrowseSearchRequest.SellerType.INDIVIDUAL)
                    .buyingOptions(EbayBrowseSearchRequest.BuyingOption.FIXED_PRICE)
                    .category(ebayCategory);

            if (ean != null) builder = builder.gtin(ean);
            else if (mpn != null) builder = builder.q(mpn);

            EbayBrowseSearchRequest req = builder.build();
            var res = ebayDeveloperAPIClient.search(req).block();

            if (res == null || res.body == null) return List.of();

            return parseJsonToActiveListings(res, ean, mpn, currency);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error while fetching active listings from ebay", ex);
            return List.of();
        }
    }

    private List<RemoteActiveListing> parseJsonToActiveListings(
            EbayDeveloperAPIClient.EbaySearchResult res,
            @Nullable String requestedEan,
            @Nullable String requestedMpn,
            Currency defaultCurrency
    ) {
        List<RemoteActiveListing> result = new ArrayList<>();

        JsonElement jsonElement = JsonParser.parseString(res.body);
        try {
            JsonObject root = jsonElement.getAsJsonObject();
            if (!root.has("itemSummaries") || !root.get("itemSummaries").isJsonArray()) {
                return result;
            }

            var arr = root.getAsJsonArray("itemSummaries");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject it = el.getAsJsonObject();

                String legacyId = null;
                if (it.has("legacyItemId") && !it.get("legacyItemId").isJsonNull()) {
                    legacyId = it.get("legacyItemId").getAsString();
                } else if (it.has("itemId") && !it.get("itemId").isJsonNull()) {
                    String raw = it.get("itemId").getAsString();
                    String[] parts = raw.split("\\|");
                    legacyId = (parts.length >= 2) ? parts[1] : raw;
                }
                if (legacyId == null) continue;

                String marketPlaceDomain;
                if (it.has("listingMarketplaceId") && !it.get("listingMarketplaceId").isJsonNull()) {
                    marketPlaceDomain = it.get("listingMarketplaceId").getAsString();
                } else if (it.has("itemWebUrl") && !it.get("itemWebUrl").isJsonNull()) {
                    try {
                        java.net.URI u = java.net.URI.create(it.get("itemWebUrl").getAsString());
                        marketPlaceDomain = u.getHost();
                    } catch (Exception ignore) {
                        marketPlaceDomain = "unknown";
                    }
                } else {
                    marketPlaceDomain = "unknown";
                }

                String title = it.has("title") && !it.get("title").isJsonNull()
                        ? it.get("title").getAsString()
                        : null;

                String itemUrl = it.has("itemWebUrl") && !it.get("itemWebUrl").isJsonNull()
                        ? it.get("itemWebUrl").getAsString()
                        : null;

                BigDecimal priceVal = null;
                String currencyStr = null;
                if (it.has("price") && it.get("price").isJsonObject()) {
                    JsonObject p = it.getAsJsonObject("price");
                    if (p.has("value") && !p.get("value").isJsonNull()) {
                        try {
                            priceVal = new BigDecimal(p.get("value").getAsString());
                        } catch (Exception ignored) { }
                    }
                    if (p.has("currency") && !p.get("currency").isJsonNull()) {
                        currencyStr = p.get("currency").getAsString();
                    }
                }
                if (priceVal == null) continue;

                Currency listingCurrency = defaultCurrency;
                if (currencyStr != null) {
                    try {
                        listingCurrency = Currency.findCurrency(currencyStr);
                    } catch (Exception ignored) { }
                }

                RemoteActiveListing listing = listingWriter.upsertIdentityAndDailyPrice(
                        res.ebayMarketplace.getCountry(),
                        "ebay",
                        marketPlaceDomain,
                        legacyId,
                        requestedEan,
                        requestedMpn,
                        title,
                        "??",
                        itemUrl,
                        null,
                        priceVal,
                        listingCurrency,
                        null,
                        null
                );

                result.add(listing);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not parse ebay json answer", ex);
        }

        return result;
    }
}
