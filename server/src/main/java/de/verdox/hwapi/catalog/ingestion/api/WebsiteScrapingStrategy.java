package de.verdox.hwapi.catalog.ingestion.api;

import org.jsoup.nodes.Document;

import java.time.Duration;
import java.util.*;

public interface WebsiteScrapingStrategy {
    /**
     * Used to extract the next multi pages from the current pages (used to traverse pagination in catalogs)disco
     */
    void extractMultiPageURLs(String currentURL, Document page, Queue<MultiPageCandidate> multiPageURLs);

    /**
     * Used to extract all single pages from the current multi pages
     */
    void extractSinglePagesURLs(String currentUrl, Document page, Set<SinglePageCandidate> singlePageURLs);

    /**
     * OPTIONAL: Ermöglicht pro Domain/Button-Logik dynamische Interaktion,
     * bevor der HTML-Snapshot als Document gebaut wird.
     * Default: tut nichts.
     */
    default void interactForMultiPage(String currentURL, org.openqa.selenium.WebDriver driver) {
        // Default: keine Interaktion
    }

    default boolean supportsHeadlessScraping() {
        return false;
    }

    /**
     * Extracts the spec map from the provided pages
     */
    Map<String, List<String>> extractSpecMap(Document document) throws Throwable;

    default Duration cacheTTLForMultiPages() {
        return Duration.ofDays(5);
    }

    record SinglePageCandidate(Set<String> urls, Map<String, List<String>> specMap) {
        public SinglePageCandidate(String url) {
            this(url, new HashMap<>());
        }

        public SinglePageCandidate(String... url) {
            this(Set.of(url), new HashMap<>());
        }

        public SinglePageCandidate(String url, Map<String, List<String>> specMap) {
            this(Set.of(url), specMap);
        }
    }

    record MultiPageCandidate(String url) {
    }
}
