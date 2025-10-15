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
import java.util.stream.Stream;

public abstract class WebsiteCatalogScraper<HARDWARE extends HardwareSpec<HARDWARE>> implements ComponentWebScraper<HARDWARE> {
    private final String domain;
    protected final String id;
    private final List<String> urlsToScrape;
    @Getter
    protected final SeleniumBasedWebScraper seleniumBasedWebScraper;
    @Getter
    @Setter
    private WebsiteScrapingStrategy websiteScrapingStrategy;

    public WebsiteCatalogScraper(String domain, String id, String... urlsToScrape) {
        this.domain = domain;
        this.id = id;
        this.urlsToScrape = Arrays.stream(urlsToScrape).toList();
        this.seleniumBasedWebScraper = new SeleniumBasedWebScraper("shop-scraper", new FScrapingCache(), new CookieJar(DataStorage.resolve("scraping")));
    }

    @Override
    public Stream<ScrapedSpecPage> downloadWebsites() throws Throwable {
        Set<String> singlePages = new HashSet<>();
        Set<String> alreadyCollected = new HashSet<>();
        Queue<String> multiPages = new ArrayDeque<>(urlsToScrape);

        if (multiPages.isEmpty()) {
            ScrapingService.LOGGER.log(Level.SEVERE, "No scraping url defined for " + domain + "[" + id + "]");
            return Stream.of();
        }

        while (!multiPages.isEmpty()) {
            String nextUrl = multiPages.poll();
            if (alreadyCollected.contains(nextUrl)) {
                continue;
            }
            alreadyCollected.add(nextUrl);

            try {
                Document doc = seleniumBasedWebScraper.fetch(domain, id, nextUrl, Duration.ofDays(30));
                websiteScrapingStrategy.extractMultiPageURLs(nextUrl, doc, multiPages);
                websiteScrapingStrategy.extractSinglePagesURLs(nextUrl, doc, singlePages);

            } catch (SeleniumBasedWebScraper.ChallengeFoundException e) {

            }
        }

        String topLevelHost = ComponentWebScraper.topLevelHost(urlsToScrape.getFirst());

        ScrapingService.LOGGER.log(Level.INFO, "Found " + singlePages.size() + " scraping pages for " + topLevelHost + " [" + id + "]");

        return singlePages
                .stream()
                .filter(url -> !alreadyCollected.contains(url))
                .map(url -> {
                    try {
                        return new ScrapedSpecPage(url, seleniumBasedWebScraper.fetch(domain, id, url, Duration.ofDays(90)));
                    } catch (Throwable ex) {
                        ScrapingService.LOGGER.log(Level.SEVERE, "Could not scrape single page " + url, ex);
                        return null;
                    } finally {
                        alreadyCollected.add(url);
                    }
                }).filter(Objects::nonNull);
    }

    @Override
    public ScrapedSpecs extract(ScrapedSpecPage scrapedPage) throws Throwable {
        return new ScrapedSpecs(scrapedPage.url(), websiteScrapingStrategy.extractSpecMap(scrapedPage.page()));
    }
}
