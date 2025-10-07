package de.verdox.openhardwareapi.io.api;

import de.verdox.openhardwareapi.component.service.ScrapingService;
import de.verdox.openhardwareapi.model.HardwareSpec;
import org.jsoup.nodes.Document;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

public abstract class MultiPageHardwareScraper<HARDWARE extends HardwareSpec> implements ComponentWebScraper<HARDWARE> {
    protected final String baseUrl;
    protected final SinglePageHardwareScraper<HARDWARE> singlePageHardwareScraper;
    protected final SeleniumBasedWebScraper seleniumBasedWebScraper = new SeleniumBasedWebScraper(new ScrapingCache(Path.of("./data/scraping")), new CookieJar(Path.of("./data/scraping")));

    public MultiPageHardwareScraper(String baseUrl, SinglePageHardwareScraper<HARDWARE> singlePageHardwareScraper) {
        this.baseUrl = baseUrl;
        this.singlePageHardwareScraper = singlePageHardwareScraper;
    }

    protected abstract void setup(Queue<String> multiPageURLs);

    protected abstract void extractMultiPageURLs(Document page, Queue<String> multiPageURLs);

    protected abstract void extractSinglePagesURLs(Document page, Set<String> singlePageURLs);

    @Override
    public final Set<HARDWARE> scrape(ScrapeListener<HARDWARE> onScrape) throws Throwable {
        Set<HARDWARE> scraped = new HashSet<>();
        try {
            Set<String> singlePages = new HashSet<>();
            Queue<String> multiPages = new ArrayDeque<>();
            if (!baseUrl.isBlank()) multiPages.add(baseUrl);
            setup(multiPages);

            if (multiPages.isEmpty()) {
                ScrapingService.LOGGER.log(Level.WARNING, "No scraping url defined for " + getClass().getSimpleName());
                return Set.of();
            }

            // Phase 1: Auflisten (0.1 -> 0.5)
            float p = 0.1f;

            Set<String> alreadyCollected = new HashSet<>();

            while (!multiPages.isEmpty()) {
                String nextUrl = multiPages.poll();
                if(alreadyCollected.contains(nextUrl)) {
                    ScrapingService.LOGGER.log(Level.INFO, "Skipping double entry: "+nextUrl);
                    continue;
                }
                alreadyCollected.add(nextUrl);

                Document doc = seleniumBasedWebScraper.scrapeWithCache(nextUrl, Duration.ofDays(30));
                ScrapingService.LOGGER.log(Level.INFO, "Extracting catalog pages from: "+nextUrl);
                extractMultiPageURLs(doc, multiPages);

                int before = singlePages.size();
                extractSinglePagesURLs(doc, singlePages);
                ScrapingService.LOGGER.log(Level.INFO, "Collected " + (singlePages.size() - before) + " from " + nextUrl);

                // Annäherung an 0.5 ohne Überschwingen
                p += (0.5f - p) * 0.5f;
            }

            ScrapingService.LOGGER.log(Level.INFO, "Collected " + singlePages.size() + " specs for further scraping...");

            // Phase 2: Einzel-Seiten (0.5 -> 1.0)
            if (this.singlePageHardwareScraper != null && !singlePages.isEmpty()) {
                final int total = singlePages.size();
                int i = 0;
                for (String url : singlePages) {
                    if(alreadyCollected.contains(url)) continue;
                    int displayIndex = i + 1;
                    singlePageHardwareScraper.setUrl(url);
                    try {
                        scraped.addAll(singlePageHardwareScraper.scrape(onScrape));
                    } catch (Throwable ex) {
                        ScrapingService.LOGGER.log(Level.SEVERE, "Could not scrape single page " + url, ex);
                    } finally {
                        i++;
                        float phaseProgress = (i / (float) total);           // 0..1
                        float overall = 0.5f + 0.5f * phaseProgress;         // 0.5..1
                        alreadyCollected.add(url);
                    }
                }
            } else {
                // Keine Single-Seiten: direkt fertig
            }

            return scraped;
        } finally {
        }
    }


    @Override
    public int getAmountTasks() {
        return 1;
    }
}
