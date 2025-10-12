package de.verdox.openhardwareapi.io.api;

import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.configuration.DataStorage;
import de.verdox.openhardwareapi.io.api.selenium.CookieJar;
import de.verdox.openhardwareapi.io.api.selenium.FScrapingCache;
import de.verdox.openhardwareapi.io.api.selenium.SeleniumBasedWebScraper;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;

import java.net.MalformedURLException;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;

public abstract class MultiPageHardwareScraper<HARDWARE extends HardwareSpec<HARDWARE>> implements ComponentWebScraper<HARDWARE> {
    protected final String id;
    protected final String baseUrl;
    protected final SinglePageHardwareScraper<HARDWARE> singlePageHardwareScraper;
    protected final SeleniumBasedWebScraper seleniumBasedWebScraper;

    public MultiPageHardwareScraper(String id, String baseUrl, SinglePageHardwareScraper<HARDWARE> singlePageHardwareScraper) {
        this.id = id;
        this.seleniumBasedWebScraper = new SeleniumBasedWebScraper(new FScrapingCache(), new CookieJar(DataStorage.resolve("scraping")));
        this.baseUrl = baseUrl;
        this.singlePageHardwareScraper = singlePageHardwareScraper;
    }

    protected abstract void setup(Queue<String> multiPageURLs);

    protected abstract void extractMultiPageURLs(String currentUrl, Document page, Queue<String> multiPageURLs);

    protected abstract void extractSinglePagesURLs(String currentUrl, Document page, Set<String> singlePageURLs) throws MalformedURLException, InterruptedException;

    @Override
    public Set<Document> downloadWebsites() throws Throwable {

        Set<Document> singlePagesScraped = new HashSet<>();
        Set<String> singlePages = new HashSet<>();
        Queue<String> multiPages = new ArrayDeque<>();
        if (!baseUrl.isBlank()) multiPages.add(baseUrl);
        setup(multiPages);

        if (multiPages.isEmpty()) {
            ScrapingService.LOGGER.log(Level.WARNING, "No scraping url defined for " + getClass().getSimpleName());
            return Set.of();
        }

        float p = 0.1f;

        Set<String> alreadyCollected = new HashSet<>();

        ScrapingService.LOGGER.log(Level.INFO, "Extracting catalog pages from: " + multiPages.peek());
        while (!multiPages.isEmpty()) {
            String nextUrl = multiPages.poll();
            if (alreadyCollected.contains(nextUrl)) {
                continue;
            }
            alreadyCollected.add(nextUrl);


            try {
                Document doc = seleniumBasedWebScraper.fetch(ComponentWebScraper.topLevelHost(nextUrl), id, nextUrl, Duration.ofDays(30));
                extractMultiPageURLs(nextUrl, doc, multiPages);
                extractSinglePagesURLs(nextUrl, doc, singlePages);
                p += (0.5f - p) * 0.5f;
            } catch (SeleniumBasedWebScraper.ChallengeFoundException e) {

            }

        }

        ScrapingService.LOGGER.log(Level.INFO, "Scraping " + singlePages.size() + " found products...");

        if (this.singlePageHardwareScraper != null && !singlePages.isEmpty()) {
            int i = 0;
            for (String url : singlePages) {
                if (alreadyCollected.contains(url)) continue;
                singlePageHardwareScraper.setUrl(url);
                try {
                    singlePagesScraped.addAll(singlePageHardwareScraper.downloadWebsites());
                } catch (Throwable ex) {
                    ScrapingService.LOGGER.log(Level.SEVERE, "Could not scrape single page " + url, ex);
                } finally {
                    i++;
                    alreadyCollected.add(url);
                }
            }
        }
        return singlePagesScraped;
    }

    @Override
    public Map<String, List<String>> extract(Document document) {
        return singlePageHardwareScraper.extract(document);
    }

    @Override
    public Optional<HARDWARE> parse(Map<String, List<String>> specs, ScrapeListener<HARDWARE> onScrape) {
        return singlePageHardwareScraper.parse(specs, onScrape);
    }

    @Override
    public int getAmountTasks() {
        return 1;
    }
}
