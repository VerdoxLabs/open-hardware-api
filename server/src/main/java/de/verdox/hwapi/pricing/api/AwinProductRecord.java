package de.verdox.hwapi.pricing.api;

import de.verdox.hwapi.catalog.domain.values.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwinProductRecord {
    private String awDeepLink;
    private String productName;
    private String awProductId;
    private String merchantProductId;
    private String merchantImageUrl;
    private String description;
    private String manufacturerName;
    private String merchantCategory;
    private BigDecimal searchPrice;
    private String merchantName;
    private long merchantId;
    private String categoryName;
    private long categoryId;
    private String awImageUrl;
    private Currency currency;
    private BigDecimal storePrice;
    private BigDecimal deliveryCost;
    private String merchantDeepLink;
    private String language;
    private String lastUpdated;
    private String ean;
    private String isbn;
    private String upc;
    private String mpn;
    private String parentProductId;
    private DisplayPrice displayPrice;
    private long dataFeedId;
    private String productGtin;
    private BigDecimal averageRating;
    public record DisplayPrice(BigDecimal price, Currency currency) {}
}
