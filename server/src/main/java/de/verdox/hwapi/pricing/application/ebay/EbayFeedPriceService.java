package de.verdox.hwapi.pricing.application.ebay;

import com.ebay.feed.api.Feed;
import com.ebay.feed.api.FeedImpl;
import com.ebay.feed.enums.FeedTypeEnum;
import com.ebay.feed.model.feed.download.GetFeedResponse;
import com.ebay.feed.model.feed.operation.feed.FeedRequest;
import com.ebay.feed.model.feed.operation.filter.FeedFilterRequest;
import com.ebay.feed.model.feed.operation.filter.Response;
import de.verdox.hwapi.integration.client.ebay.EbayMarketplace;
import de.verdox.hwapi.catalog.domain.values.Currency;
import de.verdox.hwapi.pricing.application.RemoteActiveListingWriterService;
import de.verdox.hwapi.pricing.model.RemoteActiveListing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class EbayFeedPriceService {

    private final EbayFeedProperties props;
    private final EbayOAuthService oauthService;

    // ✅ zentraler Writer
    private final RemoteActiveListingWriterService listingWriter;

    private final Feed feed = new FeedImpl();

    public void trackActiveListingsByEanAndMpn(Set<String> eans, Set<String> mpns) {
        Map<EbayMarketplace, List<RemoteActiveListing>> results = fetchPricesByEanAndMpn(eans, mpns);

        int stored = results.values().stream().mapToInt(List::size).sum();
        if (stored > 0) log.info("Stored {} active listings from eBay feed (identity+daily price)", stored);
    }

    public Map<EbayMarketplace, List<RemoteActiveListing>> fetchPricesByEanAndMpn(Set<String> eans, Set<String> mpns) {
        Map<EbayMarketplace, List<RemoteActiveListing>> result = new EnumMap<>(EbayMarketplace.class);

        for (EbayMarketplace marketplace : props.getEnabledMarketplaces()) {
            try {
                List<RemoteActiveListing> listings = fetchForSingleMarketplace(marketplace, eans, mpns);
                result.put(marketplace, listings);
            } catch (Exception e) {
                log.error("Failed to process feed for marketplace {}", marketplace, e);
            }
        }

        return result;
    }

    private List<RemoteActiveListing> fetchForSingleMarketplace(EbayMarketplace marketplace, Set<String> eans, Set<String> mpns) {
        String downloadDir = props.getDownloadDirectory() + "/" + marketplace.name().toLowerCase(Locale.ROOT);
        ensureDirectory(downloadDir);

        String downloaded = downloadFeedFile(marketplace, downloadDir);
        String unzipped = unzipFeedFile(downloaded);

        String filtered = unzipped;
        if (eans != null && !eans.isEmpty()) {
            filtered = filterByGtins(unzipped, eans);
        }

        return parseListings(filtered, marketplace, eans, mpns);
    }

    private String downloadFeedFile(EbayMarketplace marketplace, String downloadDir) {
        String bearerToken = "Bearer " + oauthService.getAccessToken();

        FeedRequest.FeedRequestBuilder builder = new FeedRequest.FeedRequestBuilder();
        builder.feedScope("ALL_ACTIVE")
                .categoryId(props.getCategoryId())
                .siteId(marketplace.getBrowseMarketplaceId())
                .token(bearerToken)
                .type(FeedTypeEnum.ITEM);

        FeedRequest request = builder.build();

        log.info("Downloading eBay feed for {} (siteId={})", marketplace, marketplace.getBrowseMarketplaceId());

        GetFeedResponse response = feed.get(request, downloadDir);

        if (response.getStatusCode() != 0) {
            throw new IllegalStateException("Feed download failed for " + marketplace + ": "
                    + response.getStatusCode() + " - " + response.getMessage());
        }

        return response.getFilePath();
    }

    private String unzipFeedFile(String archivePath) {
        Response unzip = feed.unzip(archivePath);
        if (unzip.getStatusCode() != 0) {
            throw new IllegalStateException("Feed unzip failed: " + unzip.getMessage());
        }
        return unzip.getFilePath();
    }

    private String filterByGtins(String unzippedPath, Set<String> gtins) {
        FeedFilterRequest filter = new FeedFilterRequest();
        filter.setGtins(new HashSet<>(gtins));

        Response resp = feed.filter(filter);
        if (resp.getStatusCode() != 0) {
            throw new IllegalStateException("GTIN filter failed: " + resp.getMessage());
        }

        return resp.getFilePath();
    }

    private List<RemoteActiveListing> parseListings(String tsvFile,
                                                    EbayMarketplace marketplace,
                                                    Set<String> eans,
                                                    Set<String> mpns) {
        Path path = Paths.get(tsvFile);
        if (!Files.exists(path)) {
            log.warn("TSV file missing: {}", tsvFile);
            return List.of();
        }

        List<RemoteActiveListing> listings = new ArrayList<>();

        try (Stream<String> stream = Files.lines(path)) {

            Iterator<String> it = stream.iterator();
            if (!it.hasNext()) return List.of();

            String header = it.next();
            Map<String, Integer> idx = indexHeader(header);

            while (it.hasNext()) {
                String line = it.next();
                if (line.isBlank()) continue;

                String[] cols = line.split("\t", -1);

                String gtin = val(cols, idx, "gtin");
                String mpnVal = val(cols, idx, "mpn");
                String inferred = val(cols, idx, "inferredMpn");

                boolean matchEan = eans != null && gtin != null && eans.contains(gtin);
                boolean matchMpn = mpns != null && (
                        (mpnVal != null && mpns.contains(mpnVal)) ||
                                (inferred != null && mpns.contains(inferred))
                );

                if (!matchEan && !matchMpn) continue;

                String itemId = val(cols, idx, "itemId");
                if (itemId == null || itemId.isBlank()) continue;

                String afUrl = val(cols, idx, "itemAffiliateWebUrl");
                String webUrl = val(cols, idx, "itemWebUrl");
                String effectiveUrl = (afUrl != null && !afUrl.isBlank()) ? afUrl : webUrl;
                if (effectiveUrl == null || effectiveUrl.isBlank()) continue;

                String priceVal = val(cols, idx, "priceValue");
                String currencyVal = val(cols, idx, "priceCurrency");
                if (priceVal == null || currencyVal == null) continue;

                BigDecimal price = new BigDecimal(priceVal);

                Currency currency;
                try {
                    currency = Currency.findCurrency(currencyVal);
                } catch (Exception ex) {
                    continue;
                }

                String effectiveMpn = firstNonBlank(mpnVal, inferred);

                // ✅ Identität + Daily-Price
                RemoteActiveListing listing = listingWriter.upsertIdentityAndDailyPrice(
                        marketplace.getCountry(),
                        "ebay",
                        marketplace.getDomain(),
                        itemId,
                        gtin,
                        effectiveMpn,
                        null,          // title (falls im TSV vorhanden: setzen)
                        "??",
                        effectiveUrl,
                        null,
                        price,
                        currency,
                        null,
                        null
                );

                listings.add(listing);
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        log.info("Marketplace {} → {} active matches", marketplace, listings.size());
        return listings;
    }

    private void ensureDirectory(String dir) {
        try { Files.createDirectories(Paths.get(dir)); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private Map<String, Integer> indexHeader(String headerLine) {
        String[] cols = headerLine.split("\t", -1);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < cols.length; i++) idx.put(cols[i], i);
        return idx;
    }

    private String val(String[] cols, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i < 0 || i >= cols.length) return null;
        String v = cols[i];
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }
}
