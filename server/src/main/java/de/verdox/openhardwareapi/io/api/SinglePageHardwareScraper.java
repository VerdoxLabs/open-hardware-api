package de.verdox.openhardwareapi.io.api;

import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.configuration.DataStorage;
import de.verdox.openhardwareapi.io.api.selenium.CookieJar;
import de.verdox.openhardwareapi.io.api.selenium.FScrapingCache;
import de.verdox.openhardwareapi.io.api.selenium.SeleniumBasedWebScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import lombok.Getter;
import lombok.Setter;
import org.jsoup.nodes.Document;

import java.time.Duration;
import java.util.*;
import java.util.logging.Level;

public abstract class SinglePageHardwareScraper<HARDWARE extends HardwareSpec<HARDWARE>> implements ComponentWebScraper<HARDWARE> {
    private final String id;
    @Setter
    protected String url;
    protected final HardwareQuery<HARDWARE> query;
    @Getter
    protected final SeleniumBasedWebScraper seleniumBasedWebScraper;

    public SinglePageHardwareScraper(String id, String url, HardwareQuery<HARDWARE> query) {
        this.id = id;
        seleniumBasedWebScraper = new SeleniumBasedWebScraper(new FScrapingCache(), new CookieJar(DataStorage.resolve("scraping")));
        this.url = url;
        this.query = query;
    }

    @Override
    public Set<Document> downloadWebsites() throws Throwable {
        return Set.of(seleniumBasedWebScraper.fetch(ComponentWebScraper.topLevelHost(url), id, url, Duration.ofDays(90)));
    }

    @Override
    public final Set<HARDWARE> scrape(Set<Document> doc, ScrapeListener<HARDWARE> onScrape) throws Throwable {
        Set<HARDWARE> scraped = new HashSet<>();
        for (Document document : doc) {
            Map<String, List<String>> specs = new HashMap<>();
            parsePageToSpecs(document, specs);

            if (!specs.containsKey("EAN") && !specs.containsKey("UPC") && !specs.containsKey("MPN")) {
                ScrapingService.LOGGER.log(Level.WARNING, "No product numbers were found. [" + url + "]");
                continue;
            }
            String EAN = specs.getOrDefault("EAN", List.of("---")).getFirst();
            String UPC = specs.getOrDefault("UPC", List.of("---")).getFirst();
            String MPN = specs.getOrDefault("MPN", List.of("---")).getFirst();

            if (EAN.isBlank() && UPC.isBlank() && MPN.isBlank()) {
                ScrapingService.LOGGER.log(Level.WARNING, "No product numbers were found. [" + url + "]");
                continue;
            }

            HARDWARE hardware = query.findHardwareOrCreate(EAN, UPC, MPN);
            hardware.setEAN(EAN);
            hardware.setMPN(MPN);
            hardware.setUPC(UPC);
            translateSpecsToTarget(specs, hardware);

            if (specs.containsKey("model") && !specs.get("model").isEmpty()) {
                hardware.setModel(specs.get("model").getFirst());
            }

            scraped.add(hardware);
            onScrape.onScrape(hardware);
        }

        return scraped;
    }

    protected abstract void parsePageToSpecs(Document page, Map<String, List<String>> specs) throws Throwable;

    protected void translateSpecsToTarget(Map<String, List<String>> specs, HARDWARE target) {

    }

    public interface HardwareQuery<HARDWARE extends HardwareSpec<HARDWARE>> {
        HARDWARE findHardwareOrCreate(String EAN, String UPC, String MPN);
    }

    @Override
    public int getAmountTasks() {
        return 1;
    }
}
