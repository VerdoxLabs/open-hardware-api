package de.verdox.hwapi.catalog.ingestion.api;

import de.verdox.hwapi.infrastructure.storage.DataStorage;
import de.verdox.hwapi.catalog.ingestion.ScrapingService;
import de.verdox.hwapi.catalog.ingestion.api.selenium.CookieJar;
import de.verdox.hwapi.catalog.ingestion.api.selenium.FScrapingCache;
import de.verdox.hwapi.catalog.ingestion.api.selenium.FetchOptions;
import de.verdox.hwapi.catalog.ingestion.api.selenium.DomainRateLimiter;
import de.verdox.hwapi.catalog.ingestion.api.selenium.SeleniumBasedWebScraper;
import de.verdox.hwapi.catalog.ingestion.images.ProductImageCandidates;
import de.verdox.hwapi.catalog.domain.HardwareSpec;
import lombok.Getter;
import lombok.Setter;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.MalformedURLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.function.IntConsumer;

public abstract class WebsiteCatalogScraper<HARDWARE extends HardwareSpec<HARDWARE>> implements ComponentWebScraper<HARDWARE> {
    private final String domain;
    protected final String id;
    private final List<String> urlsToScrape;
    @Getter
    protected final SeleniumBasedWebScraper seleniumBasedWebScraper;
    @Getter
    @Setter
    private WebsiteScrapingStrategy websiteScrapingStrategy;
    private CatalogCheckpointStore productCheckpoints;
    private volatile int discoveredDetailPages = -1;
    private volatile int discoveredPaginationPages;
    private volatile IntConsumer paginationProgressListener = ignored -> {};
    private final AtomicInteger unreachableDetailPages = new AtomicInteger();

    public WebsiteCatalogScraper(String domain, String id, String... urlsToScrape) {
        this.domain = domain;
        this.id = id;
        this.urlsToScrape = Arrays.stream(urlsToScrape).toList();
        this.seleniumBasedWebScraper = new SeleniumBasedWebScraper("shop-scraper", new FScrapingCache(), new CookieJar(DataStorage.resolve("scraping")));
    }

    @Override
    public Stream<ScrapedSpecPage> downloadWebsites() throws Throwable {
        unreachableDetailPages.set(0);
        discoveredPaginationPages = 0;
        Set<WebsiteScrapingStrategy.SinglePageCandidate> singlePages = new HashSet<>();
        Set<String> alreadyCollected = new HashSet<>();
        Queue<WebsiteScrapingStrategy.MultiPageCandidate> multiPages = new ArrayDeque<>(urlsToScrape.stream().map(WebsiteScrapingStrategy.MultiPageCandidate::new).toList());

        if (multiPages.isEmpty()) {
            ScrapingService.LOGGER.log(Level.SEVERE, "\tNo scraping urls defined for " + domain + "[" + id + "]");
            return Stream.of();
        }

        AtomicBoolean challengeFound = new AtomicBoolean(false);

        while (!multiPages.isEmpty()) {
            WebsiteScrapingStrategy.MultiPageCandidate nextCandidate = multiPages.poll();
            if (alreadyCollected.contains(nextCandidate.url())) {
                continue;
            }
            alreadyCollected.add(nextCandidate.url());

            FetchOptions options = new FetchOptions()
                    .setTryHeadlessFirst(websiteScrapingStrategy.supportsHeadlessScraping())
                    .setSkipIfNotCache(challengeFound.get())
                    .setTtl(websiteScrapingStrategy.cacheTTLForMultiPages());

            try {
                Document doc = seleniumBasedWebScraper.fetchWithInteraction(
                        domain,
                        id,
                        nextCandidate.url(),
                        options,
                        driver -> websiteScrapingStrategy.interactForMultiPage(nextCandidate.url(), driver)
                );

                websiteScrapingStrategy.extractMultiPageURLs(nextCandidate.url(), doc, multiPages);
                websiteScrapingStrategy.extractSinglePagesURLs(nextCandidate.url(), doc, singlePages);
                // singlePages contains products and can therefore be in the
                // thousands. For the pagination progress we need the number
                // of catalog URLs already fetched instead.
                discoveredPaginationPages = alreadyCollected.size();
                paginationProgressListener.accept(discoveredPaginationPages);

            } catch (SeleniumBasedWebScraper.ChallengeFoundException e) {
                ScrapingService.LOGGER.log(Level.SEVERE, "\tChallenge found on domain " + domain);
                challengeFound.set(true);
            } catch (DomainRateLimiter.DomainPausedException e) {
                ScrapingService.LOGGER.log(Level.INFO, "\tSkipping paused domain " + domain + " for " + e.remaining().toMinutes() + " minutes");
                return Stream.of();
            }
        }

        String topLevelHost = ComponentWebScraper.topLevelHost(urlsToScrape.getFirst());

        ScrapingService.LOGGER.log(Level.INFO, "\tFound " + singlePages.size() + " scraping pages for " + topLevelHost + " [" + id + "]");

        Duration productRevalidation = websiteScrapingStrategy.productRevalidationInterval();
        if (productRevalidation != null) {
            productCheckpoints = new CatalogCheckpointStore(domain, id);
        }
        CatalogCheckpointStore checkpoints = productCheckpoints;
        long dueProducts = singlePages.stream()
                .filter(candidate -> !alreadyCollected.contains(candidate.urls()))
                .filter(candidate -> checkpoints == null || checkpoints.isDue(candidate.urls(), productRevalidation))
                .count();
        discoveredDetailPages = (int) dueProducts;
        if (checkpoints != null) {
            ScrapingService.LOGGER.info("\t" + dueProducts + " new or due product pages for " + topLevelHost + " [" + id + "]");
        }

        return singlePages.stream().filter(singlePageCandidate -> !alreadyCollected.contains(singlePageCandidate.urls()))
                .filter(singlePageCandidate -> checkpoints == null || checkpoints.isDue(singlePageCandidate.urls(), productRevalidation)).map(singlePageCandidate -> {
            try {
                Set<Document> documents = new HashSet<>();
                for (String url : singlePageCandidate.urls()) {
                    Document document = seleniumBasedWebScraper.fetch(domain, id, url,
                            new FetchOptions()
                                    .setTryHeadlessFirst(websiteScrapingStrategy.supportsHeadlessScraping())
                                    .setSkipIfNotCache(challengeFound.get())
                                    .setTtl(Duration.ofDays(1000000L))
                                    .setBeforeSaveOperation(driver -> {
                                        try {
                                            JavascriptExecutor js = (JavascriptExecutor) driver;

                                            // 1) Minimaler React-Check
                                            Boolean isReact = (Boolean) js.executeScript(
                                                    "return !!window.__REACT_DEVTOOLS_GLOBAL_HOOK__ || " +
                                                            "!!window.React || !!window.ReactDOM;"
                                            );

                                            // 2) Wenn KEIN React → sofort zurück
                                            if (isReact == null || !isReact) return;

                                            // 3) Wenn React → warte, bis React fertig gerendert hat
                                            new WebDriverWait(driver, Duration.ofSeconds(10)).until(webDriver ->
                                                    js.executeScript("return document.readyState === 'complete';")
                                                            .equals(true)
                                            );
                                        }
                                        catch (Exception e) {

                                        }
                                    })
                                    // Product details are immutable enough for our catalog.  Once a
                                    // page was cached, reparse it locally; only catalog pages decide
                                    // whether a newly discovered product needs a live request.
                                    .setTtl(Duration.ZERO)
                    );
                    documents.add(document);
                }
                return new ScrapedSpecPage(singlePageCandidate, documents);
            } catch (SeleniumBasedWebScraper.ChallengeFoundException e) {
                challengeFound.set(true);
                ScrapingService.LOGGER.log(Level.SEVERE, "\tChallenge found on domain " + domain);
                return null;
            } catch (DomainRateLimiter.DomainPausedException e) {
                throw e;
            } catch (TimeoutException timeoutException) {
                unreachableDetailPages.incrementAndGet();
                ScrapingService.LOGGER.log(Level.SEVERE, "\tTimeout while scraping single pages " + singlePageCandidate.urls());
                try {
                    seleniumBasedWebScraper.restartDriver();
                } catch (MalformedURLException e) {
                    ScrapingService.LOGGER.log(Level.SEVERE, "\tCould not restart the selenium driver " + singlePageCandidate.urls(), e);
                }
                return null;
            } catch (Throwable ex) {
                unreachableDetailPages.incrementAndGet();
                ScrapingService.LOGGER.log(Level.SEVERE, "\tCould not scrape single pages " + singlePageCandidate.urls(), ex);
                return null;
            } finally {
                alreadyCollected.addAll(singlePageCandidate.urls());
            }
        }).filter(Objects::nonNull);
    }

