package de.verdox.hwapi.priceapi.component.service.amazon;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "amazon.paapi")
public class AmazonPaapiProperties {

    /**
     * Konfiguration pro Marketplace (DE, US, ...).
     */
    private Map<AmazonMarketplace, MarketplaceConfig> marketplaces = new EnumMap<>(AmazonMarketplace.class);

    @Getter
    @Setter
    public static class MarketplaceConfig {
        private String accessKey;
        private String secretKey;
        private String partnerTag;
        private boolean enabled = true;
    }
}
