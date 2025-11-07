package de.verdox.openhardwareapi.io.ebay.api;

import de.verdox.openhardwareapi.model.HardwareSpec;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hilfsklasse zum Bauen von eBay-Browse-Search-Requests
 * (API: https://api.ebay.com/buy/browse/v1/item_summary/search)
 */
public final class EbayBrowseSearchRequest {

    /** Typ des Verkäufers: privat oder gewerblich */
    public enum SellerType { BUSINESS, INDIVIDUAL }

    /** Art der Kaufoption laut eBay-Datenmodell */
    public enum BuyingOption { AUCTION, FIXED_PRICE, CLASSIFIED_AD }

    private static final String BASE = "https://api.ebay.com/buy/browse/v1/item_summary/search";

    private final String q;                     // optional (Keyword)
    private final String gtin;                  // optional (EAN/GTIN)
    private final String categoryId;            // optional (aus EbayCategory)
    private final SellerType sellerType;        // optional
    private final Set<BuyingOption> buyingOptions; // optional
    private final Integer limit;                // optional
    private final Integer offset;               // optional
    private final List<String> fieldGroups;     // optional (z.B. EXTENDED)
    private final List<String> extraFilters;    // optional (z.B. price:[..])
    private final EbayMarketplace marketplace;  // Pflicht (für Header)

    private EbayBrowseSearchRequest(Builder b) {
        this.q = b.q;
        this.gtin = b.gtin;
        this.categoryId = b.categoryId;
        this.sellerType = b.sellerType;
        this.limit = b.limit;
        this.offset = b.offset;
        this.marketplace = Objects.requireNonNull(b.marketplace, "marketplace is required");
        this.buyingOptions = Collections.unmodifiableSet(EnumSet.copyOf(b.buyingOptions));
        this.fieldGroups = List.copyOf(b.fieldGroups);
        this.extraFilters = List.copyOf(b.extraFilters);
        validate();
    }

    private void validate() {
        if (q != null && gtin != null)
            throw new IllegalStateException("Use either 'q' or 'gtin', not both.");
        if (q == null && gtin == null && categoryId == null)
            throw new IllegalStateException("Provide at least one of: q, gtin, categoryId.");
    }

    /** Baut die komplette API-URL zusammen */
    public String buildUrl() {
        Map<String, String> params = new LinkedHashMap<>();
        if (q != null && !q.isBlank()) params.put("q", q);
        if (gtin != null && !gtin.isBlank()) params.put("gtin", gtin);
        if (categoryId != null && !categoryId.isBlank()) params.put("category_ids", categoryId);
        if (!fieldGroups.isEmpty()) params.put("fieldgroups", String.join(",", fieldGroups));

        // Filter zusammenstellen
        List<String> filters = new ArrayList<>();
        if (sellerType != null)
            filters.add("sellerAccountTypes:{" + sellerType.name() + "}");
        if (!buyingOptions.isEmpty()) {
            String joined = buyingOptions.stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(","));
            filters.add("buyingOptions:{" + joined + "}");
        }
        if (!extraFilters.isEmpty())
            filters.addAll(extraFilters);
        if (!filters.isEmpty())
            params.put("filter", String.join(",", filters));

        if (limit != null) params.put("limit", String.valueOf(limit));
        if (offset != null) params.put("offset", String.valueOf(offset));

        String query = params.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        return BASE + (query.isEmpty() ? "" : "?" + query);
    }

    /** Header inkl. Marketplace-ID (aus EbayMarketplace) */
    public Map<String, String> buildHeaders(String bearerToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + Objects.requireNonNull(bearerToken, "bearer token required"));
        headers.put("Accept", "application/json");
        headers.put("X-EBAY-C-MARKETPLACE-ID", marketplace.getBrowseMarketplaceId());
        return headers;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /** Erstellt für mehrere GTINs je eine Request-Instanz */
    public static List<EbayBrowseSearchRequest> bulkForGtins(List<String> gtins, Builder template) {
        if (gtins == null || gtins.isEmpty()) return List.of();
        return gtins.stream()
                .map(gtin -> template.copy().gtin(gtin).q(null).build())
                .toList();
    }

    // ---------------- Builder ----------------

    public static Builder builder(EbayMarketplace marketplace) {
        return new Builder(marketplace);
    }

    public static final class Builder {
        private String q;
        private String gtin;
        private String categoryId;
        private SellerType sellerType;
        private final Set<BuyingOption> buyingOptions = EnumSet.noneOf(BuyingOption.class);
        private Integer limit;
        private Integer offset;
        private final EbayMarketplace marketplace;
        private final List<String> fieldGroups = new ArrayList<>();
        private final List<String> extraFilters = new ArrayList<>();

        public Builder(EbayMarketplace marketplace) {
            this.marketplace = marketplace;
        }

        public Builder copy() {
            Builder b = new Builder(this.marketplace);
            b.q = this.q;
            b.gtin = this.gtin;
            b.categoryId = this.categoryId;
            b.sellerType = this.sellerType;
            b.limit = this.limit;
            b.offset = this.offset;
            b.fieldGroups.addAll(this.fieldGroups);
            b.extraFilters.addAll(this.extraFilters);
            b.buyingOptions.addAll(this.buyingOptions);
            return b;
        }

        // Basis-Parameter
        public Builder q(String q) { this.q = q; return this; }
        public Builder gtin(String gtin) { this.gtin = gtin; return this; }

        // Kategorie via EbayCategory
        public Builder category(EbayCategory category) {
            this.categoryId = category != null ? category.getEbayCategoryId() : null;
            return this;
        }

        // Kategorie via HardwareSpec-Typ (automatische Zuordnung)
        public Builder category(Class<? extends HardwareSpec<?>> type) {
            EbayCategory cat = EbayCategory.fromType(type);
            this.categoryId = cat != null ? cat.getEbayCategoryId() : null;
            return this;
        }

        // Verkäufer-Typ
        public Builder sellerType(SellerType sellerType) {
            this.sellerType = sellerType;
            return this;
        }

        // Kaufoption(en)
        public Builder buyingOptions(BuyingOption... options) {
            this.buyingOptions.clear();
            if (options != null && options.length > 0)
                this.buyingOptions.addAll(Arrays.asList(options));
            return this;
        }

        public Builder addBuyingOption(BuyingOption option) {
            if (option != null) this.buyingOptions.add(option);
            return this;
        }

        public Builder clearBuyingOptions() {
            this.buyingOptions.clear();
            return this;
        }

        // Sonstiges
        public Builder limit(Integer limit) { this.limit = limit; return this; }
        public Builder offset(Integer offset) { this.offset = offset; return this; }

        public Builder addFieldGroup(String fieldGroup) {
            this.fieldGroups.add(fieldGroup);
            return this;
        }

        /** Rohfilter, z. B. "price:[10..50]" oder "itemLocationCountry:DE" */
        public Builder addFilter(String rawFilterClause) {
            this.extraFilters.add(rawFilterClause);
            return this;
        }

        public EbayBrowseSearchRequest build() {
            return new EbayBrowseSearchRequest(this);
        }
    }
}