    @Override
    public int getDiscoveredDetailPages() {
        return discoveredDetailPages;
    }

    @Override
    public int getDiscoveredPaginationPages() {
        return discoveredPaginationPages;
    }

    @Override
    public void setPaginationProgressListener(IntConsumer listener) {
        paginationProgressListener = listener == null ? ignored -> {} : listener;
    }

    @Override
    public int getUnreachableDetailPages() {
        return unreachableDetailPages.get();
    }

    @Override
    public Duration getMinLiveRequestInterval() {
        return seleniumBasedWebScraper.getMinLiveRequestInterval();
    }

    @Override
    public void markProcessed(ScrapedSpecPage scrapedPage) {
        if (productCheckpoints != null) {
            productCheckpoints.markProcessed(scrapedPage.singlePageCandidate().urls());
        }
    }

    @Override
    public ScrapedSpecs extract(ScrapedSpecPage scrapedPage) throws Throwable {
        Set<Document> pages = scrapedPage.pages();

        if (pages.isEmpty()) {
            throw new IllegalStateException("ScrapedSpecPage has no pages");
        }

        Document merged;
        if (pages.size() == 1) {
            // nur eine Seite – nichts zu mergen
            merged = pages.iterator().next();
        } else {
            merged = mergePages(pages);
        }

        Map<String, List<String>> specs = websiteScrapingStrategy.extractSpecMap(merged);
        ProductImageCandidates.addTo(specs, merged);
        specs.putAll(scrapedPage.singlePageCandidate().specMap());

        return new ScrapedSpecs(scrapedPage.singlePageCandidate().urls(), specs);
    }

    private Document mergePages(Set<Document> pages) {
        Iterator<Document> it = pages.iterator();

        // Basis-Dokument (nimmt <head>, DOCTYPE etc.)
        Document base = it.next();
        Element baseBody = base.body();

        // alle weiteren Seiten in das body des Basisdokuments hängen
        while (it.hasNext()) {
            Document doc = it.next();
            for (Element child : doc.body().children()) {
                baseBody.appendChild(child.clone()); // clone() wichtig!
            }
        }

        return base;
    }
}
