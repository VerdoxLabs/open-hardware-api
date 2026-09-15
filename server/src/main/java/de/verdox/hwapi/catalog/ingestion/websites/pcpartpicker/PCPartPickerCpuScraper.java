package de.verdox.hwapi.catalog.ingestion.websites.pcpartpicker;

import de.verdox.hwapi.catalog.application.HardwareSpecService;
import de.verdox.hwapi.catalog.domain.CPU;
import de.verdox.hwapi.catalog.domain.HardwareTypes;
import de.verdox.hwapi.catalog.ingestion.api.ScrapeParser;
import de.verdox.hwapi.catalog.ingestion.api.WebsiteScraper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * PCPartPicker CPU catalog scraper.  It starts at the catalog, follows its
 * hash-based pagination, and subsequently visits the discovered product pages.
 */
public final class PCPartPickerCpuScraper {
    public static final String CATALOG_URL = "https://pcpartpicker.com/products/cpu/";

    private PCPartPickerCpuScraper() {
    }

    public static WebsiteScraper create(HardwareSpecService service) {
        return new WebsiteScraper(service, "pcpartpicker.com")
                .withStrategy(new PCPartPickerStrategy())
                .withMinLiveRequestInterval(Duration.ofSeconds(60))
                .withShouldSavePredicate(PCPartPickerCpuScraper::isUsableCatalogSnapshot)
                .withCPUScrape("PCPartPicker/CPU", cpu -> cpu.addMainScrapeLogic((scraped, target) -> {
                            applySpecs(scraped.specs(), target);
                            PCPartPickerImageStore.storeFirstProductImage(scraped.specs(), target);
                        }, CATALOG_URL));
    }

    static boolean isUsableCatalogSnapshot(String url, org.jsoup.nodes.Document document) {
        return !url.contains("/products/") || !document.select("#category_content tr.tr__product").isEmpty();
    }

    static void applySpecs(Map<String, List<String>> specs, CPU target) {
        new ScrapeParser<CPU>(specs)
                                        .parseString("Manufacturer", CPU::getManufacturer, CPU::setManufacturer)
                                        .parseNumber("Core Count", Integer::parseInt, CPU::getCores, CPU::setCores, 0)
                                        .parseNumber("Thread Count", Integer::parseInt, CPU::getThreads, CPU::setThreads, 0)
                                        .parseNumber("Performance Core Clock", value -> Double.parseDouble(value) * 1000,
                                                CPU::getBaseClockMhz, CPU::setBaseClockMhz, 0d)
                                        .parseNumber("Performance Core Boost Clock", value -> Double.parseDouble(value) * 1000,
                                                CPU::getBoostClockMhz, CPU::setBoostClockMhz, 0d)
                                        .parseNumber("L3 Cache", Integer::parseInt, CPU::getL3CacheMb, CPU::setL3CacheMb, 0)
                                        .parseNumber("TDP", Integer::parseInt, CPU::getTdpWatts, CPU::setTdpWatts, 0)
                                        .parseString("Integrated Graphics", CPU::getIntegratedGraphics, CPU::setIntegratedGraphics)
                                        .parseEnum("Socket", CPU::getSocket, CPU::setSocket,
                                                (value, socket) -> value.equalsIgnoreCase(socket.name())
                                                        || value.equalsIgnoreCase(socket.getName()),
                                                HardwareTypes.CpuSocket.UNKNOWN)
                                        .parse(target);
    }
}
