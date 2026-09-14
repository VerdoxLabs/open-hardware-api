package de.verdox.hwapi.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Central switch for every outbound catalog and price scraper.
 * Importing already collected data remains available when this is disabled.
 */
@Component
public class ScrapingEnabled {
    private final boolean enabled;

    public ScrapingEnabled(@Value("${hwapi.scraping.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
