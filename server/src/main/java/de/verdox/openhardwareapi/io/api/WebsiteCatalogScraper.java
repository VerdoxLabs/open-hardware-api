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
import java.util.concurrent.atomic.AtomicBoolean;
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
        Set<WebsiteScrapingStrategy.SinglePageCandidate> singlePages = new HashSet<>();
        Set<String> alreadyCollected = new HashSet<>();
        Queue<WebsiteScrapingStrategy.MultiPageCandidate> multiPages = new ArrayDeque<>(urlsToScrape.stream().map(WebsiteScrapingStrategy.MultiPageCandidate::new).toList());

        if (multiPages.isEmpty()) {
            ScrapingService.LOGGER.log(Level.SEVERE, "No scraping url defined for " + domain + "[" + id + "]");
            return Stream.of();
        }

        AtomicBoolean challengeFound = new AtomicBoolean(false);

        while (!multiPages.isEmpty()) {
            WebsiteScrapingStrategy.MultiPageCandidate nextCandidate = multiPages.poll();
            if (alreadyCollected.contains(nextCandidate.url())) {
                continue;
            }
            alreadyCollected.add(nextCandidate.url());

            try {
                Document doc = seleniumBasedWebScraper.fetch(domain, id, nextCandidate.url(), Duration.ofDays(30), challengeFound.get());
                websiteScrapingStrategy.extractMultiPageURLs(nextCandidate.url(), doc, multiPages);
                websiteScrapingStrategy.extractSinglePagesURLs(nextCandidate.url(), doc, singlePages);

            } catch (SeleniumBasedWebScraper.ChallengeFoundException e) {
                ScrapingService.LOGGER.log(Level.SEVERE, "Challenge found on domain " + domain);
                challengeFound.set(true);
            }
        }

        String topLevelHost = ComponentWebScraper.topLevelHost(urlsToScrape.getFirst());

        ScrapingService.LOGGER.log(Level.INFO, "Found " + singlePages.size() + " scraping pages for " + topLevelHost + " [" + id + "]");

        return singlePages.stream().filter(singlePageCandidate -> !alreadyCollected.contains(singlePageCandidate.url())).map(singlePageCandidate -> {
            try {
                return new ScrapedSpecPage(singlePageCandidate, seleniumBasedWebScraper.fetch(domain, id, singlePageCandidate.url(), Duration.ofDays(90), challengeFound.get()));
            } catch (SeleniumBasedWebScraper.ChallengeFoundException e) {
                challengeFound.set(true);
                ScrapingService.LOGGER.log(Level.SEVERE, "Challenge found on domain " + domain);
                return null;
            } catch (Throwable ex) {
                ScrapingService.LOGGER.log(Level.SEVERE, "Could not scrape single page " + singlePageCandidate.url(), ex);
                return null;
            } finally {
                alreadyCollected.add(singlePageCandidate.url());
            }
        }).filter(Objects::nonNull);
    }

    @Override
    public ScrapedSpecs extract(ScrapedSpecPage scrapedPage) throws Throwable {
        Map<String, List<String>> specs = websiteScrapingStrategy.extractSpecMap(scrapedPage.page());
        specs.putAll(scrapedPage.singlePageCandidate().specMap());
        return new ScrapedSpecs(scrapedPage.singlePageCandidate().url(), specs);
    }
}
