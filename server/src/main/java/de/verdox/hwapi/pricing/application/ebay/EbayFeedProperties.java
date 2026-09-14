package de.verdox.hwapi.pricing.application.ebay;

import de.verdox.hwapi.integration.client.ebay.EbayMarketplace;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ebay.feed")
public class EbayFeedProperties {

    private String clientId;
    private String clientSecret;

    /**
     * Kategorie-ID für den ITEM-Feed (z. B. 58058 = PC-Komponenten).
     * Falls du später pro Marketplace unterschiedliche Kategorien willst,
     * können wir das hier optional pro Marketplace ergänzen.
     */
    private String categoryId;

    private String downloadDirectory = "/tmp/ebay-feed";

    /**
     * Welche Marketplaces sollen aktiv verwendet werden?
     */
    private Set<EbayMarketplace> enabledMarketplaces = Set.of(EbayMarketplace.GERMANY);
}