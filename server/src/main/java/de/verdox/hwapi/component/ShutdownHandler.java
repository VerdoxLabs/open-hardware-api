package de.verdox.hwapi.component;

import de.verdox.hwapi.io.api.selenium.SeleniumBasedWebScraper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class ShutdownHandler {
    @PreDestroy
    public void onShutdown() {
        SeleniumBasedWebScraper.cleanup();
    }
}

