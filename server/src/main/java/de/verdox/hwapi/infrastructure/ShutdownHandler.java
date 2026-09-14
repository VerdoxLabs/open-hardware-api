package de.verdox.hwapi.infrastructure;

import de.verdox.hwapi.catalog.ingestion.api.selenium.SeleniumBasedWebScraper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class ShutdownHandler {
    @PreDestroy
    public void onShutdown() {
        SeleniumBasedWebScraper.cleanup();
    }
}

