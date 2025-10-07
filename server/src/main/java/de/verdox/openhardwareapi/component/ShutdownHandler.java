package de.verdox.openhardwareapi.component;

import de.verdox.openhardwareapi.io.api.SeleniumBasedWebScraper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class ShutdownHandler {
    @PreDestroy
    public void onShutdown() {
        SeleniumBasedWebScraper.cleanup();
    }
}

