package de.verdox.hwapi.io.api;

import org.jsoup.nodes.Document;

import java.util.*;

public interface WebsiteScrapingStrategy {
    /**
     * Used to extract the next multi page from the current page (used to traverse pagination in catalogs)disco
     */
    void extractMultiPageURLs(String currentURL, Document page, Queue<MultiPageCandidate> multiPageURLs);

    /**
     * Used to extract all single pages from the current multi page
     */
    void extractSinglePagesURLs(String currentUrl, Document page, Set<SinglePageCandidate> singlePageURLs);

    default boolean supportsHeadlessScraping() {
        return false;
    }

    /**
     * Extracts the spec map from the provided page
     */
    Map<String, List<String>> extractSpecMap(Document document) throws Throwable;

    record SinglePageCandidate(String url, Map<String, List<String>> specMap) {
        public SinglePageCandidate(String url) {
            this(url, new HashMap<>());
        }
    }

    record MultiPageCandidate(String url) {
    }
}
