package de.verdox.hwapi.priceapi.component.service.amazon;

/*import com.amazon.paapi5.v1.*;
import com.amazon.paapi5.v1.api.DefaultApi;*/
import de.verdox.hwapi.model.HardwareSpec;
import de.verdox.hwapi.model.values.Currency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Amazon PA-API Service mit:
 * - Multi-Marketplace Support (DE, US, ...)
 * - Suche per EAN oder MPN (Part Number)
 */
@Service
@Slf4j
public class AmazonPriceService {
/*
    private static class ClientContext {
        private final DefaultApi api;
        private final String partnerTag;
        private final AmazonMarketplace marketplace;

        private ClientContext(DefaultApi api, String partnerTag, AmazonMarketplace marketplace) {
            this.api = api;
            this.partnerTag = partnerTag;
            this.marketplace = marketplace;
        }
    }

    private final Map<AmazonMarketplace, ClientContext> clients = new EnumMap<>(AmazonMarketplace.class);

    public AmazonPriceService(AmazonPaapiProperties props) {

        for (Map.Entry<AmazonMarketplace, AmazonPaapiProperties.MarketplaceConfig> entry
                : props.getMarketplaces().entrySet()) {

            AmazonMarketplace marketplace = entry.getKey();
            AmazonPaapiProperties.MarketplaceConfig cfg = entry.getValue();

            String maskedAccess = mask(cfg.getAccessKey());
            String maskedSecret = mask(cfg.getSecretKey());
            String maskedTag = mask(cfg.getPartnerTag());

            log.info("Loading Amazon PA-API config for {}: enabled={}, accessKey={}, secretKey={}, partnerTag={}",
                    marketplace,
                    cfg.isEnabled(),
                    maskedAccess,
                    maskedSecret,
                    maskedTag
            );

            if (!cfg.isEnabled()) {
                log.info("Amazon PA-API marketplace {} disabled via config", marketplace);
                continue;
            }
            if (cfg.getAccessKey() == null || cfg.getSecretKey() == null || cfg.getPartnerTag() == null) {
                log.warn("Amazon PA-API marketplace {} has incomplete credentials, skipping", marketplace);
                continue;
            }

            ApiClient client = new ApiClient();
            client.setAccessKey(cfg.getAccessKey());
            client.setSecretKey(cfg.getSecretKey());
            client.setHost(marketplace.getHost());
            client.setRegion(marketplace.getRegion());

            DefaultApi api = new DefaultApi(client);
            clients.put(marketplace, new ClientContext(api, cfg.getPartnerTag(), marketplace));

            log.info("Amazon PA-API client initialized for marketplace {}", marketplace);
        }
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        int len = value.length();
        if (len <= 8) {
            return value.charAt(0) + "****" + value.charAt(len - 1);
        }
        return value.substring(0, 4) + "****" + value.substring(len - 4);
    }

    public boolean isMarketplaceConfigured(AmazonMarketplace marketplace) {
        return clients.containsKey(marketplace);
    }

    *//* ===================== Public API ===================== *//*

    *//**
     * Beste Angebotssuche für eine EAN in einem bestimmten Marketplace.
     *//*
    public Optional<AmazonPriceResult> findBestOfferByEan(AmazonMarketplace marketplace, String ean) {
        if (!isMarketplaceConfigured(marketplace) || ean == null || ean.isBlank()) {
            return Optional.empty();
        }

        return searchSingle(
                marketplace,
                ean,
                buildResourcesForEan(),
                item -> matchesEan(item, ean),
                Comparator.comparing(AmazonPriceResult::price)
        );
    }

    *//**
     * Beste Angebotssuche für eine MPN/Part-Number in einem bestimmten Marketplace.
     *//*
    public Optional<AmazonPriceResult> findBestOfferByMpn(AmazonMarketplace marketplace, String mpn) {
        if (!isMarketplaceConfigured(marketplace) || mpn == null || mpn.isBlank()) {
            return Optional.empty();
        }

        return searchSingle(
                marketplace,
                mpn,
                buildResourcesForMpn(),
                item -> matchesMpn(item, mpn),
                Comparator.comparing(AmazonPriceResult::price)
        );
    }

    *//**
     * Kombinierte Suche: erst EAN, wenn nicht vorhanden/kein Treffer → MPN.
     *//*
    public Optional<AmazonPriceResult> findBestOffer(
            AmazonMarketplace marketplace,
            String ean,
            String mpn
    ) {
        if (ean != null && !ean.isBlank()) {
            Optional<AmazonPriceResult> byEan = findBestOfferByEan(marketplace, ean);
            if (byEan.isPresent()) {
                return byEan;
            }
        }
        if (mpn != null && !mpn.isBlank()) {
            return findBestOfferByMpn(marketplace, mpn);
        }
        return Optional.empty();
    }

    *//**
     * Liefert alle passenden Angebote für eine EAN.
     *//*
    public List<AmazonPriceResult> findAllOffersByEan(AmazonMarketplace marketplace, String ean) {
        if (!isMarketplaceConfigured(marketplace) || ean == null || ean.isBlank()) {
            return List.of();
        }

        return searchAll(
                marketplace,
                ean,
                buildResourcesForEan(),
                item -> matchesEan(item, ean)
        );
    }

    *//**
     * Liefert alle passenden Angebote für eine MPN.
     *//*
    public List<AmazonPriceResult> findAllOffersByMpn(AmazonMarketplace marketplace, String mpn) {
        if (!isMarketplaceConfigured(marketplace) || mpn == null || mpn.isBlank()) {
            return List.of();
        }

        return searchAll(
                marketplace,
                mpn,
                buildResourcesForMpn(),
                item -> matchesMpn(item, mpn)
        );
    }

    *//* ===================== Intern: Search Helpers ===================== *//*

    private List<SearchItemsResource> buildResourcesForEan() {
        return Arrays.asList(
                SearchItemsResource.ITEMINFO_TITLE,
                SearchItemsResource.ITEMINFO_EXTERNALIDS,
                SearchItemsResource.OFFERS_LISTINGS_PRICE
        );
    }

    private List<SearchItemsResource> buildResourcesForMpn() {
        return Arrays.asList(
                SearchItemsResource.ITEMINFO_TITLE,
                SearchItemsResource.ITEMINFO_MANUFACTUREINFO,
                SearchItemsResource.ITEMINFO_EXTERNALIDS,
                SearchItemsResource.OFFERS_LISTINGS_PRICE
        );
    }

    private Optional<AmazonPriceResult> searchSingle(
            AmazonMarketplace marketplace,
            String keywords,
            List<SearchItemsResource> resources,
            Predicate<Item> filter,
            Comparator<AmazonPriceResult> resultComparator
    ) {
        List<AmazonPriceResult> all = searchAll(marketplace, keywords, resources, filter);
        return all.stream().min(resultComparator);
    }

    private List<AmazonPriceResult> searchAll(
            AmazonMarketplace marketplace,
            String keywords,
            List<SearchItemsResource> resources,
            Predicate<Item> filter
    ) {
        ClientContext ctx = clients.get(marketplace);
        if (ctx == null) {
            return List.of();
        }

        SearchItemsRequest request = new SearchItemsRequest()
                .partnerTag(ctx.partnerTag)
                .partnerType(PartnerType.ASSOCIATES)
                .searchIndex("All")
                .keywords(keywords)
                .itemCount(10)
                .resources(resources);

        try {
            SearchItemsResponse response = ctx.api.searchItems(request);

            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                response.getErrors().forEach(err ->
                        log.debug("Amazon PA-API error {} ({}): {}", err.getCode(), marketplace, err.getMessage())
                );
            }

            if (response.getSearchResult() == null ||
                    response.getSearchResult().getItems() == null) {
                return List.of();
            }

            return response.getSearchResult()
                    .getItems()
                    .stream()
                    .filter(filter)
                    .map(item -> toResult(item, ctx))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (ApiException e) {
            log.error("Error calling Amazon PA-API for marketplace {}. Status: {}, body: {}",
                    marketplace, e.getCode(), e.getResponseBody(), e);
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error calling Amazon PA-API for marketplace {}", marketplace, e);
            return List.of();
        }
    }

    *//* ===================== Matching ===================== *//*

    private boolean matchesEan(Item item, String ean) {
        if (item == null ||
                item.getItemInfo() == null ||
                item.getItemInfo().getExternalIds() == null ||
                item.getItemInfo().getExternalIds().getEaNs() == null ||
                item.getItemInfo().getExternalIds().getEaNs().getDisplayValues() == null) {
            return false;
        }

        String needle = ean.trim();
        return item.getItemInfo()
                .getExternalIds()
                .getEaNs()
                .getDisplayValues()
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(v -> v.equals(needle));
    }

    private boolean matchesMpn(Item item, String mpn) {
        if (item == null || item.getItemInfo() == null) {
            return false;
        }

        String needleNorm = normalizePartNumber(mpn);

        // 1) ManufactureInfo.ItemPartNumber / Model
        if (item.getItemInfo().getManufactureInfo() != null) {
            ManufactureInfo mi = item.getItemInfo().getManufactureInfo();

            if (mi.getItemPartNumber() != null &&
                    mi.getItemPartNumber().getDisplayValue() != null) {
                String val = normalizePartNumber(mi.getItemPartNumber().getDisplayValue());
                if (!val.isEmpty() && val.equals(needleNorm)) {
                    return true;
                }
            }

            if (mi.getModel() != null &&
                    mi.getModel().getDisplayValue() != null) {
                String val = normalizePartNumber(mi.getModel().getDisplayValue());
                if (!val.isEmpty() && val.equals(needleNorm)) {
                    return true;
                }
            }
        }

        // 2) Fallback: Titel enthält MPN (oft im Titel drin)
        if (item.getItemInfo().getTitle() != null &&
                item.getItemInfo().getTitle().getDisplayValue() != null) {
            String title = item.getItemInfo().getTitle().getDisplayValue().toUpperCase(Locale.ROOT);
            if (title.contains(mpn.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    private String normalizePartNumber(String value) {
        if (value == null) {
            return "";
        }
        // Großschreibung + nur Buchstaben/Ziffern behalten → robust gegen -, /, Leerzeichen
        return value
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
    }

    *//* ===================== Mapping ===================== *//*

    private AmazonPriceResult toResult(Item item, ClientContext ctx) {
        if (item.getOffers() == null ||
                item.getOffers().getListings() == null ||
                item.getOffers().getListings().isEmpty()) {
            return null;
        }

        OfferListing listing = item.getOffers().getListings().get(0);
        if (listing.getPrice() == null || listing.getPrice().getAmount() == null) {
            return null;
        }

        BigDecimal amount = listing.getPrice().getAmount();
        String currencyCode = listing.getPrice().getCurrency();

        Currency currencyEnum = mapCurrency(currencyCode, ctx.marketplace);

        String asin = item.getASIN();
        String detailUrl = item.getDetailPageURL();
        String title = null;

        if (item.getItemInfo() != null &&
                item.getItemInfo().getTitle() != null) {
            title = item.getItemInfo().getTitle().getDisplayValue();
        }

        String affiliateUrl;
        if (asin != null && !asin.isBlank()) {
            affiliateUrl = "https://" + ctx.marketplace.getDomain() + "/dp/" + asin + "/?tag=" + ctx.partnerTag;
        } else {
            affiliateUrl = detailUrl;
        }

        return new AmazonPriceResult(
                asin,
                title,
                amount,
                currencyCode,
                currencyEnum,
                ctx.marketplace.getDomain(),
                detailUrl,
                affiliateUrl,
                Instant.now()
        );
    }

    private Currency mapCurrency(String currencyCode, AmazonMarketplace marketplace) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return marketplace.getDefaultCurrency();
        }
        Currency byCode = Currency.findCurrency(currencyCode);
        return byCode != null ? byCode : marketplace.getDefaultCurrency();
    }

    *//* ===================== DTO ===================== *//*

    public record AmazonPriceResult(
            String asin,
            String title,
            BigDecimal price,
            String currencyCode,
            Currency currency,
            String marketplaceDomain,
            String detailPageUrl,
            String affiliateUrl,
            Instant fetchedAt
    ) {
    }*/
}

