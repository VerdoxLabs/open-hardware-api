package de.verdox.hwapi.io.api;

import de.verdox.hwapi.configuration.DataStorage;
import de.verdox.hwapi.hardwareapi.component.service.ScrapingService;
import de.verdox.hwapi.io.api.selenium.CookieJar;
import de.verdox.hwapi.io.api.selenium.FScrapingCache;
import de.verdox.hwapi.io.api.selenium.FetchOptions;
import de.verdox.hwapi.io.api.selenium.SeleniumBasedWebScraper;
import de.verdox.hwapi.model.HardwareSpec;
import lombok.Getter;
import lombok.Setter;
import org.jsoup.nodes.Document;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.MalformedURLException;
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
            ScrapingService.LOGGER.log(Level.SEVERE, "\tNo scraping url defined for " + domain + "[" + id + "]");
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


                Document doc = seleniumBasedWebScraper.fetch(domain, id, nextCandidate.url(),
                        new FetchOptions()
                                .setTryHeadlessFirst(websiteScrapingStrategy.supportsHeadlessScraping())
                                .setSkipIfNotCache(challengeFound.get())
                                .setTtl(Duration.ofDays(5))
                );
                websiteScrapingStrategy.extractMultiPageURLs(nextCandidate.url(), doc, multiPages);
                websiteScrapingStrategy.extractSinglePagesURLs(nextCandidate.url(), doc, singlePages);

            } catch (SeleniumBasedWebScraper.ChallengeFoundException e) {
                ScrapingService.LOGGER.log(Level.SEVERE, "\tChallenge found on domain " + domain);
                challengeFound.set(true);
            }
        }

        String topLevelHost = ComponentWebScraper.topLevelHost(urlsToScrape.getFirst());

        ScrapingService.LOGGER.log(Level.INFO, "\tFound " + singlePages.size() + " scraping pages for " + topLevelHost + " [" + id + "]");

        return singlePages.stream().filter(singlePageCandidate -> !alreadyCollected.contains(singlePageCandidate.url())).map(singlePageCandidate -> {
            try {
                return new ScrapedSpecPage(singlePageCandidate, seleniumBasedWebScraper.fetch(domain, id, singlePageCandidate.url(),
                        new FetchOptions()
                                .setTryHeadlessFirst(websiteScrapingStrategy.supportsHeadlessScraping())
                                .setSkipIfNotCache(challengeFound.get())
                                .setTtl(Duration.ofDays(1000000L))
                                .setBeforeSaveOperation(driver -> {
                                    new WebDriverWait(driver, Duration.ofSeconds(10)).until(webDriver -> {
                                        try {
                                            return ((JavascriptExecutor) webDriver).executeScript("return jQuery.active === 0;");
                                        }
                                        catch (Exception e) {
                                            return new Object();
                                        }
                                    });
                                })
                                .setTtl(Duration.ofDays(30))
                ));
            } catch (SeleniumBasedWebScraper.ChallengeFoundException e) {
                challengeFound.set(true);
                ScrapingService.LOGGER.log(Level.SEVERE, "\tChallenge found on domain " + domain);
                return null;
            } catch (TimeoutException timeoutException) {
                ScrapingService.LOGGER.log(Level.SEVERE, "\tTimeout while scraping single page " + singlePageCandidate.url());
                try {
                    seleniumBasedWebScraper.restartDriver();
                } catch (MalformedURLException e) {
                    ScrapingService.LOGGER.log(Level.SEVERE, "\tCould not restart the selenium driver " + singlePageCandidate.url(), e);
                }
                return null;
            } catch (Throwable ex) {
                ScrapingService.LOGGER.log(Level.SEVERE, "\tCould not scrape single page " + singlePageCandidate.url(), ex);
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
