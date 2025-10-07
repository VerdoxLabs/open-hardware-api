package de.verdox.openhardwareapi.io.api;

import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.model.HardwareSpec;
import lombok.Getter;
import lombok.Setter;
import org.jsoup.nodes.Document;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public abstract class SinglePageHardwareScraper<HARDWARE extends HardwareSpec> implements ComponentWebScraper<HARDWARE> {
    @Setter
    protected String url;
    protected final HardwareQuery<HARDWARE> query;
    @Getter
    protected final SeleniumBasedWebScraper seleniumBasedWebScraper = new SeleniumBasedWebScraper(new ScrapingCache(Path.of("./data/scraping")), new CookieJar(Path.of("./data/scraping")));

    public SinglePageHardwareScraper(String url, HardwareQuery<HARDWARE> query) {
        this.url = url;
        this.query = query;
    }

    @Override
    public final Set<HARDWARE> scrape(ScrapeListener<HARDWARE> onScrape) throws Throwable {
        Document doc = seleniumBasedWebScraper.scrapeWithCache(url, Duration.ofDays(90));

        Map<String, List<String>> specs = new HashMap<>();

        String[] numbers = new String[]{"", "", ""};
        parsePageToSpecs(doc, specs);
        extractNumbers(doc, numbers, specs);

        String EAN = numbers[0];
        String UPC = numbers[1];
        String MPN = numbers[2];

        if (EAN.isBlank() && UPC.isBlank() && MPN.isBlank()) {
            ScrapingService.LOGGER.log(Level.WARNING, "No product numbers were found. [" + url + "]");
            return Set.of();
        }

        HARDWARE hardware = query.findHardwareOrCreate(EAN, UPC, MPN);
        hardware.setEAN(EAN);
        hardware.setMPN(MPN);
        hardware.setUPC(UPC);
        translateSpecsToTarget(specs, hardware);

        if(specs.containsKey("model") && !specs.get("model").isEmpty()) {
            hardware.setModel(specs.get("model").getFirst());
        }

        onScrape.onScrape(hardware);
        return Set.of(hardware);
    }

    protected abstract void parsePageToSpecs(Document page, Map<String, List<String>> specs) throws Throwable;

    protected abstract void extractNumbers(Document page, String[] numbers, Map<String, List<String>> specs);

    protected void translateSpecsToTarget(Map<String, List<String>> specs, HARDWARE target) {

    }

    public interface HardwareQuery<HARDWARE extends HardwareSpec> {
        HARDWARE findHardwareOrCreate(String EAN, String UPC, String MPN);
    }

    protected void setEAN(String EAN, String[] numbers) {
        numbers[0] = EAN;
    }

    protected void setUPC(String UPC, String[] numbers) {
        numbers[1] = UPC;
    }

    protected void setMPN(String MPN, String[] numbers) {
        numbers[2] = MPN;
    }

    @Override
    public int getAmountTasks() {
        return 1;
    }
}
