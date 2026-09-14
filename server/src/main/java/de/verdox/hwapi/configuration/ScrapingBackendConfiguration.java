package de.verdox.hwapi.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.hwapi.catalog.ingestion.api.selenium.SeleniumBasedWebScraper;
import de.verdox.hwapi.catalog.ingestion.api.webscraper.WebScraperApiClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Locale;

/** Configures the process-wide scraper backend used by the existing scraper instances. */
@Slf4j
@Configuration
public class ScrapingBackendConfiguration {

    private final String backend;
    private final String baseUrl;
    private final String apiKey;
    private final String engine;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    public ScrapingBackendConfiguration(
            @Value("${hwapi.scraping.backend:selenium}") String backend,
            @Value("${hwapi.scraping.webscraper.base-url:http://localhost:8090}") String baseUrl,
            @Value("${hwapi.scraping.webscraper.api-key:dev-key-change-me}") String apiKey,
            @Value("${hwapi.scraping.webscraper.engine:auto}") String engine,
            @Value("${hwapi.scraping.webscraper.timeout:120s}") Duration timeout,
            ObjectMapper objectMapper) {
        this.backend = backend;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.engine = engine;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void configureBackend() {
        switch (backend.trim().toLowerCase(Locale.ROOT)) {
            case "selenium", "legacy" -> {
                SeleniumBasedWebScraper.useLegacySelenium();
                log.info("Hardware scraping backend: legacy Selenium");
            }
            case "webscraper", "api" -> {
                SeleniumBasedWebScraper.useWebScraperApi(
                        new WebScraperApiClient(baseUrl, apiKey, engine, timeout, objectMapper));
                log.info("Hardware scraping backend: Webscraper API at {} (engine={})", baseUrl, engine);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown hwapi.scraping.backend '" + backend + "' (expected selenium or webscraper)");
        }
    }
}
